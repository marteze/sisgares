package br.mp.mpf.sisgares.dominio.csv;

/** Linha de {@code dados-grupo-recurso.csv} (GREC_ID, GREC_DESC, GREC_ORDEM, GREC_ST_ATIVO). */
public record LinhaGrec(long grecId, String grecDesc, int grecOrdem, String grecStAtivo) {
}
