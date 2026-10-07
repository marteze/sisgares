package br.mp.mpf.sisgares.dominio;

import java.util.Objects;

/**
 * Conflito de horário detectado entre um Período da Reserva e um Período ocupado.
 *
 * @param codigo                {@link CodigosRegra#RN5_CONFLITO_HORARIO} ou {@link CodigosRegra#RN6_CONFLITO_PAI_FILHO}
 * @param periodo               período da reserva avaliada
 * @param reservaConflitanteId  RESE_ID da reserva conflitante
 * @param ambienteConflitanteId AMBI_ID ocupado pela reserva conflitante
 */
public record Conflito(String codigo, Periodo periodo, long reservaConflitanteId, long ambienteConflitanteId) {

    public Conflito {
        Objects.requireNonNull(codigo, "codigo é obrigatório");
        Objects.requireNonNull(periodo, "periodo é obrigatório");
        if (!CodigosRegra.RN5_CONFLITO_HORARIO.equals(codigo)
                && !CodigosRegra.RN6_CONFLITO_PAI_FILHO.equals(codigo)) {
            throw new IllegalArgumentException("codigo de conflito inválido: " + codigo);
        }
    }
}
