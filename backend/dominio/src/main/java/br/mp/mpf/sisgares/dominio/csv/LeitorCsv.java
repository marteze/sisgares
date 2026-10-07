package br.mp.mpf.sisgares.dominio.csv;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura e escrita dos CSVs do Seed (Requisitos 3.1, 4.3, 4.4 e 4.10).
 * Linhas inválidas são rejeitadas com número da linha e motivo {@code FORMATO_INVALIDO};
 * a leitura continua nas linhas seguintes.
 */
public final class LeitorCsv {

    private static final String QUEBRA = "\n";

    private LeitorCsv() {
    }

    /** Lê o CSV (primeira linha não vazia é o cabeçalho) e converte cada linha com o mapeador. */
    public static <T> ResultadoLeitura<T> ler(Reader in, MapeadorLinha<T> m) {
        List<ParserCsv.RegistroCsv> brutos;
        try {
            brutos = ParserCsv.analisar(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<T> registros = new ArrayList<>();
        List<Rejeicao> rejeicoes = new ArrayList<>();

        int indice = 0;
        while (indice < brutos.size() && vazio(brutos.get(indice))) {
            indice++;
        }
        if (indice >= brutos.size()) {
            return new ResultadoLeitura<>(registros, rejeicoes);
        }

        // Cabeçalho: precisa conter todas as colunas esperadas pelo mapeador
        ParserCsv.RegistroCsv cabecalho = brutos.get(indice++);
        List<String> colunas = new ArrayList<>();
        for (String c : cabecalho.campos()) {
            colunas.add(c.strip());
        }
        List<String> faltantes = new ArrayList<>(m.cabecalho());
        faltantes.removeAll(colunas);
        if (cabecalho.erro() != null || !faltantes.isEmpty()) {
            String detalhe = cabecalho.erro() != null
                    ? "cabeçalho: " + cabecalho.erro()
                    : "cabeçalho sem as colunas " + String.join(", ", faltantes);
            rejeicoes.add(Rejeicao.formatoInvalido(cabecalho.linha(), detalhe));
            return new ResultadoLeitura<>(registros, rejeicoes);
        }

        for (; indice < brutos.size(); indice++) {
            ParserCsv.RegistroCsv bruto = brutos.get(indice);
            if (vazio(bruto)) {
                continue;
            }
            if (bruto.erro() != null) {
                rejeicoes.add(Rejeicao.formatoInvalido(bruto.linha(), bruto.erro()));
                continue;
            }
            if (bruto.campos().size() != colunas.size()) {
                rejeicoes.add(Rejeicao.formatoInvalido(bruto.linha(),
                        "linha com " + bruto.campos().size() + " colunas; esperado " + colunas.size()));
                continue;
            }
            Map<String, String> linha = new HashMap<>();
            for (int i = 0; i < colunas.size(); i++) {
                linha.put(colunas.get(i), bruto.campos().get(i));
            }
            try {
                registros.add(m.deLinha(linha));
            } catch (IllegalArgumentException e) {
                // Inclui FormatoInvalidoException e validações dos records
                rejeicoes.add(Rejeicao.formatoInvalido(bruto.linha(), e.getMessage()));
            }
        }
        return new ResultadoLeitura<>(registros, rejeicoes);
    }

    /** Escreve o CSV com cabeçalho entre aspas e IDs ≥ 1000 com ponto de milhar. */
    public static <T> String formatar(List<T> registros, MapeadorLinha<T> m) {
        StringBuilder sb = new StringBuilder();
        List<String> cabecalho = m.cabecalho();
        for (int i = 0; i < cabecalho.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(cabecalho.get(i).replace("\"", "\"\"")).append('"');
        }
        sb.append(QUEBRA);
        for (T registro : registros) {
            Map<String, String> linha = new LinkedHashMap<>(m.paraLinha(registro));
            for (int i = 0; i < cabecalho.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escapar(linha.get(cabecalho.get(i))));
            }
            sb.append(QUEBRA);
        }
        return sb.toString();
    }

    /** Coloca o valor entre aspas quando contém vírgula, aspas, quebra de linha ou espaços nas pontas. */
    static String escapar(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        boolean precisaAspas = valor.indexOf(',') >= 0 || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0
                || !valor.equals(valor.strip());
        if (!precisaAspas) {
            return valor;
        }
        return '"' + valor.replace("\"", "\"\"") + '"';
    }

    /** Registro sem conteúdo (linha em branco). */
    private static boolean vazio(ParserCsv.RegistroCsv r) {
        return r.erro() == null && r.campos().size() == 1 && r.campos().get(0).isBlank();
    }
}
