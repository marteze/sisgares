package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Recurso;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.SolicitacaoOcupada;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementação em memória das portas, com o mesmo contrato do {@link AdaptadorDynamo}
 * (sem rede). Guarda as versões gravadas, todos os eventos gravados ({@code eventos}) e os
 * pendentes no outbox ({@code outbox}) para as asserções.
 */
final class MemoriaReservas implements PortaReservas, PortaCatalogo {

    final Map<Long, Reserva> atuais = new HashMap<>();
    final Map<Long, LocalDateTime> criacoes = new HashMap<>();
    final Map<Long, List<VersaoReserva>> historico = new HashMap<>();
    /** Todos os eventos gravados, mesmo os já publicados e removidos do outbox. */
    final List<EventoOutbox> eventos = new ArrayList<>();
    /** Eventos pendentes no outbox (removidos após a publicação). */
    final List<EventoOutbox> outbox = new ArrayList<>();
    final List<Ambiente> ambientes = new ArrayList<>();
    final Map<Long, Recurso> recursos = new HashMap<>();
    final Map<Long, Set<Long>> setoresAmbiente = new HashMap<>();
    private final Map<String, Long> sequencias = new HashMap<>();
    Configuracao configuracao = new Configuracao(60,
            new Configuracao.FaixaHoraria(LocalTime.of(7, 0), LocalTime.of(22, 0)), null);

    @Override
    public long reservarIds(String sequencia, int quantidade) {
        long ultimo = sequencias.merge(sequencia, (long) quantidade, Long::sum);
        return ultimo - quantidade + 1;
    }

    @Override
    public Optional<Reserva> obter(long id) {
        return Optional.ofNullable(atuais.get(id));
    }

    @Override
    public Optional<LocalDateTime> criadoEm(long id) {
        return Optional.ofNullable(criacoes.get(id));
    }

    @Override
    public List<Reserva> porSolicitante(String sub) {
        return atuais.values().stream().filter(r -> sub.equals(r.solicitante())).toList();
    }

    @Override
    public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
        return atuais.values().stream().filter(r -> unidade.equals(r.unidade())).toList();
    }

    @Override
    public List<PeriodoOcupado> periodosOcupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim) {
        List<PeriodoOcupado> lista = new ArrayList<>();
        for (Reserva r : atuais.values()) {
            if (!r.cancelada() && r.ambienteId() != null) {
                for (Periodo p : r.periodos()) {
                    lista.add(new PeriodoOcupado(r.id(), r.ambienteId(), p));
                }
            }
        }
        return lista;
    }

    @Override
    public List<SolicitacaoOcupada> solicitacoesOcupadasPorRecurso(long recursoId, LocalDateTime ini,
                                                                   LocalDateTime fim) {
        return List.of();
    }

    @Override
    public long lerVersaoControle(String pk) {
        return 0L;
    }

    @Override
    public void gravar(Reserva r, LocalDateTime criadoEm, Long raizId, Set<Long> setores,
                       Map<Long, Long> versoesCtrl, Long versaoLida, EventoOutbox evento) {
        atuais.put(r.id(), r);
        criacoes.putIfAbsent(r.id(), criadoEm);
        historico.computeIfAbsent(r.id(), k -> new ArrayList<>()).add(new VersaoReserva(r.versao(), r));
        eventos.add(evento);
        outbox.add(evento);
    }

    @Override
    public void removerOutbox(EventoOutbox evento) {
        outbox.removeIf(e -> e.eventoId().equals(evento.eventoId()));
    }

    @Override
    public List<EventoOutbox> listarOutboxPendentes() {
        return List.copyOf(outbox);
    }

    @Override
    public List<VersaoReserva> versoes(long id) {
        return historico.getOrDefault(id, List.of());
    }

    @Override
    public List<Ambiente> ambientes() {
        return ambientes;
    }

    @Override
    public Optional<Recurso> recurso(long id) {
        return Optional.ofNullable(recursos.get(id));
    }

    @Override
    public Set<Long> setoresDoAmbiente(long ambienteId) {
        return setoresAmbiente.getOrDefault(ambienteId, Set.of());
    }

    @Override
    public Set<Long> setoresDoRecurso(long recursoId) {
        return Set.of();
    }

    @Override
    public Configuracao configuracao() {
        return configuracao;
    }
}
