package br.mp.mpf.sisgares.dominio.csv;

/**
 * Linha de {@code dados-recurso.csv} (RECU_ID, RECU_DESC, RECU_GREC_ID, RECU_ST_LIMITADO,
 * RECU_DISPONIBILIDADE, RECU_ST_ATIVO, RECU_ICONE_ARQUIVO).
 */
public record LinhaRecu(
        long recuId,
        String recuDesc,
        long recuGrecId,
        String recuStLimitado,
        int recuDisponibilidade,
        String recuStAtivo,
        String recuIconeArquivo) {
}
