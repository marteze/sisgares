package br.mp.mpf.sisgares.reservas.dto;

/**
 * Corpo do cancelamento de Reserva.
 *
 * <p>A exigência de {@code confirmado = true} é a regra RN12_CONFIRMACAO_OBRIGATORIA, aplicada no
 * domínio/handler para devolver o código da regra.
 *
 * @param confirmado confirmação explícita do usuário; {@code null} equivale a não confirmado
 */
public record CancelamentoRequisicao(Boolean confirmado) {

    /** Indica se o cancelamento foi confirmado explicitamente. */
    public boolean foiConfirmado() {
        return Boolean.TRUE.equals(confirmado);
    }
}
