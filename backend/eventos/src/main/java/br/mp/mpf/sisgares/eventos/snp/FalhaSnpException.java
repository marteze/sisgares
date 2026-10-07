package br.mp.mpf.sisgares.eventos.snp;

/**
 * Falha ao registrar Pedido_SNP. Propagada ao Step Functions para acionar o retry (Req. 14.5).
 * O {@link #motivo()} é um código curto, sem dados pessoais, gravado em {@code motivoFalha}.
 */
public final class FalhaSnpException extends RuntimeException {

    public static final String SEM_ENDPOINT = "ENDPOINT_NAO_CONFIGURADO";
    public static final String ENDPOINT_INVALIDO = "ENDPOINT_INVALIDO";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String INDISPONIVEL = "INDISPONIVEL";
    public static final String INTERROMPIDO = "INTERROMPIDO";
    public static final String RESPOSTA_INVALIDA = "RESPOSTA_INVALIDA";
    public static final String PEDIDOS_FALHOS = "PEDIDOS_FALHOS";

    private final String motivo;

    public FalhaSnpException(String motivo, String mensagem) {
        super(mensagem);
        this.motivo = motivo;
    }

    public FalhaSnpException(String motivo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.motivo = motivo;
    }

    /** Motivo da falha (ex.: {@code TIMEOUT}, {@code HTTP_503}). */
    public String motivo() {
        return motivo;
    }
}
