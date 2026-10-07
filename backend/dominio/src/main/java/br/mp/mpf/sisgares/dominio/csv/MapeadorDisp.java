package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de DISP (disposições); o ícone é opcional e resolvido depois pelo ResolvedorIcone. */
public final class MapeadorDisp implements MapeadorLinha<LinhaDisp> {

    public static final String DISP_ID = "DISP_ID";
    public static final String DISP_DESC = "DISP_DESC";
    public static final String DISP_ST_ATIVO = "DISP_ST_ATIVO";
    public static final String DISP_ICONE_ARQUIVO = "DISP_ICONE_ARQUIVO";

    private static final List<String> CABECALHO =
            List.of(DISP_ID, DISP_DESC, DISP_ST_ATIVO, DISP_ICONE_ARQUIVO);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaDisp deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaDisp r = new LinhaDisp(
                c.id(DISP_ID),
                c.texto(DISP_DESC),
                c.simNao(DISP_ST_ATIVO),
                c.textoOpcional(DISP_ICONE_ARQUIVO));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaDisp r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.dispId()),
                r.dispDesc(),
                r.dispStAtivo(),
                r.dispIconeArquivo());
    }
}
