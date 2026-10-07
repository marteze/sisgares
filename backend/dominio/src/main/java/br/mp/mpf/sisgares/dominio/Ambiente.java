package br.mp.mpf.sisgares.dominio;

import java.util.Objects;

/**
 * Ambiente reservável (AMBI), organizado em árvore via {@code idPai}.
 *
 * @param id        AMBI_ID
 * @param descricao AMBI_DESC
 * @param ativo     AMBI_ST_ATIVO
 * @param idPai     AMBI_ID_PAI; {@code null} para Ambiente_Raiz
 * @param unidade   Unidade_Macro (obrigatória)
 */
public record Ambiente(long id, String descricao, boolean ativo, Long idPai, String unidade) {

    public Ambiente {
        Objects.requireNonNull(descricao, "descricao é obrigatória");
        Objects.requireNonNull(unidade, "unidade é obrigatória");
        if (idPai != null && idPai == id) {
            throw new IllegalArgumentException("ambiente não pode ser pai de si mesmo");
        }
    }

    /** Indica se o ambiente é raiz da árvore. */
    public boolean raiz() {
        return idPai == null;
    }
}
