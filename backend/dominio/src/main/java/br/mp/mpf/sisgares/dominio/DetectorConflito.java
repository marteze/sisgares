package br.mp.mpf.sisgares.dominio;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detecção de conflitos de horário entre Reservas (RN5 e RN6).
 *
 * <p>Dois Períodos conflitam quando {@code a.ini < b.fim + MARGEM && b.ini < a.fim + MARGEM},
 * com {@link ConstantesDominio#MARGEM} de 30 minutos. A regra é simétrica por construção.
 *
 * <ul>
 *   <li>RN5: conflito no mesmo Ambiente.</li>
 *   <li>RN6: conflito em Ambiente_Relacionado (ancestral ou descendente).</li>
 *   <li>Local_Proprio, reservas canceladas e a própria Reserva são ignorados.</li>
 * </ul>
 */
public final class DetectorConflito {

    /**
     * Indica se os Períodos conflitam considerando a Margem (Req. 10.1).
     *
     * @param a primeiro período
     * @param b segundo período
     * @return {@code true} se os intervalos, estendidos pela margem, se sobrepõem
     */
    public boolean conflita(Periodo a, Periodo b) {
        Objects.requireNonNull(a, "a é obrigatório");
        Objects.requireNonNull(b, "b é obrigatório");
        Duration margem = ConstantesDominio.MARGEM;
        return a.inicio().isBefore(b.termino().plus(margem))
                && b.inicio().isBefore(a.termino().plus(margem));
    }

    /**
     * Lista os conflitos da Reserva contra os Períodos ocupados (Req. 10.2, 10.3, 10.4, 10.8).
     *
     * @param r        reserva avaliada
     * @param ocupados períodos de outras reservas não canceladas
     * @param arv      árvore de Ambientes usada para RN6
     * @return conflitos encontrados (RN5 e/ou RN6), em ordem de período e de ocupação; vazio se não houver
     */
    public List<Conflito> conflitos(Reserva r, List<PeriodoOcupado> ocupados, ArvoreAmbientes arv) {
        Objects.requireNonNull(r, "r é obrigatório");
        Objects.requireNonNull(ocupados, "ocupados é obrigatório");
        Objects.requireNonNull(arv, "arv é obrigatório");

        // Local_Proprio não verifica conflito; reserva cancelada não ocupa nem conflita
        if (r.localProprio() || r.cancelada()) {
            return List.of();
        }
        long ambiente = r.ambienteId();

        List<Conflito> resultado = new ArrayList<>();
        for (Periodo periodo : r.periodos()) {
            for (PeriodoOcupado ocupado : ocupados) {
                // Ignora os períodos da própria Reserva (alteração)
                if (r.id() != null && r.id() == ocupado.reservaId()) {
                    continue;
                }
                String codigo = codigoConflito(ambiente, ocupado.ambienteId(), arv);
                if (codigo != null && conflita(periodo, ocupado.periodo())) {
                    resultado.add(new Conflito(codigo, periodo, ocupado.reservaId(), ocupado.ambienteId()));
                }
            }
        }
        return List.copyOf(resultado);
    }

    /** RN5 para o mesmo Ambiente, RN6 para Ambiente_Relacionado, {@code null} se não relacionados. */
    private String codigoConflito(long ambiente, long ambienteOcupado, ArvoreAmbientes arv) {
        if (ambiente == ambienteOcupado) {
            return CodigosRegra.RN5_CONFLITO_HORARIO;
        }
        if (arv.saoRelacionados(ambiente, ambienteOcupado)) {
            return CodigosRegra.RN6_CONFLITO_PAI_FILHO;
        }
        return null;
    }
}
