package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de AMBI (ambientes); AMBI_ID_PAI vazio → null. */
public final class MapeadorAmbi implements MapeadorLinha<LinhaAmbi> {

    public static final String AMBI_ID = "AMBI_ID";
    public static final String AMBI_DESC = "AMBI_DESC";
    public static final String AMBI_ST_ATIVO = "AMBI_ST_ATIVO";
    public static final String AMBI_ID_PAI = "AMBI_ID_PAI";

    private static final List<String> CABECALHO = List.of(AMBI_ID, AMBI_DESC, AMBI_ST_ATIVO, AMBI_ID_PAI);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaAmbi deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaAmbi r = new LinhaAmbi(
                c.id(AMBI_ID),
                c.texto(AMBI_DESC),
                c.simNao(AMBI_ST_ATIVO),
                c.idOpcional(AMBI_ID_PAI));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaAmbi r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.ambiId()),
                r.ambiDesc(),
                r.ambiStAtivo(),
                CamposCsv.formatarId(r.ambiIdPai()));
    }
}
