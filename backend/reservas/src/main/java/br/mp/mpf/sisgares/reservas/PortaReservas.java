package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.SolicitacaoOcupada;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persistência usada pelo {@link ServicoReservas}. A implementação de produção é
 * {@link AdaptadorDynamo}; nos testes, uma implementação em memória.
 */
public interface PortaReservas {

    /** Sequência do RESE_ID ({@code PK=SEQ#RESE}). */
    String SEQUENCIA_RESERVA = "RESE";
    /** Sequência do PRES_ID ({@code PK=SEQ#PRES}). */
    String SEQUENCIA_PERIODO = "PRES";

    /**
     * Reserva {@code quantidade} ids consecutivos na sequência (contador atômico) e devolve o primeiro.
     *
     * @param sequencia {@link #SEQUENCIA_RESERVA} ou {@link #SEQUENCIA_PERIODO}
     * @param quantidade quantidade de ids (≥ 1)
     */
    long reservarIds(String sequencia, int quantidade);

    Optional<Reserva> obter(long id);

    /** Data de criação original (GSI4), preservada nas alterações. */
    Optional<LocalDateTime> criadoEm(long id);

    List<Reserva> porSolicitante(String sub);

    List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate);

    List<PeriodoOcupado> periodosOcupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim);

    List<SolicitacaoOcupada> solicitacoesOcupadasPorRecurso(long recursoId, LocalDateTime ini, LocalDateTime fim);

    /** Versão atual do Item_Controle ({@code SK=CTRL}); 0 quando não existe. */
    long lerVersaoControle(String pk);

    /** Gravação atômica (delegada ao {@code GravadorReservas}). */
    void gravar(Reserva r, LocalDateTime criadoEm, Long raizId, Set<Long> setores,
                Map<Long, Long> versoesCtrl, Long versaoLida, EventoOutbox evento);

    /** Snapshots {@code VERS#} em ordem crescente de número. */
    List<VersaoReserva> versoes(long id);

    /** Remove o evento do outbox após a publicação bem-sucedida (Req. 2.8). */
    void removerOutbox(EventoOutbox evento);

    /** Eventos ainda pendentes no outbox, em ordem cronológica (republicador). */
    List<EventoOutbox> listarOutboxPendentes();
}
