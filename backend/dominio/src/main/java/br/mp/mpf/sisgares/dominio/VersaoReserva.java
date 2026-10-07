package br.mp.mpf.sisgares.dominio;

import java.util.Objects;

/**
 * Versão_Reserva: snapshot de uma {@link Reserva} em determinado número de versão.
 *
 * @param numero  número da versão
 * @param reserva snapshot da Reserva nessa versão
 */
public record VersaoReserva(long numero, Reserva reserva) {
    public VersaoReserva {
        if (numero < 0) {
            throw new IllegalArgumentException("numero não pode ser negativo");
        }
        Objects.requireNonNull(reserva, "reserva é obrigatória");
    }
}
