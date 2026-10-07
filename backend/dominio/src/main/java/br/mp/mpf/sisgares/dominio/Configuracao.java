package br.mp.mpf.sisgares.dominio;

import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;

/**
 * Parâmetros de Antecedência_Mínima e Faixa_Horária (global e por Unidade_Macro).
 *
 * @param antecedenciaMinutos Antecedência_Mínima em minutos (≥ 0)
 * @param faixaGlobal         faixa aplicada quando a unidade não tem faixa própria
 * @param faixasPorUnidade    faixas próprias por Unidade_Macro (ex.: "PR/CE")
 */
public record Configuracao(int antecedenciaMinutos, FaixaHoraria faixaGlobal,
                           Map<String, FaixaHoraria> faixasPorUnidade) {

    public Configuracao {
        // Req. 8.1: inteiro entre 0 e 10080 minutos
        if (antecedenciaMinutos < 0 || antecedenciaMinutos > ConstantesDominio.ANTECEDENCIA_MAXIMA_MINUTOS) {
            throw new IllegalArgumentException("antecedenciaMinutos deve estar entre 0 e "
                    + ConstantesDominio.ANTECEDENCIA_MAXIMA_MINUTOS);
        }
        Objects.requireNonNull(faixaGlobal, "faixaGlobal é obrigatória");
        faixasPorUnidade = faixasPorUnidade == null ? Map.of() : Map.copyOf(faixasPorUnidade);
    }

    /** Faixa aplicável: a da unidade substitui a global quando existir (Req. 9.11). */
    public FaixaHoraria faixaAplicavel(String unidade) {
        if (unidade == null) {
            return faixaGlobal;
        }
        return faixasPorUnidade.getOrDefault(unidade, faixaGlobal);
    }

    /**
     * Faixa_Horária: horário mínimo e máximo (HH:mm) aceitos para início e término de Períodos.
     *
     * @param minimo horário mínimo (incluso)
     * @param maximo horário máximo (incluso)
     */
    public record FaixaHoraria(LocalTime minimo, LocalTime maximo) {

        public FaixaHoraria {
            Objects.requireNonNull(minimo, "minimo é obrigatório");
            Objects.requireNonNull(maximo, "maximo é obrigatório");
            // Req. 8.3: mínimo igual ou posterior ao máximo é inválido
            if (!minimo.isBefore(maximo)) {
                throw new IllegalArgumentException("minimo deve ser anterior a maximo");
            }
        }

        /** Indica se o horário está dentro da faixa, limites inclusos. */
        public boolean contem(LocalTime horario) {
            return !horario.isBefore(minimo) && !horario.isAfter(maximo);
        }
    }
}
