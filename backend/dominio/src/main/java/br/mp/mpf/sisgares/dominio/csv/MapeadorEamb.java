package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de EAMB (setor envolvido × ambiente). */
public final class MapeadorEamb implements MapeadorLinha<LinhaEamb> {

    public static final String EAMB_ID = "EAMB_ID";
    public static final String EAMB_ENVO_ID = "EAMB_ENVO_ID";
    public static final String EAMB_AMBI_ID = "EAMB_AMBI_ID";

    private static final List<String> CABECALHO = List.of(EAMB_ID, EAMB_ENVO_ID, EAMB_AMBI_ID);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaEamb deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaEamb r = new LinhaEamb(c.id(EAMB_ID), c.id(EAMB_ENVO_ID), c.id(EAMB_AMBI_ID));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaEamb r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.eambId()),
                CamposCsv.formatarId(r.eambEnvoId()),
                CamposCsv.formatarId(r.eambAmbiId()));
    }
}
