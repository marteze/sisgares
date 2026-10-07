package br.mp.mpf.sisgares.dominio;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Parâmetros da grade do Painel do Solicitante (Req. 15.1).
 *
 * @param ambienteId     Ambiente selecionado
 * @param unidade        Unidade_Macro do Ambiente (define a Faixa_Horária aplicável)
 * @param dataReferencia primeira data exibida
 * @param colunas        número de colunas (datas), de 1 a 14
 * @param fds            {@code true} para exibir sábados e domingos
 */
public record ParametrosGrade(long ambienteId, String unidade, LocalDate dataReferencia,
                              int colunas, boolean fds) {

    /** Mínimo de colunas da grade. */
    public static final int COLUNAS_MINIMO = 1;
    /** Máximo de colunas da grade. */
    public static final int COLUNAS_MAXIMO = 14;
    /** Número padrão de colunas (Req. 15.1). */
    public static final int COLUNAS_PADRAO = 7;

    public ParametrosGrade {
        Objects.requireNonNull(dataReferencia, "dataReferencia é obrigatória");
        if (colunas < COLUNAS_MINIMO || colunas > COLUNAS_MAXIMO) {
            throw new IllegalArgumentException("colunas deve estar entre " + COLUNAS_MINIMO
                    + " e " + COLUNAS_MAXIMO);
        }
    }
}
