package br.mp.mpf.sisgares.dominio;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Grade de datas por horários de 30 minutos do Painel do Solicitante (Req. 15).
 *
 * @param ambienteId Ambiente da grade
 * @param linhas     horários iniciais das linhas, dentro da Faixa_Horária aplicável
 * @param colunas    colunas (datas) em ordem cronológica
 */
public record Grade(long ambienteId, List<LocalTime> linhas, List<ColunaGrade> colunas) {

    public Grade {
        linhas = List.copyOf(Objects.requireNonNull(linhas, "linhas é obrigatória"));
        colunas = List.copyOf(Objects.requireNonNull(colunas, "colunas é obrigatória"));
    }
}
