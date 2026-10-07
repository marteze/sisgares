package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import java.util.List;
import java.util.Optional;

/**
 * Leituras de reserva, versões e catálogo necessárias ao Notificador.
 */
public interface LeitorDados extends FonteSetores {

    /** Versão_Reserva em {@code RESE#<id>} / {@code VERS#<000n>}. */
    Optional<VersaoReserva> versao(long reseId, long numero);

    /** Estado atual da reserva ({@code RESE#<id>}), usado se a versão não existir. */
    Optional<Reserva> reservaAtual(long reseId);

    /** Números de Pedido_SNP já gravados ({@code SNP#}). */
    List<String> pedidosSnp(long reseId);

    Optional<String> descricaoAmbiente(long ambienteId);

    Optional<String> descricaoDisposicao(long disposicaoId);

    Optional<String> descricaoRecurso(long recursoId);
}
