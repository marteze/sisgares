package br.mp.mpf.sisgares.reservas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Solicitação de Recurso (SOLI) no JSON.
 *
 * <p>Quantidade menor que 1 não é rejeitada aqui: é a regra RN8_QUANTIDADE_INVALIDA, acumulada pelo
 * Validador_Reserva. Quantidade ausente é tratada como 1 pelo {@link ConversorReserva}.
 *
 * @param recursoId  RECU_ID como String
 * @param quantidade quantidade pedida; opcional
 */
public record SolicitacaoDto(
        @NotNull(message = "Informe o recurso.")
        @Pattern(regexp = ValidadorEntrada.REGEX_ID, message = ValidadorEntrada.MSG_ID) String recursoId,
        Integer quantidade) {
}
