package br.mp.mpf.sisgares.eventos.snp;

/** Porta de envio de pedidos ao SNP (Req. 14.3). */
public interface GatewaySnp {

    /**
     * Registra o pedido no endpoint informado.
     *
     * @param endpoint URL do SNP vinda da Configuração
     * @throws FalhaSnpException se o SNP estiver indisponível, responder erro ou resposta inválida
     */
    RespostaSnp registrar(String endpoint, PedidoSnp pedido);
}
