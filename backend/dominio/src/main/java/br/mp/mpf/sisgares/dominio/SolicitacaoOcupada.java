package br.mp.mpf.sisgares.dominio;

import java.util.Objects;

/**
 * Quantidade de um Recurso já comprometida por outra Reserva não cancelada em um período (RN8).
 *
 * @param reservaId  RESE_ID da outra reserva
 * @param recursoId  RECU_ID comprometido
 * @param quantidade quantidade comprometida
 * @param periodo    período em que o recurso está em uso
 */
public record SolicitacaoOcupada(long reservaId, long recursoId, int quantidade, Periodo periodo) {

    public SolicitacaoOcupada {
        Objects.requireNonNull(periodo, "periodo é obrigatório");
        if (quantidade < 0) {
            throw new IllegalArgumentException("quantidade não pode ser negativa");
        }
    }
}
