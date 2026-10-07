package br.mp.mpf.sisgares.eventos.notificador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Notificador com implementações em memória das portas (sem AWS).
 *
 * <p><b>Validates: Requirements 2.6, 13.1, 13.2, 13.4, 13.7</b>
 */
class NotificadorTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2030-01-10T12:00:00Z"), ZoneId.of("America/Fortaleza"));

    private final DadosMemoria dados = new DadosMemoria();
    private final CaixaMemoria caixa = new CaixaMemoria();
    private final IdempotenciaMemoria idempotencia = new IdempotenciaMemoria();
    private final EnviadorMemoria enviador = new EnviadorMemoria();
    private final Notificador notificador = new Notificador(dados, enviador, caixa, idempotencia, CLOCK,
            new LogEstruturado(CLOCK, "teste", new PrintStream(OutputStream.nullOutputStream())));

    NotificadorTest() {
        // Ambiente 10 → setores 1 e 2; recurso 20 → setores 2 (duplicado) e 3 (inativo)
        dados.eamb.put(10L, List.of(1L, 2L));
        dados.erec.put(20L, List.of(2L, 3L));
        dados.setores.put(1L, setor(1, "s1@exemplo.gov.br", true, List.of()));
        dados.setores.put(2L, setor(2, "s2@exemplo.gov.br", true, List.of("alt@exemplo.gov.br")));
        dados.setores.put(3L, setor(3, "s3@exemplo.gov.br", false, List.of()));
    }

    @Test
    void destinatariosSaoUniaoDeSetoresAtivosComListaAlternativa() {
        dados.versoes.put(1L, new VersaoReserva(1, reserva(14, 1)));

        Notificador.Resultado r = notificador.processar(new EventoReserva("e1", 5, 1, TipoEvento.CRIADA));

        assertThat(r.enviadas()).isEqualTo(2);
        assertThat(enviador.envios).containsExactly(
                List.of("s1@exemplo.gov.br"), List.of("alt@exemplo.gov.br"));
        assertThat(caixa.itens.values()).allSatisfy(n -> {
            assertThat(n.status()).isEqualTo(StatusNotificacao.SIMULADA);
            assertThat(n.expiraEm()).isEqualTo(CLOCK.instant().plus(Notificador.TTL).getEpochSecond());
        });
    }

    @Test
    void entregaDuplicadaNaoGeraNovaNotificacao() {
        dados.versoes.put(1L, new VersaoReserva(1, reserva(14, 1)));
        EventoReserva evento = new EventoReserva("e2", 5, 1, TipoEvento.CRIADA);

        notificador.processar(evento);
        Notificador.Resultado segunda = notificador.processar(evento);

        assertThat(segunda.jaProcessado()).isTrue();
        assertThat(enviador.envios).hasSize(2);
    }

    @Test
    void alteracaoDestacaDiferencasESemVersaoAnteriorViraInclusao() {
        dados.versoes.put(1L, new VersaoReserva(1, reserva(14, 1)));
        dados.versoes.put(2L, new VersaoReserva(2, reserva(15, 2)));

        notificador.processar(new EventoReserva("e3", 5, 2, TipoEvento.ALTERADA));
        Notificacao alterada = caixa.itens.values().iterator().next();
        assertThat(alterada.tipo()).isEqualTo(TipoEvento.ALTERADA);
        assertThat(alterada.html()).contains("<del").contains("<ins");

        caixa.itens.clear();
        notificador.processar(new EventoReserva("e4", 5, 1, TipoEvento.ALTERADA));
        assertThat(caixa.itens.values()).allSatisfy(n -> {
            assertThat(n.tipo()).isEqualTo(TipoEvento.CRIADA);
            assertThat(n.html()).doesNotContain("<del");
        });
    }

    @Test
    void falhaDeEnvioEhRegistradaELiberaNovaTentativa() {
        dados.versoes.put(1L, new VersaoReserva(1, reserva(14, 1)));
        enviador.falhar = true;
        EventoReserva evento = new EventoReserva("e5", 5, 1, TipoEvento.CRIADA);

        assertThatThrownBy(() -> notificador.processar(evento)).isInstanceOf(FalhaNotificacaoException.class);
        assertThat(caixa.itens.values()).allSatisfy(n -> assertThat(n.status()).isEqualTo(StatusNotificacao.FALHA));

        enviador.falhar = false;
        Notificador.Resultado retry = notificador.processar(evento);
        assertThat(retry.enviadas()).isEqualTo(2);
    }

    // ---------- Dados e implementações em memória ----------

    private static Reserva reserva(int hora, long versao) {
        LocalDateTime ini = LocalDateTime.of(2030, 3, 1, hora, 0);
        return new Reserva(5L, "PR/CE", "sub-ficticio", 10L, null, null, "Reunião", 10,
                List.of(Periodo.de(ini, ini.plusHours(1))), List.of(new Solicitacao(20, 1)),
                false, null, null, versao);
    }

    private static Setor setor(long id, String email, boolean ativo, List<String> alternativos) {
        return new Setor(id, "Setor " + id, email, ativo, "PR/CE", alternativos);
    }

    private static final class DadosMemoria implements LeitorDados {
        final Map<Long, VersaoReserva> versoes = new HashMap<>();
        final Map<Long, List<Long>> eamb = new HashMap<>();
        final Map<Long, List<Long>> erec = new HashMap<>();
        final Map<Long, Setor> setores = new HashMap<>();

        @Override public Optional<VersaoReserva> versao(long reseId, long numero) {
            return Optional.ofNullable(versoes.get(numero));
        }
        @Override public Optional<Reserva> reservaAtual(long reseId) { return Optional.empty(); }
        @Override public List<String> pedidosSnp(long reseId) { return List.of(); }
        @Override public Optional<String> descricaoAmbiente(long id) { return Optional.of("Auditório"); }
        @Override public Optional<String> descricaoDisposicao(long id) { return Optional.empty(); }
        @Override public Optional<String> descricaoRecurso(long id) { return Optional.of("Projetor"); }
        @Override public List<Long> setoresDoAmbiente(long id) { return eamb.getOrDefault(id, List.of()); }
        @Override public List<Long> setoresDoRecurso(long id) { return erec.getOrDefault(id, List.of()); }
        @Override public Optional<Setor> setor(long id) { return Optional.ofNullable(setores.get(id)); }
    }

    private static final class CaixaMemoria implements CaixaNotificacoes {
        final Map<String, Notificacao> itens = new HashMap<>();

        @Override public void registrar(Notificacao n) {
            itens.put(n.reseId() + "#" + n.eventoId() + "#" + n.envoId(), n);
        }
        @Override public Optional<StatusNotificacao> status(long reseId, String eventoId, long envoId) {
            return Optional.ofNullable(itens.get(reseId + "#" + eventoId + "#" + envoId)).map(Notificacao::status);
        }
    }

    private static final class IdempotenciaMemoria implements RegistroIdempotencia {
        final Set<String> concluidos = new HashSet<>();
        final Set<String> emProcessamento = new HashSet<>();

        @Override public boolean iniciar(String id) {
            return !concluidos.contains(id) && emProcessamento.add(id);
        }
        @Override public void concluir(String id) { emProcessamento.remove(id); concluidos.add(id); }
        @Override public void falhar(String id) { emProcessamento.remove(id); }
    }

    private static final class EnviadorMemoria implements EnviadorEmail {
        final List<List<String>> envios = new ArrayList<>();
        boolean falhar;

        @Override public StatusNotificacao enviar(List<String> para, Email email) {
            if (falhar) {
                throw new IllegalStateException("SES indisponível");
            }
            envios.add(para);
            return StatusNotificacao.SIMULADA;
        }
    }
}
