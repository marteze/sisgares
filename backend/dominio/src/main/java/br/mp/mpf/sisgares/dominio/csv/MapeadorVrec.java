package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de VREC (vínculo recurso × ambiente). */
public final class MapeadorVrec implements MapeadorLinha<LinhaVrec> {

    public static final String VREC_ID = "VREC_ID";
    public static final String VREC_RECU_ID = "VREC_RECU_ID";
    public static final String VREC_AMBI_ID = "VREC_AMBI_ID";

    private static final List<String> CABECALHO = List.of(VREC_ID, VREC_RECU_ID, VREC_AMBI_ID);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaVrec deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaVrec r = new LinhaVrec(c.id(VREC_ID), c.id(VREC_RECU_ID), c.id(VREC_AMBI_ID));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaVrec r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.vrecId()),
                CamposCsv.formatarId(r.vrecRecuId()),
                CamposCsv.formatarId(r.vrecAmbiId()));
    }
}
