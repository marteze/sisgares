package br.mp.mpf.sisgares.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Coluna da grade: uma data com uma célula por linha de 30 minutos.
 *
 * @param data    data da coluna
 * @param celulas células em ordem de horário, alinhadas a {@link Grade#linhas()}
 */
public record ColunaGrade(LocalDate data, List<CelulaGrade> celulas) {

    public ColunaGrade {
        Objects.requireNonNull(data, "data é obrigatória");
        celulas = List.copyOf(Objects.requireNonNull(celulas, "celulas é obrigatória"));
    }
}
