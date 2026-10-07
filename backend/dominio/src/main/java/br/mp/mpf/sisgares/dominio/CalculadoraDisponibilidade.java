package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Calcula a quantidade disponível de um Recurso em um período (RN8, Requisito 11.3).
 *
 * <p>A disponibilidade é {@code RECU_DISPONIBILIDADE} menos o pico de uso simultâneo dentro de
 * [início, término) do período consultado, obtido por varredura de eventos. Não há margem de
 * 30 minutos para recursos: os intervalos são semiabertos, então períodos adjacentes não se somam.
 */
public final class CalculadoraDisponibilidade {

    /** Evento da varredura: em {@code instante}, o uso varia em {@code delta}. */
    private record Evento(LocalDateTime instante, int delta) {
    }

    /**
     * Retorna a quantidade do recurso ainda disponível no período.
     *
     * @param r      recurso consultado
     * @param p      período consultado (semiaberto)
     * @param outras solicitações já comprometidas por outras reservas não canceladas
     * @return disponibilidade do recurso menos o pico de uso simultâneo no período
     */
    public int disponivel(Recurso r, Periodo p, List<SolicitacaoOcupada> outras) {
        Objects.requireNonNull(r, "recurso é obrigatório");
        Objects.requireNonNull(p, "periodo é obrigatório");
        if (outras == null || outras.isEmpty()) {
            return r.disponibilidade();
        }

        List<Evento> eventos = new ArrayList<>();
        for (SolicitacaoOcupada s : outras) {
            // Considera apenas solicitações do mesmo recurso e com quantidade efetiva
            if (s == null || s.recursoId() != r.id() || s.quantidade() == 0) {
                continue;
            }
            // Recorta o intervalo da solicitação ao período consultado
            LocalDateTime ini = max(s.periodo().inicio(), p.inicio());
            LocalDateTime fim = min(s.periodo().termino(), p.termino());
            // Interseção vazia (inclui adjacência, por ser semiaberto): ignora
            if (!ini.isBefore(fim)) {
                continue;
            }
            eventos.add(new Evento(ini, s.quantidade()));
            eventos.add(new Evento(fim, -s.quantidade()));
        }

        // Ordena por instante; no mesmo instante, saídas antes de entradas (intervalo semiaberto)
        eventos.sort(Comparator.comparing(Evento::instante).thenComparingInt(Evento::delta));

        int uso = 0;
        int pico = 0;
        for (Evento e : eventos) {
            uso += e.delta();
            pico = Math.max(pico, uso);
        }
        return r.disponibilidade() - pico;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
