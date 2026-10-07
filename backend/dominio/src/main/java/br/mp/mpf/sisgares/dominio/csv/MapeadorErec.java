package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de EREC (setor envolvido × recurso). */
public final class MapeadorErec implements MapeadorLinha<LinhaErec> {

    public static final String EREC_ID = "EREC_ID";
    public static final String EREC_ENVO_ID = "EREC_ENVO_ID";
    public static final String EREC_RECU_ID = "EREC_RECU_ID";

    private static final List<String> CABECALHO = List.of(EREC_ID, EREC_ENVO_ID, EREC_RECU_ID);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaErec deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaErec r = new LinhaErec(c.id(EREC_ID), c.id(EREC_ENVO_ID), c.id(EREC_RECU_ID));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaErec r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.erecId()),
                CamposCsv.formatarId(r.erecEnvoId()),
                CamposCsv.formatarId(r.erecRecuId()));
    }
}
