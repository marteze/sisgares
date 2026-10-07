package br.mp.mpf.sisgares.reservas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * Período [início, término) trafegado no JSON em ISO-8601 local ({@code yyyy-MM-ddTHH:mm[:ss]}).
 *
 * <p>Término ≤ início não é rejeitado aqui: é a regra RN1, acumulada pelo Validador_Reserva.
 *
 * @param id      identificador do período (PRES) como String; opcional (ausente em período novo)
 * @param inicio  instante inicial (incluso)
 * @param termino instante final (excluso)
 */
public record PeriodoDto(
        @Pattern(regexp = ValidadorEntrada.REGEX_ID, message = ValidadorEntrada.MSG_ID) String id,
        @NotNull(message = "Informe o início do período.") LocalDateTime inicio,
        @NotNull(message = "Informe o término do período.") LocalDateTime termino) {
}
