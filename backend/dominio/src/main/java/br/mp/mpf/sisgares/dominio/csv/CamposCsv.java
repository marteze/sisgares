package br.mp.mpf.sisgares.dominio.csv;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Leitura tipada dos campos de uma linha de CSV, acumulando todos os erros encontrados
 * (em vez de parar no primeiro). Ao final, {@link #concluir()} lança
 * {@link FormatoInvalidoException} com a lista de problemas, citando apenas os nomes das colunas.
 * Também oferece os formatadores usados na escrita.
 */
public final class CamposCsv {

    /** Formato de data/hora dos CSVs: {@code dd/MM/yyyy HH:mm:ss}. */
    public static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    /** Inteiro não negativo, com ou sem ponto de milhar (ex.: "14.207" ou "14207"). */
    private static final Pattern INTEIRO = Pattern.compile("\\d{1,3}(\\.\\d{3})+|\\d+");

    private final Map<String, String> linha;
    private final List<String> erros = new ArrayList<>();

    public CamposCsv(Map<String, String> linha) {
        this.linha = linha;
    }

    /** Valor da coluna sem espaços nas pontas; null se ausente ou em branco. */
    private String bruto(String coluna) {
        String valor = linha.get(coluna);
        if (valor == null) {
            return null;
        }
        String aparado = valor.strip();
        return aparado.isEmpty() ? null : aparado;
    }

    private void erro(String mensagem) {
        erros.add(mensagem);
    }

    /** ID obrigatório; aceita ponto de milhar ("14.207" → 14207). */
    public long id(String coluna) {
        Long valor = idOpcional(coluna);
        if (valor == null) {
            // Formato inválido já foi registrado por idOpcional; aqui só falta o caso vazio
            if (bruto(coluna) == null) {
                erro(coluna + ": obrigatório");
            }
            return 0L;
        }
        return valor;
    }

    /** ID opcional (vazio → null); aceita ponto de milhar. */
    public Long idOpcional(String coluna) {
        String valor = bruto(coluna);
        if (valor == null) {
            return null;
        }
        if (!INTEIRO.matcher(valor).matches()) {
            erro(coluna + ": número inteiro inválido");
            return null;
        }
        try {
            return Long.parseLong(valor.replace(".", ""));
        } catch (NumberFormatException e) {
            erro(coluna + ": número fora do intervalo");
            return null;
        }
    }

    /** Inteiro obrigatório (não negativo). */
    public int inteiro(String coluna) {
        Integer valor = inteiroOpcional(coluna);
        if (valor == null) {
            if (bruto(coluna) == null) {
                erro(coluna + ": obrigatório");
            }
            return 0;
        }
        return valor;
    }

    /** Inteiro opcional (vazio → null). */
    public Integer inteiroOpcional(String coluna) {
        Long valor = idOpcional(coluna);
        if (valor == null) {
            return null;
        }
        if (valor > Integer.MAX_VALUE) {
            erro(coluna + ": número fora do intervalo");
            return null;
        }
        return valor.intValue();
    }

    /** Texto obrigatório. */
    public String texto(String coluna) {
        String valor = bruto(coluna);
        if (valor == null) {
            erro(coluna + ": obrigatório");
        }
        return valor;
    }

    /** Texto opcional (vazio → null). */
    public String textoOpcional(String coluna) {
        return bruto(coluna);
    }

    /** Indicador {@code *_ST_*}: somente "S" ou "N" (Requisito 3.6). */
    public String simNao(String coluna) {
        String valor = bruto(coluna);
        if (valor == null) {
            erro(coluna + ": obrigatório (S ou N)");
            return null;
        }
        String maiusculo = valor.toUpperCase(java.util.Locale.ROOT);
        if (!maiusculo.equals("S") && !maiusculo.equals("N")) {
            erro(coluna + ": deve ser S ou N");
            return null;
        }
        return maiusculo;
    }

    /** Data/hora obrigatória no formato {@code dd/MM/yyyy HH:mm:ss}. */
    public LocalDateTime dataHora(String coluna) {
        String valor = bruto(coluna);
        if (valor == null) {
            erro(coluna + ": obrigatório");
            return null;
        }
        try {
            return LocalDateTime.parse(valor, FORMATO_DATA_HORA);
        } catch (DateTimeParseException e) {
            erro(coluna + ": data fora do formato dd/MM/yyyy HH:mm:ss");
            return null;
        }
    }

    /** Lança {@link FormatoInvalidoException} se algum campo teve erro. */
    public void concluir() {
        if (!erros.isEmpty()) {
            throw new FormatoInvalidoException(String.join("; ", erros));
        }
    }

    // ---------- Formatação (escrita) ----------

    /** ID com ponto de milhar quando ≥ 1000 (14207 → "14.207"). */
    public static String formatarId(long id) {
        String digitos = Long.toString(id);
        if (id < 1000) {
            return digitos;
        }
        StringBuilder sb = new StringBuilder();
        int primeiro = digitos.length() % 3;
        if (primeiro == 0) {
            primeiro = 3;
        }
        sb.append(digitos, 0, primeiro);
        for (int i = primeiro; i < digitos.length(); i += 3) {
            sb.append('.').append(digitos, i, i + 3);
        }
        return sb.toString();
    }

    /** ID opcional: null → null. */
    public static String formatarId(Long id) {
        return id == null ? null : formatarId(id.longValue());
    }

    /** Inteiro sem separador de milhar; null → null. */
    public static String formatarInteiro(Integer valor) {
        return valor == null ? null : Integer.toString(valor);
    }

    public static String formatarDataHora(LocalDateTime dataHora) {
        return dataHora == null ? null : FORMATO_DATA_HORA.format(dataHora);
    }

    /** Monta a linha na ordem do cabeçalho; {@code valores} deve ter o mesmo tamanho. */
    public static Map<String, String> linha(List<String> cabecalho, String... valores) {
        if (cabecalho.size() != valores.length) {
            throw new IllegalArgumentException("quantidade de valores difere do cabeçalho");
        }
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < valores.length; i++) {
            mapa.put(cabecalho.get(i), valores[i]);
        }
        return mapa;
    }
}
