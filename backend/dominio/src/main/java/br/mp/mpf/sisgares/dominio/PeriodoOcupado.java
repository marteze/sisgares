package br.mp.mpf.sisgares.dominio;

import java.util.Objects;

/**
 * Período de outra Reserva não cancelada que ocupa um Ambiente (base de RN5/RN6 e da grade).
 *
 * @param reservaId  RESE_ID da reserva dona do período
 * @param ambienteId AMBI_ID ocupado
 * @param periodo    intervalo ocupado
 */
public record PeriodoOcupado(long reservaId, long ambienteId, Periodo periodo) {

    public PeriodoOcupado {
        Objects.requireNonNull(periodo, "periodo é obrigatório");
    }
}
