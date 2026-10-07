package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de ENVO (setores envolvidos); ENVO_EMAIL vazio → null. */
public final class MapeadorEnvo implements MapeadorLinha<LinhaEnvo> {

    public static final String ENVO_ID = "ENVO_ID";
    public static final String ENVO_DESC = "ENVO_DESC";
    public static final String ENVO_EMAIL = "ENVO_EMAIL";
    public static final String ENVO_ST_ATIVO = "ENVO_ST_ATIVO";

    private static final List<String> CABECALHO = List.of(ENVO_ID, ENVO_DESC, ENVO_EMAIL, ENVO_ST_ATIVO);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaEnvo deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaEnvo r = new LinhaEnvo(
                c.id(ENVO_ID),
                c.texto(ENVO_DESC),
                c.textoOpcional(ENVO_EMAIL),
                c.simNao(ENVO_ST_ATIVO));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaEnvo r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.envoId()),
                r.envoDesc(),
                r.envoEmail(),
                r.envoStAtivo());
    }
}
