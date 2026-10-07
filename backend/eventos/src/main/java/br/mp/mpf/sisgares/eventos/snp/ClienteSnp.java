package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.Metricas;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.eventos.notificador.EventoReserva;
import br.mp.mpf.sisgares.eventos.notificador.RegistroIdempotencia;
import br.mp.mpf.sisgares.eventos.notificador.TipoEvento;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Cliente_SNP: registra um Pedido_SNP por vínculo EAMB/EREC envolvido com código de serviço
 * (Req. 2.6, 14.1–14.5).
 *
 * <ol>
 *   <li>Idempotência por {@code eventoId} (consumidor {@code CLIENTE_SNP}).</li>
 *   <li>{@code ReservaCriada}: todos os vínculos com código. {@code ReservaAlterada}: só os vínculos
 *       que não estavam na versão anterior. {@code ReservaCancelada}: nenhum pedido.</li>
 *   <li>Vínculos que já têm {@code SNP#<vinculo>} REGISTRADO são ignorados (retry não duplica).</li>
 *   <li>Falha de um pedido grava {@code status=FALHA} e {@code motivoFalha}; ao final lança
 *       {@link FalhaSnpException} para o retry do Step Functions. A Reserva nunca é alterada.</li>
 * </ol>
 */
public final class ClienteSnp {

    private final RepositorioSnp repositorio;
    private final GatewaySnp gateway;
    private final Supplier<Optional<String>> endpoint;
    private final RegistroIdempotencia idempotencia;
    private final Clock clock;
    private final LogEstruturado log;
    private final Metricas metricas;

    /**
     * @param endpoint fornecedor da URL do SNP na Configuração (lida a cada evento, com cache do repositório)
     */
    public ClienteSnp(RepositorioSnp repositorio, GatewaySnp gateway, Supplier<Optional<String>> endpoint,
                      RegistroIdempotencia idempotencia, Clock clock, LogEstruturado log) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.idempotencia = Objects.requireNonNull(idempotencia, "idempotencia");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
        this.metricas = new Metricas(this.clock);
    }

    /** Resultado devolvido ao Step Functions. */
    public record Resultado(String eventoId, boolean jaProcessado, int registrados, int falhas) {
    }

    // ---------------------------------------------------------------- lógica pura

    /**
     * Decide quais vínculos precisam de Pedido_SNP (RN11; Req. 14.1, 14.2, 14.4).
     *
     * @param atuais      vínculos envolvidos na versão nova
     * @param anteriores  vínculos da versão anterior (vazio na inclusão)
     * @param registrados chaves de vínculo que já têm {@code SNP#<vinculo>} REGISTRADO
     * @return vínculos com código, novos e sem pedido, sem duplicatas e ordenados pela chave
     */
    public static List<VinculoSnp> selecionar(Collection<VinculoSnp> atuais, Collection<VinculoSnp> anteriores,
                                              Set<String> registrados) {
        Set<String> excluidos = new HashSet<>(registrados == null ? Set.of() : registrados);
        if (anteriores != null) {
            anteriores.forEach(v -> excluidos.add(v.chave()));
        }
        Map<String, VinculoSnp> pendentes = new TreeMap<>();
        if (atuais != null) {
            for (VinculoSnp v : atuais) {
                if (v.temCodigo() && !excluidos.contains(v.chave())) {
                    pendentes.putIfAbsent(v.chave(), v);
                }
            }
        }
        return List.copyOf(pendentes.values());
    }

    /** Vínculos EAMB do ambiente (exceto Local_Proprio) e EREC dos recursos solicitados. */
    public static List<VinculoSnp> vinculosEnvolvidos(Reserva reserva, RepositorioSnp fonte) {
        if (reserva == null) {
            return List.of();
        }
        List<VinculoSnp> vinculos = new ArrayList<>();
        if (reserva.ambienteId() != null) {
            vinculos.addAll(fonte.vinculosDoAmbiente(reserva.ambienteId()));
        }
        Set<Long> recursos = new TreeSet<>();
        for (Solicitacao s : reserva.solicitacoes()) {
            recursos.add(s.recursoId());
        }
        recursos.forEach(id -> vinculos.addAll(fonte.vinculosDoRecurso(id)));
        return vinculos;
    }

    // ---------------------------------------------------------------- coordenação

    /** Processa o evento; idempotente por {@code eventoId} (Req. 2.6). */
    public Resultado processar(EventoReserva evento) {
        Objects.requireNonNull(evento, "evento");
        if (!idempotencia.iniciar(evento.eventoId())) {
            log.info(evento.eventoId(), "Evento já processado pelo Cliente_SNP; nada a fazer.");
            return new Resultado(evento.eventoId(), true, 0, 0);
        }
        try {
            Resultado resultado = registrar(evento);
            idempotencia.concluir(evento.eventoId());
            return resultado;
        } catch (RuntimeException e) {
            idempotencia.falhar(evento.eventoId());
            // Uma métrica por tentativa falha do evento (Req. 21.2)
            metricas.contar(Metricas.FALHA_SNP);
            throw e;
        }
    }

    private Resultado registrar(EventoReserva evento) {
        if (evento.tipo() == TipoEvento.CANCELADA) {
            return new Resultado(evento.eventoId(), false, 0, 0);
        }
        Reserva nova = repositorio.versao(evento.reseId(), evento.versao())
                .or(() -> repositorio.reservaAtual(evento.reseId()))
                .orElseThrow(() -> new IllegalStateException("Reserva " + evento.reseId() + " não encontrada."));
        // Sem versão anterior, a alteração é tratada como inclusão
        List<VinculoSnp> anteriores = evento.tipo() == TipoEvento.ALTERADA && evento.versao() > 0
                ? repositorio.versao(evento.reseId(), evento.versao() - 1)
                        .map(r -> vinculosEnvolvidos(r, repositorio))
                        .orElse(List.of())
                : List.of();
        List<VinculoSnp> pendentes = selecionar(vinculosEnvolvidos(nova, repositorio), anteriores,
                repositorio.vinculosRegistrados(evento.reseId()));
        if (pendentes.isEmpty()) {
            log.info(evento.eventoId(), "Nenhum Pedido_SNP necessário.", Map.of("reseId", evento.reseId()));
            return new Resultado(evento.eventoId(), false, 0, 0);
        }

        int ano = nova.primeiroInicio().map(LocalDateTime::getYear).orElseGet(() -> LocalDate.now(clock).getYear());
        Optional<String> url = endpoint.get().filter(u -> !u.isBlank());
        int registrados = 0;
        int falhas = 0;
        for (VinculoSnp v : pendentes) {
            PedidoSnp pedido = new PedidoSnp(evento.reseId(), v.chave(), v.codigoServico(), v.envoId(), v.alvoId(), ano);
            try {
                RespostaSnp resposta = gateway.registrar(
                        url.orElseThrow(() -> new FalhaSnpException(FalhaSnpException.SEM_ENDPOINT,
                                "Endpoint do SNP não configurado.")),
                        pedido);
                repositorio.gravarRegistro(pedido, resposta);
                registrados++;
            } catch (RuntimeException e) {
                String motivo = e instanceof FalhaSnpException f ? f.motivo() : e.getClass().getSimpleName();
                repositorio.gravarFalha(pedido, motivo);
                falhas++;
                log.erro(evento.eventoId(), "Falha ao registrar Pedido_SNP do vínculo " + v.chave(), e);
            }
        }
        log.info(evento.eventoId(), "Pedidos SNP processados.",
                Map.of("reseId", evento.reseId(), "registrados", registrados, "falhas", falhas));
        if (falhas > 0) {
            throw new FalhaSnpException(FalhaSnpException.PEDIDOS_FALHOS,
                    "Falha em " + falhas + " Pedido(s)_SNP do evento " + evento.eventoId());
        }
        return new Resultado(evento.eventoId(), false, registrados, 0);
    }
}
