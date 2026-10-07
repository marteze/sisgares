package br.mp.mpf.sisgares.dominio.csv;

/** Linha de {@code dados-vinculo-recurso.csv} (VREC_ID, VREC_RECU_ID, VREC_AMBI_ID). */
public record LinhaVrec(long vrecId, long vrecRecuId, long vrecAmbiId) {
}
