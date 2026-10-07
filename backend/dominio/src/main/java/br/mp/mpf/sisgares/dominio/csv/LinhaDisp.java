package br.mp.mpf.sisgares.dominio.csv;

/** Linha de {@code dados-disposicao.csv} (DISP_ID, DISP_DESC, DISP_ST_ATIVO, DISP_ICONE_ARQUIVO). */
public record LinhaDisp(long dispId, String dispDesc, String dispStAtivo, String dispIconeArquivo) {
}
