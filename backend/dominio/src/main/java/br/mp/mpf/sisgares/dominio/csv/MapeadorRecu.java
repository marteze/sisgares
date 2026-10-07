package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de RECU (recursos); o ícone é opcional e resolvido depois pelo ResolvedorIcone. */
public final class MapeadorRecu implements MapeadorLinha<LinhaRecu> {

    public static final String RECU_ID = "RECU_ID";
    public static final String RECU_DESC = "RECU_DESC";
    public static final String RECU_GREC_ID = "RECU_GREC_ID";
    public static final String RECU_ST_LIMITADO = "RECU_ST_LIMITADO";
    public static final String RECU_DISPONIBILIDADE = "RECU_DISPONIBILIDADE";
    public static final String RECU_ST_ATIVO = "RECU_ST_ATIVO";
    public static final String RECU_ICONE_ARQUIVO = "RECU_ICONE_ARQUIVO";

    private static final List<String> CABECALHO = List.of(RECU_ID, RECU_DESC, RECU_GREC_ID,
            RECU_ST_LIMITADO, RECU_DISPONIBILIDADE, RECU_ST_ATIVO, RECU_ICONE_ARQUIVO);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaRecu deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaRecu r = new LinhaRecu(
                c.id(RECU_ID),
                c.texto(RECU_DESC),
                c.id(RECU_GREC_ID),
                c.simNao(RECU_ST_LIMITADO),
                c.inteiro(RECU_DISPONIBILIDADE),
                c.simNao(RECU_ST_ATIVO),
                c.textoOpcional(RECU_ICONE_ARQUIVO));
        c.concluir();
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaRecu r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.recuId()),
                r.recuDesc(),
                CamposCsv.formatarId(r.recuGrecId()),
                r.recuStLimitado(),
                Integer.toString(r.recuDisponibilidade()),
                r.recuStAtivo(),
                r.recuIconeArquivo());
    }
}
