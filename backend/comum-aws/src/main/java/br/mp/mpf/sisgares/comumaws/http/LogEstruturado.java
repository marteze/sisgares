package br.mp.mpf.sisgares.comumaws.http;

import br.mp.mpf.sisgares.dominio.MascaradorDados;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Log estruturado em JSON (uma linha por evento) via {@code System.out}, lido pelo CloudWatch.
 *
 * <p>Toda mensagem e todo valor textual extra passam por {@link MascaradorDados#mascarar(String)}.
 * Exceções são registradas apenas com tipo e mensagem mascarada, nunca com stack trace.
 * Não registre tokens.
 */
public final class LogEstruturado {

    /** Níveis suportados. */
    public enum Nivel { DEBUG, INFO, WARN, ERROR }

    private final Clock clock;
    private final PrintStream saida;
    private final String origem;

    /** Log que escreve em {@code System.out}. */
    public LogEstruturado(Clock clock, String origem) {
        this(clock, origem, System.out);
    }

    /** Construtor com saída explícita (útil em testes). */
    public LogEstruturado(Clock clock, String origem, PrintStream saida) {
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
        this.origem = Objects.requireNonNull(origem, "origem é obrigatória");
        this.saida = Objects.requireNonNull(saida, "saida é obrigatória");
    }

    public void info(String correlationId, String mensagem) {
        registrar(Nivel.INFO, correlationId, mensagem, Map.of(), null);
    }

    public void info(String correlationId, String mensagem, Map<String, ?> extras) {
        registrar(Nivel.INFO, correlationId, mensagem, extras, null);
    }

    public void aviso(String correlationId, String mensagem) {
        registrar(Nivel.WARN, correlationId, mensagem, Map.of(), null);
    }

    public void erro(String correlationId, String mensagem, Throwable erro) {
        registrar(Nivel.ERROR, correlationId, mensagem, Map.of(), erro);
    }

    /**
     * Gera e escreve a linha de log.
     *
     * @param extras campos adicionais; valores textuais são mascarados
     * @param erro   exceção opcional (somente tipo e mensagem mascarada)
     */
    public void registrar(Nivel nivel, String correlationId, String mensagem,
                          Map<String, ?> extras, Throwable erro) {
        saida.println(formatar(nivel, correlationId, mensagem, extras, erro));
    }

    /** Monta o JSON da linha sem escrevê-lo. */
    String formatar(Nivel nivel, String correlationId, String mensagem,
                    Map<String, ?> extras, Throwable erro) {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("timestamp", Instant.now(clock).toString());
        linha.put("nivel", nivel.name());
        linha.put("origem", origem);
        linha.put("correlationId", correlationId);
        linha.put("mensagem", MascaradorDados.mascarar(mensagem));
        if (extras != null) {
            extras.forEach((chave, valor) -> {
                // Campos fixos não podem ser sobrescritos por extras
                if (!linha.containsKey(chave)) {
                    linha.put(chave, mascararValor(valor));
                }
            });
        }
        if (erro != null) {
            Map<String, Object> detalhe = new LinkedHashMap<>();
            detalhe.put("tipo", erro.getClass().getName());
            detalhe.put("mensagem", MascaradorDados.mascarar(erro.getMessage()));
            linha.put("erro", detalhe);
        }
        try {
            return Json.escrever(linha);
        } catch (RuntimeException e) {
            // Fallback mínimo para nunca perder a linha de log
            return "{\"nivel\":\"ERROR\",\"mensagem\":\"falha ao serializar log\",\"correlationId\":"
                    + Json.escrever(correlationId) + "}";
        }
    }

    /** Mascara strings; demais tipos são convertidos para texto e mascarados, exceto números e booleanos. */
    private static Object mascararValor(Object valor) {
        if (valor == null || valor instanceof Number || valor instanceof Boolean) {
            return valor;
        }
        return MascaradorDados.mascarar(String.valueOf(valor));
    }
}
