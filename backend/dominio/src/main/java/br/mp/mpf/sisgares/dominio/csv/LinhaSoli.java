package br.mp.mpf.sisgares.dominio.csv;

/**
 * Linha de {@code dados-solicitacao.csv} (SOLI_ID, SOLI_RESE_ID, SOLI_RECU_ID, SOLI_QTD).
 * {@code soliQtd} é null quando SOLI_QTD vem vazio.
 */
public record LinhaSoli(long soliId, long soliReseId, long soliRecuId, Integer soliQtd) {
}
