package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de SOLI (solicitações de recurso); SOLI_QTD vazio → null. */
public final class MapeadorSoli implements MapeadorLinha<LinhaSoli> {

    public static final String SOLI_ID = "SOLI_ID";
    public static final String SOLI_RESE_ID = "SOLI_RESE_ID";
    public static final String SOLI_RECU_ID = "SOLI_RECU_ID";
    public static final String SOLI_QTD = "SOLI_QTD";

    private static final List<String> CABECALHO = List.of(SOLI_ID, SOLI_RESE_ID, SOLI_RECU_ID, SOLI_QTD);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaSoli deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaSoli r = new LinhaSoli(
                c.id(SOLI_ID),
                c.id(SOLI_RESE_ID),
                c.id(SOLI_RECU_ID),
                c.inteiroOpcional(SOLI_QTD));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaSoli r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.soliId()),
                CamposCsv.formatarId(r.soliReseId()),
                CamposCsv.formatarId(r.soliRecuId()),
                CamposCsv.formatarInteiro(r.soliQtd()));
    }
}
