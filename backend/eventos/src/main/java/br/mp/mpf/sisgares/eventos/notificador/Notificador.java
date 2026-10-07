package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.dominio.ComparadorVersoes;
import br.mp.mpf.sisgares.dominio.Diferenca;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordena a notificação de um Evento_Reserva (Req. 2.6, 13.1–13.8).
 *
 * <ol>
 *   <li>Idempotência: {@link RegistroIdempotencia#iniciar}; entrega repetida conclui sem efeito.</li>
 *   <li>Carrega a versão nova ({@code VERS#}) e, na alteração, a anterior. Sem versão anterior,
 *       o evento é tratado como inclusão (sem diferenças).</li>
 *   <li>Resolve destinatários, monta o e-mail e envia por {@link EnviadorEmail}.</li>
 *   <li>Registra cada Notificação na {@link CaixaNotificacoes} com TTL de 90 dias, inclusive as
 *       falhas (Req. 13.7). Setores já atendidos numa tentativa anterior não recebem de novo.</li>
 * </ol>
 * Se algum envio falhar, o evento é marcado como falho e uma {@link FalhaNotificacaoException} é
 * lançada para que o Step Functions refaça a tentativa; a Reserva nunca é alterada.
 */
public final class Notificador {

    /** TTL das notificações e dos eventos processados. */
    public static final Duration TTL = Duration.ofDays(90);

    private final LeitorDados leitor;
    private final ResolvedorDestinatarios resolvedor;
    private final EnviadorEmail enviador;
    private final CaixaNotificacoes caixa;
    private final RegistroIdempotencia idempotencia;
    private final Clock clock;
    private final LogEstruturado log;

    public Notificador(LeitorDados leitor, EnviadorEmail enviador, CaixaNotificacoes caixa,
                       RegistroIdempotencia idempotencia, Clock clock, LogEstruturado log) {
        this.leitor = Objects.requireNonNull(leitor, "leitor");
        this.resolvedor = new ResolvedorDestinatarios(leitor);
        this.enviador = Objects.requireNonNull(enviador, "enviador");
        this.caixa = Objects.requireNonNull(caixa, "caixa");
        this.idempotencia = Objects.requireNonNull(idempotencia, "idempotencia");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Resultado do processamento, devolvido ao Step Functions. */
    public record Resultado(String eventoId, boolean jaProcessado, int enviadas, int ignoradas) {
    }

    /** Processa o evento; idempotente por {@code eventoId}. */
    public Resultado processar(EventoReserva evento) {
        Objects.requireNonNull(evento, "evento");
        if (!idempotencia.iniciar(evento.eventoId())) {
            log.info(evento.eventoId(), "Evento já processado; nada a fazer.");
            return new Resultado(evento.eventoId(), true, 0, 0);
        }
        try {
            Resultado resultado = notificar(evento);
            idempotencia.concluir(evento.eventoId());
            return resultado;
        } catch (RuntimeException e) {
            idempotencia.falhar(evento.eventoId());
            throw e;
        }
    }

    private Resultado notificar(EventoReserva evento) {
        VersaoReserva nova = carregarNova(evento);
        Optional<VersaoReserva> anterior = evento.tipo() == TipoEvento.ALTERADA && evento.versao() > 0
                ? leitor.versao(evento.reseId(), evento.versao() - 1)
                : Optional.empty();
        // Sem versão anterior, a alteração é tratada como inclusão
        TipoEvento tipo = evento.tipo() == TipoEvento.ALTERADA && anterior.isEmpty()
                ? TipoEvento.CRIADA
                : evento.tipo();
        List<Diferenca> diferencas = ComparadorVersoes.comparar(anterior.orElse(null), nova);

        List<Reserva> reservas = new ArrayList<>();
        reservas.add(nova.reserva());
        anterior.ifPresent(v -> reservas.add(v.reserva()));
        List<Destinatario> destinatarios = resolvedor.resolver(reservas);

        Email email = MontadorEmail.montar(conteudo(tipo, evento.reseId(), nova.reserva(), diferencas));

        int enviadas = 0;
        int ignoradas = 0;
        int falhas = 0;
        for (Destinatario d : destinatarios) {
            Optional<StatusNotificacao> atual = caixa.status(evento.reseId(), evento.eventoId(), d.envoId());
            if (atual.map(StatusNotificacao::concluida).orElse(false)) {
                ignoradas++;
                continue;
            }
            StatusNotificacao status;
            String motivo = null;
            try {
                status = enviador.enviar(d.emails(), email);
                enviadas++;
            } catch (RuntimeException e) {
                status = StatusNotificacao.FALHA;
                // Somente o tipo da exceção: a mensagem pode conter endereços de e-mail
                motivo = e.getClass().getSimpleName();
                falhas++;
                log.erro(evento.eventoId(), "Falha ao enviar notificação ao setor " + d.envoId(), e);
            }
            caixa.registrar(notificacao(evento, tipo, d, email, status, motivo));
        }
        log.info(evento.eventoId(), "Notificações processadas.",
                Map.of("reseId", evento.reseId(), "enviadas", enviadas, "ignoradas", ignoradas, "falhas", falhas));
        if (falhas > 0) {
            throw new FalhaNotificacaoException(
                    "Falha no envio de " + falhas + " notificação(ões) do evento " + evento.eventoId());
        }
        return new Resultado(evento.eventoId(), false, enviadas, ignoradas);
    }

    /** Versão nova pelo {@code VERS#}; se ausente, usa o estado atual da reserva. */
    private VersaoReserva carregarNova(EventoReserva evento) {
        return leitor.versao(evento.reseId(), evento.versao())
                .or(() -> leitor.reservaAtual(evento.reseId()).map(r -> new VersaoReserva(evento.versao(), r)))
                .orElseThrow(() -> new IllegalStateException("Reserva " + evento.reseId() + " não encontrada."));
    }

    private Notificacao notificacao(EventoReserva evento, TipoEvento tipo, Destinatario d, Email email,
                                    StatusNotificacao status, String motivo) {
        Instant agora = clock.instant();
        return new Notificacao(evento.reseId(), evento.eventoId(), d.envoId(), tipo, d.emails(),
                email.assunto(), email.html(), status, motivo, agora, agora.plus(TTL).getEpochSecond());
    }

    /** Converte IDs em descrições para o e-mail (Req. 13.3). */
    ConteudoEmail conteudo(TipoEvento tipo, long reseId, Reserva r, List<Diferenca> diferencas) {
        String ambiente;
        if (r.localProprio()) {
            ambiente = "Local próprio: " + textoOuVazio(r.complemento());
        } else {
            ambiente = leitor.descricaoAmbiente(r.ambienteId()).orElse("Ambiente " + r.ambienteId());
            if (r.complemento() != null && !r.complemento().isBlank()) {
                ambiente += " (" + r.complemento().strip() + ")";
            }
        }
        String disposicao = r.disposicaoId() == null ? ""
                : leitor.descricaoDisposicao(r.disposicaoId()).orElse("Disposição " + r.disposicaoId());
        List<String> periodos = r.periodos().stream()
                .sorted(Comparator.comparing(Periodo::inicio).thenComparing(Periodo::termino))
                .map(p -> p.inicio().format(ComparadorVersoes.FORMATO_DATA_HORA) + " a "
                        + p.termino().format(ComparadorVersoes.FORMATO_DATA_HORA))
                .toList();
        List<String> recursos = r.solicitacoes().stream()
                .sorted(Comparator.comparingLong(Solicitacao::recursoId))
                .map(s -> leitor.descricaoRecurso(s.recursoId()).orElse("Recurso " + s.recursoId())
                        + ": " + s.quantidade())
                .toList();
        return new ConteudoEmail(tipo, reseId, ambiente, textoOuVazio(r.finalidade()),
                r.participantes() == null ? "" : r.participantes().toString(), disposicao, periodos,
                recursos, textoOuVazio(r.solicitante()), leitor.pedidosSnp(reseId), diferencas);
    }

    private static String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
