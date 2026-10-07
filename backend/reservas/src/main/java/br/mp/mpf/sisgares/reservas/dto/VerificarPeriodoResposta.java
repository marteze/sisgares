package br.mp.mpf.sisgares.reservas.dto;

import java.util.List;

/**
 * Resposta de POST {@code /api/reservas/verificar-periodo}: {@code {"conflitos":[...]}}.
 * Sem dados pessoais (Req. 6.17): somente código, mensagem e id da Reserva conflitante.
 */
public record VerificarPeriodoResposta(List<ConflitoDto> conflitos) {

    public VerificarPeriodoResposta {
        conflitos = conflitos == null ? List.of() : List.copyOf(conflitos);
    }

    /**
     * @param codigo    {@code RN5_CONFLITO_HORARIO} ou {@code RN6_CONFLITO_PAI_FILHO}
     * @param mensagem  mensagem ao usuário
     * @param reservaId RESE_ID da Reserva conflitante
     */
    public record ConflitoDto(String codigo, String mensagem, String reservaId) {
    }
}
