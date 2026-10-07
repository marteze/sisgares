package br.mp.mpf.sisgares.dominio.csv;

/**
 * Linha de CSV rejeitada na leitura.
 *
 * @param linha   número da linha física (base 1) em que o registro começa
 * @param motivo  código do motivo (ex.: {@code FORMATO_INVALIDO})
 * @param detalhe descrição do problema, sem dados pessoais
 */
public record Rejeicao(int linha, String motivo, String detalhe) {

    /** Motivo usado para linhas com formato inválido (Requisito 4.10). */
    public static final String FORMATO_INVALIDO = "FORMATO_INVALIDO";

    public static Rejeicao formatoInvalido(int linha, String detalhe) {
        return new Rejeicao(linha, FORMATO_INVALIDO, detalhe);
    }
}
