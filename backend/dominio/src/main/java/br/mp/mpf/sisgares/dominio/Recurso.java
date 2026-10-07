package br.mp.mpf.sisgares.dominio;

import java.util.Objects;
import java.util.Set;

/**
 * Recurso reservável (RECU).
 *
 * @param id                   RECU_ID
 * @param descricao            RECU_DESC
 * @param ativo                RECU_ST_ATIVO
 * @param limitado             RECU_ST_LIMITADO = "S" (Recurso_Limitado)
 * @param disponibilidade      RECU_DISPONIBILIDADE (relevante só quando limitado)
 * @param unidade              Unidade_Macro; {@code null} indica todas as unidades
 * @param ambientesVinculados  AMBI_IDs com vínculo VREC; vazio indica recurso não vinculado a ambiente
 */
public record Recurso(long id, String descricao, boolean ativo, boolean limitado, int disponibilidade,
                      String unidade, Set<Long> ambientesVinculados) {

    public Recurso {
        Objects.requireNonNull(descricao, "descricao é obrigatória");
        if (disponibilidade < 0) {
            throw new IllegalArgumentException("disponibilidade não pode ser negativa");
        }
        ambientesVinculados = ambientesVinculados == null ? Set.of() : Set.copyOf(ambientesVinculados);
    }

    /** Indica se o recurso pode ser usado na Unidade_Macro informada (RN9). */
    public boolean atendeUnidade(String unidadeReserva) {
        return unidade == null || unidade.equals(unidadeReserva);
    }
}
