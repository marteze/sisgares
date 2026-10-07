package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/** Mapeador de PRES (períodos de reserva); início deve ser anterior ao término. */
public final class MapeadorPres implements MapeadorLinha<LinhaPres> {

    public static final String PRES_ID = "PRES_ID";
    public static final String PRES_RESE_ID = "PRES_RESE_ID";
    public static final String PRES_DTHR_INICIO = "PRES_DTHR_INICIO";
    public static final String PRES_DTHR_TERMINO = "PRES_DTHR_TERMINO";

    private static final List<String> CABECALHO =
            List.of(PRES_ID, PRES_RESE_ID, PRES_DTHR_INICIO, PRES_DTHR_TERMINO);

    @Override
    public List<String> cabecalho() {
        return CABECALHO;
    }

    @Override
    public LinhaPres deLinha(Map<String, String> linha) {
        CamposCsv c = new CamposCsv(linha);
        LinhaPres r = new LinhaPres(
                c.id(PRES_ID),
                c.id(PRES_RESE_ID),
                c.dataHora(PRES_DTHR_INICIO),
                c.dataHora(PRES_DTHR_TERMINO));
        c.concluir();
        if (!r.presDthrInicio().isBefore(r.presDthrTermino())) {
            throw new FormatoInvalidoException(PRES_DTHR_INICIO + ": deve ser anterior a " + PRES_DTHR_TERMINO);
        }
        return r;
    }

    @Override
    public Map<String, String> paraLinha(LinhaPres r) {
        return CamposCsv.linha(CABECALHO,
                CamposCsv.formatarId(r.presId()),
                CamposCsv.formatarId(r.presReseId()),
                CamposCsv.formatarDataHora(r.presDthrInicio()),
                CamposCsv.formatarDataHora(r.presDthrTermino()));
    }
}
