package br.mp.mpf.sisgares.eventos.snp;

/**
 * Resposta do SNP: número e link do Pedido_SNP (Req. 14.3).
 *
 * @param numero ex.: {@code SNP-2025-000123}
 * @param link   URL do pedido no simulador
 */
public record RespostaSnp(String numero, String link) {

    public RespostaSnp {
        if (numero == null || numero.isBlank() || link == null || link.isBlank()) {
            throw new FalhaSnpException(FalhaSnpException.RESPOSTA_INVALIDA, "Resposta do SNP sem número ou link.");
        }
    }
}
