package br.mp.mpf.sisgares.dominio.csv;

/**
 * Indica que uma linha de CSV não pôde ser convertida (motivo {@code FORMATO_INVALIDO}).
 * A mensagem cita apenas nomes de colunas e o problema, nunca os valores (LGPD).
 */
public class FormatoInvalidoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public FormatoInvalidoException(String detalhe) {
        super(detalhe);
    }
}
