package br.mp.mpf.sisgares.reservas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo de POST {@code /api/reservas/verificar-periodo}.
 *
 * @param ambienteId AMBI_ID a verificar
 * @param periodo    período candidato
 * @param reservaId  Reserva em edição (ignorada na detecção de conflito); opcional
 */
public record VerificarPeriodoRequisicao(
        @NotNull(message = "Informe o ambiente.")
        @Pattern(regexp = ValidadorEntrada.REGEX_ID, message = ValidadorEntrada.MSG_ID) String ambienteId,
        @NotNull(message = "Informe o período.") @Valid PeriodoDto periodo,
        @Pattern(regexp = ValidadorEntrada.REGEX_ID, message = ValidadorEntrada.MSG_ID) String reservaId) {
}
