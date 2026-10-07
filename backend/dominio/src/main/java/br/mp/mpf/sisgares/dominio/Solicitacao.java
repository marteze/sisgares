package br.mp.mpf.sisgares.dominio;

/**
 * Solicitação de Recurso dentro de uma Reserva (SOLI).
 *
 * <p>A quantidade não é validada aqui: quantidade menor que 1 é a regra RN8_QUANTIDADE_INVALIDA,
 * acumulada pelo Validador_Reserva.
 *
 * @param recursoId  RECU_ID solicitado
 * @param quantidade quantidade pedida
 */
public record Solicitacao(long recursoId, int quantidade) {
}
