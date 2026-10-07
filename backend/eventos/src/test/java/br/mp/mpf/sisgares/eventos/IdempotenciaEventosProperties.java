package br.mp.mpf.sisgares.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import br.mp.mpf.sisgares.eventos.notificador.CaixaNotificacoes;
import br.mp.mpf.sisgares.eventos.notificador.EnviadorEmail;
import br.mp.mpf.sisgares.eventos.notificador.Email;
import br.mp.mpf.sisgares.eventos.notificador.EventoReserva;
import br.mp.mpf.sisgares.eventos.notificador.LeitorDados;
import br.mp.mpf.sisgares.eventos.notificador.Notificacao;
import br.mp.mpf.sisgares.eventos.notificador.Notificador;
import br.mp.mpf.sisgares.eventos.notificador.RegistroIdempotencia;
import br.mp.mpf.sisgares.eventos.notificador.StatusNotificacao;
import br.mp.mpf.sisgares.eventos.notificador.TipoEvento;
import br.mp.mpf.sisgares.eventos.snp.ClienteSnp;
import br.mp.mpf.sisgares.eventos.snp.PedidoSnp;
import br.mp.mpf.sisgares.eventos.snp.RepositorioSnp;
import br.mp.mpf.sisgares.eventos.snp.RespostaSnp;
import br.mp.mpf.sisgares.eventos.snp.VinculoSnp;
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
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property 10: Idempotência dos eventos. Reprocessar o mesmo Evento_Reserva (inclusive com
 * entregas duplicadas pelo barramento) produz exatamente o mesmo efeito observável que um
 * único processamento: as mesmas notificações na Caixa_Simulada e os mesmos Pedidos_SNP
 * registrados, sem envios nem chamadas extras.
 *
 * <p><b>Validates: Requirements 2.6</b>
 */
class IdempotenciaEventosProperties {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2030-01-10T12:00:00Z"), ZoneId.of("America/Fortaleza"));
    private static final LogEstruturado LOG =
            new LogEstruturado(CLOCK, "teste", new PrintStream(OutputStream.nullOutputStream()));

    /**
     * O Notificador é idempotente: qualquer número de entregas do mesmo eventoId envia os
     * e-mails uma só vez e deixa a Caixa_Simulada idêntica à de um processamento único.
     *
     * <p>// Feature: sisgares-reservas, Property 10: Idempotencia dos eventos (Notificador)
     */
    @Property(tries = 100)
    void notificadorProcessaEventoUmaUnicaVez(@ForAll("eventos") EventoReserva evento,
                                              @ForAll @IntRange(min = 1, max = 5) int entregas) {
        // Referência: uma única entrega
        Cenario ref = cenario();
        Notificador.Resultado primeira = ref.notificador.processar(evento);
        int enviosReferencia = ref.enviador.envios.size();
        Map<String, Notificacao> caixaReferencia = Map.copyOf(ref.caixa.itens);

        // Alvo: a mesma entrega repetida 'entregas' vezes
        Cenario alvo = cenario();
        Notificador.Resultado r0 = null;
        for (int i = 0; i < entregas; i++) {
            Notificador.Resultado r = alvo.notificador.processar(evento);
            if (i == 0) {
                r0 = r;
            } else {
                // Entregas repetidas são reconhecidas como já processadas, sem efeito
                assertThat(r.jaProcessado()).isTrue();
                assertThat(r.enviadas()).isZero();
            }
        }

        assertThat(r0.jaProcessado()).isEqualTo(primeira.jaProcessado());
        assertThat(r0.enviadas()).isEqualTo(primeira.enviadas());
        // Nenhum envio extra, independentemente do número de entregas
        assertThat(alvo.enviador.envios).hasSize(enviosReferencia);
        // Caixa_Simulada idêntica à do processamento único
        assertThat(alvo.caixa.itens).containsExactlyInAnyOrderEntriesOf(caixaReferencia);
    }

    /**
     * O Cliente_SNP é idempotente: reentregas do mesmo eventoId não geram Pedidos_SNP
     * adicionais nem novas chamadas ao gateway.
     *
     * <p>// Feature: sisgares-reservas, Property 10: Idempotencia dos eventos (Cliente_SNP)
     */
    @Property(tries = 100)
    void clienteSnpRegistraPedidosUmaUnicaVez(@ForAll("eventos") EventoReserva evento,
                                              @ForAll @IntRange(min = 1, max = 5) int entregas) {
        Cenario ref = cenario();
        ClienteSnp.Resultado primeira = ref.clienteSnp.processar(evento);
        int chamadasReferencia = ref.repositorioSnp.chamadasGateway;
        Map<String, String> registrosReferencia = Map.copyOf(ref.repositorioSnp.registros);

        Cenario alvo = cenario();
        ClienteSnp.Resultado r0 = null;
        for (int i = 0; i < entregas; i++) {
            ClienteSnp.Resultado r = alvo.clienteSnp.processar(evento);
            if (i == 0) {
                r0 = r;
            } else {
                assertThat(r.jaProcessado()).isTrue();
                assertThat(r.registrados()).isZero();
            }
        }

        assertThat(r0.registrados()).isEqualTo(primeira.registrados());
        assertThat(alvo.repositorioSnp.chamadasGateway).isEqualTo(chamadasReferencia);
        assertThat(alvo.repositorioSnp.registros).containsExactlyInAnyOrderEntriesOf(registrosReferencia);
    }

    // ---------- Geradores ----------

    @Provide
    Arbitrary<EventoReserva> eventos() {
        Arbitrary<TipoEvento> tipos = Arbitraries.of(TipoEvento.CRIADA, TipoEvento.ALTERADA, TipoEvento.CANCELADA);
        Arbitrary<Long> versoes = Arbitraries.longs().between(1, 2);
        return tipos.flatMap(tipo -> versoes.map(v -> new EventoReserva("evt-" + tipo + "-" + v, 7L, v, tipo)));
    }

    // ---------- Cenário com portas em memória ----------

    /** Monta um cenário novo com os mesmos dados de catálogo e uma reserva em duas versões. */
    private static Cenario cenario() {
        Cenario c = new Cenario();
        // Ambiente 10 → setores 1 (ativo) e 2 (ativo, lista alternativa); recurso 20 → setores 2 e 3 (inativo)
        c.dados.eamb.put(10L, List.of(1L, 2L));
        c.dados.erec.put(20L, List.of(2L, 3L));
        c.dados.setores.put(1L, setor(1, "s1@exemplo.gov.br", true, List.of()));
        c.dados.setores.put(2L, setor(2, "s2@exemplo.gov.br", true, List.of("alt@exemplo.gov.br")));
        c.dados.setores.put(3L, setor(3, "s3@exemplo.gov.br", false, List.of()));
        c.dados.versoes.put(1L, new VersaoReserva(1, reserva(14, 1)));
        c.dados.versoes.put(2L, new VersaoReserva(2, reserva(15, 2)));
        // Vínculos SNP: ambiente 10 com código, recurso 20 sem código
        c.repositorioSnp.vinculosAmbiente.put(10L, List.of(new VinculoSnp("EAMB#10", "SRV-SALA", 1, 10)));
        c.repositorioSnp.vinculosRecurso.put(20L, List.of(new VinculoSnp("EREC#20", null, 2, 20)));
        c.repositorioSnp.versoes.put(1L, reserva(14, 1));
        c.repositorioSnp.versoes.put(2L, reserva(15, 2));
        return c;
    }

    private static final class Cenario {
        final DadosMemoria dados = new DadosMemoria();
        final CaixaMemoria caixa = new CaixaMemoria();
        final IdempotenciaMemoria idemNotif = new IdempotenciaMemoria();
        final IdempotenciaMemoria idemSnp = new IdempotenciaMemoria();
        final EnviadorMemoria enviador = new EnviadorMemoria();
        final RepositorioSnpMemoria repositorioSnp = new RepositorioSnpMemoria();
        final Notificador notificador = new Notificador(dados, enviador, caixa, idemNotif, CLOCK, LOG);
        final ClienteSnp clienteSnp = new ClienteSnp(repositorioSnp, repositorioSnp::registrar,
                () -> Optional.of("https://snp.exemplo.gov.br"), idemSnp, CLOCK, LOG);
    }

    private static Reserva reserva(int hora, long versao) {
        LocalDateTime ini = LocalDateTime.of(2030, 3, 1, hora, 0);
        return new Reserva(7L, "PR/CE", "sub-ficticio", 10L, null, null, "Reunião", 10,
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

        @Override public StatusNotificacao enviar(List<String> para, Email email) {
            envios.add(para);
            return StatusNotificacao.SIMULADA;
        }
    }

    /** RepositorioSnp em memória que também faz as vezes de GatewaySnp (via referência de método). */
    private static final class RepositorioSnpMemoria implements RepositorioSnp {
        final Map<Long, List<VinculoSnp>> vinculosAmbiente = new HashMap<>();
        final Map<Long, List<VinculoSnp>> vinculosRecurso = new HashMap<>();
        final Map<Long, Reserva> versoes = new HashMap<>();
        final Map<String, String> registros = new HashMap<>();
        int chamadasGateway;

        @Override public Optional<Reserva> versao(long reseId, long numero) {
            return Optional.ofNullable(versoes.get(numero));
        }
        @Override public Optional<Reserva> reservaAtual(long reseId) { return Optional.empty(); }
        @Override public List<VinculoSnp> vinculosDoAmbiente(long ambienteId) {
            return vinculosAmbiente.getOrDefault(ambienteId, List.of());
        }
        @Override public List<VinculoSnp> vinculosDoRecurso(long recursoId) {
            return vinculosRecurso.getOrDefault(recursoId, List.of());
        }
        @Override public Set<String> vinculosRegistrados(long reseId) {
            return new TreeSet<>(registros.keySet());
        }
        @Override public void gravarRegistro(PedidoSnp pedido, RespostaSnp resposta) {
            registros.put(pedido.vinculo(), resposta.numero());
        }
        @Override public void gravarFalha(PedidoSnp pedido, String motivo) {
            // Nenhuma falha neste cenário.
        }

        /** Gateway determinístico: número por vínculo, sem efeitos externos. */
        RespostaSnp registrar(String endpoint, PedidoSnp pedido) {
            chamadasGateway++;
            String numero = "SNP-" + pedido.ano() + "-" + pedido.vinculo();
            return new RespostaSnp(numero, "https://snp.exemplo.gov.br/" + numero);
        }
    }
}
