package br.mp.mpf.sisgares.dominio.csv;

/** Linha de {@code dados-ambiente.csv} (AMBI_ID, AMBI_DESC, AMBI_ST_ATIVO, AMBI_ID_PAI). */
public record LinhaAmbi(long ambiId, String ambiDesc, String ambiStAtivo, Long ambiIdPai) {
}
