package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de GREC (grupos de recurso). */
public final class MapeadorGrec implements MapeadorLinha<LinhaGrec> {

    public static final String GREC_ID = "GREC_ID";
    public static final String GREC_DESC = "GREC_DESC";
    public static final String GREC_ORDEM = "GREC_ORDEM";
    public static final String GREC_ST_ATIVO = "GREC_ST_ATIVO";

    private static final List<String> CABECALHO = List.of(GREC_ID, GREC_DESC, GREC_ORDEM, GREC_ST_ATIVO);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaGrec deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaGrec r = new LinhaGrec(
                c.id(GREC_ID),
                c.texto(GREC_DESC),
                c.inteiro(GREC_ORDEM),
                c.simNao(GREC_ST_ATIVO));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaGrec r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.grecId()),
                r.grecDesc(),
                Integer.toString(r.grecOrdem()),
                r.grecStAtivo());
    }
}
