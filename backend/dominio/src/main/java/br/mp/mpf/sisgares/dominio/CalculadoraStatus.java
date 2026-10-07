package br.mp.mpf.sisgares.dominio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Calcula o Status de uma Reserva conforme a regra RN13 (Req. 9.16).
 *
 * <p>Precedência: cancelada &gt; prevista &gt; transcorrida &gt; em andamento.
 * <ul>
 *   <li>{@link Status#CANCELADA}: a reserva está cancelada;</li>
 *   <li>{@link Status#PREVISTA}: agora é anterior ao primeiro início;</li>
 *   <li>{@link Status#TRANSCORRIDA}: agora é igual ou posterior ao último término;</li>
 *   <li>{@link Status#EM_ANDAMENTO}: demais casos.</li>
 * </ul>
 *
 * <p>O instante atual vem do {@link Clock} injetado e é convertido para {@link ConstantesDominio#ZONA},
 * independentemente do fuso do relógio recebido.
 */
public final class CalculadoraStatus {

    private final Clock relogio;

    public CalculadoraStatus(Clock relogio) {
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    /**
     * Retorna exatamente um Status para a reserva no instante atual do relógio (RN13).
     *
     * <p>Reserva sem Períodos e não cancelada é tratada como {@link Status#PREVISTA}, pois nenhum
     * período começou.
     */
    public Status status(Reserva reserva) {
        Objects.requireNonNull(reserva, "reserva é obrigatória");
        if (reserva.cancelada()) {
            return Status.CANCELADA;
        }
        Optional<LocalDateTime> primeiroInicio = reserva.primeiroInicio();
        Optional<LocalDateTime> ultimoTermino = reserva.ultimoTermino();
        if (primeiroInicio.isEmpty() || ultimoTermino.isEmpty()) {
            return Status.PREVISTA;
        }
        // Data/hora local no fuso oficial do SISGARES
        LocalDateTime agora = LocalDateTime.ofInstant(relogio.instant(), ConstantesDominio.ZONA);
        if (agora.isBefore(primeiroInicio.get())) {
            return Status.PREVISTA;
        }
        if (!agora.isBefore(ultimoTermino.get())) {
            return Status.TRANSCORRIDA;
        }
        return Status.EM_ANDAMENTO;
    }
}
