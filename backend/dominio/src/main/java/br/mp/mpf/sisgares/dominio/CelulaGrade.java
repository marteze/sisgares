package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Célula [inicio, inicio + 30 min) da grade.
 *
 * @param inicio    instante inicial da célula no fuso {@link ConstantesDominio#ZONA}
 * @param estado    estado calculado
 * @param reservaId reserva que ocupa a célula; preenchido somente quando {@code estado == OCUPADO}
 */
public record CelulaGrade(LocalDateTime inicio, EstadoCelula estado, Long reservaId) {

    public CelulaGrade {
        Objects.requireNonNull(inicio, "inicio é obrigatório");
        Objects.requireNonNull(estado, "estado é obrigatório");
        if ((estado == EstadoCelula.OCUPADO) != (reservaId != null)) {
            throw new IllegalArgumentException("reservaId deve ser informado somente para célula OCUPADO");
        }
    }

    /** Término (excluso) da célula. */
    public LocalDateTime termino() {
        return inicio.plus(CalculadoraGrade.PASSO);
    }
}
