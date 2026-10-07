package br.mp.mpf.sisgares.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoConflitoConcorrente;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.CodigosRegra;
import br.mp.mpf.sisgares.dominio.Reserva;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

/**
 * Testes do {@link Handler} de reservas com dublês (tarefa 10.5): repositórios em memória e
 * EventBridge mockado (Mockito). Cobrem os códigos HTTP 201/409/422, o acúmulo de todas as
 * violações na resposta, o formato padrão de erro e a falha de {@code PutEvents} que mantém o
 * Evento_Reserva no outbox.
 *
 * <p><b>Validates: Requirements 1.6, 2.8, 22.6</b>
 */
class HandlerDublesTest {

    private static final String BARRAMENTO = "sisgares-bus";
    private static final String CORPO_VALIDO = """
            {"ambienteId":"1","finalidade":"Reunião","participantes":5,
             "periodos":[{"inicio":"2030-01-12T09:00","termino":"2030-01-12T11:00"}],"recursos":[]}""";

    /**
     * Decorador de {@link MemoriaReservas} ({@code final}) que pode falhar a gravação para simular
     * o conflito otimista (RN7 → 409). Delega as demais operações à memória real.
     */
    private static final class MemoriaComFalha implements PortaReservas {
        private final MemoriaReservas memoria = new MemoriaReservas();
        boolean falharAoGravar;

        List<EventoOutbox> eventos() {
            return memoria.eventos;
        }

        List<EventoOutbox> outbox() {
            return memoria.outbox;
        }

        @Override
        public void gravar(Reserva r, LocalDateTime criadoEm, Long raizId, Set<Long> setores,
                           Map<Long, Long> versoesCtrl, Long versaoLida, EventoOutbox evento) {
            if (falharAoGravar) {
                throw new ExcecaoConflitoConcorrente("RN7: condição otimista falhou ao salvar");
            }
            memoria.gravar(r, criadoEm, raizId, setores, versoesCtrl, versaoLida, evento);
        }

        @Override
        public long reservarIds(String sequencia, int quantidade) {
            return memoria.reservarIds(sequencia, quantidade);
        }

        @Override
        public java.util.Optional<Reserva> obter(long id) {
            return memoria.obter(id);
        }

        @Override
        public java.util.Optional<LocalDateTime> criadoEm(long id) {
            return memoria.criadoEm(id);
        }

        @Override
        public List<Reserva> porSolicitante(String sub) {
            return memoria.porSolicitante(sub);
        }

        @Override
        public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
            return memoria.porUnidade(unidade, de, ate);
        }

        @Override
        public List<br.mp.mpf.sisgares.dominio.PeriodoOcupado> periodosOcupadosPorRaiz(long raizId,
                LocalDateTime ini, LocalDateTime fim) {
            return memoria.periodosOcupadosPorRaiz(raizId, ini, fim);
        }

        @Override
        public List<br.mp.mpf.sisgares.dominio.SolicitacaoOcupada> solicitacoesOcupadasPorRecurso(
                long recursoId, LocalDateTime ini, LocalDateTime fim) {
            return memoria.solicitacoesOcupadasPorRecurso(recursoId, ini, fim);
        }

        @Override
        public long lerVersaoControle(String pk) {
            return memoria.lerVersaoControle(pk);
        }

        @Override
        public List<br.mp.mpf.sisgares.dominio.VersaoReserva> versoes(long id) {
            return memoria.versoes(id);
        }

        @Override
        public void removerOutbox(EventoOutbox evento) {
            memoria.removerOutbox(evento);
        }

        @Override
        public List<EventoOutbox> listarOutboxPendentes() {
            return memoria.listarOutboxPendentes();
        }
    }

    private final MemoriaComFalha memoria = new MemoriaComFalha();
    private final MemoriaReservas catalogo = criarCatalogo();

    private static MemoriaReservas criarCatalogo() {
        MemoriaReservas m = new MemoriaReservas();
        m.ambientes.add(new Ambiente(1L, "Auditório", true, null, ServicoReservasTest.UNIDADE));
        return m;
    }

    private Handler handler(PublicadorEventos publicador) {
        LogEstruturado log = new LogEstruturado(ServicoReservasTest.RELOGIO, "teste",
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        ServicoReservas servico = new ServicoReservas(memoria, catalogo, ServicoReservasTest.RELOGIO,
                ServicoReservas.ResolvedorNome.doPrincipal(), publicador, log);
        return new Handler(servico, new ExtratorPrincipal(true, ServicoReservasTest.RELOGIO),
                new MapeadorErros(log));
    }

    /** Publicador nulo (não falha): o evento é removido do outbox após o commit. */
    private Handler handler() {
        LogEstruturado log = new LogEstruturado(ServicoReservasTest.RELOGIO, "teste",
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        return handler(PublicadorEventos.nulo(log));
    }

    private APIGatewayV2HTTPResponse chamar(Handler handler, String metodo, String caminho, String corpo) {
        return handler.handleRequest(HandlerTest.evento(metodo, caminho, corpo, "sub-1"), null);
    }

    private static JsonNode json(APIGatewayV2HTTPResponse r) throws Exception {
        return Json.mapper().readTree(r.getBody());
    }

    @Test
    void post_valido_responde201ERemoveEventoDoOutbox() throws Exception {
        Handler handler = handler();

        APIGatewayV2HTTPResponse r = chamar(handler, "POST", "/api/reservas", CORPO_VALIDO);

        assertThat(r.getStatusCode()).isEqualTo(201);
        assertThat(json(r).get("status").asText()).isEqualTo("PREVISTA");
        // Evento gravado na transação e removido do outbox após a publicação bem-sucedida (Req. 2.8)
        assertThat(memoria.eventos()).singleElement()
                .satisfies(e -> assertThat(e.tipo()).isEqualTo(ServicoReservas.EVENTO_CRIADA));
        assertThat(memoria.outbox()).isEmpty();
    }

    @Test
    void post_conflitoOtimista_responde409NoFormatoPadrao() throws Exception {
        Handler handler = handler();
        memoria.falharAoGravar = true;

        APIGatewayV2HTTPResponse r = chamar(handler, "POST", "/api/reservas", CORPO_VALIDO);

        assertThat(r.getStatusCode()).isEqualTo(409);
        JsonNode corpo = json(r);
        assertThat(corpo.at("/erros/0/codigo").asText()).isEqualTo(CodigosRegra.RN7_CONFLITO_AO_SALVAR);
        assertThat(corpo.get("correlationId").asText()).isNotBlank();
        assertThat(memoria.eventos()).isEmpty();
    }

    @Test
    void post_variasViolacoes_responde422ComTodasAsViolacoes() throws Exception {
        Handler handler = handler();
        // Finalidade em branco e participantes nulos: duas violações RN2 acumuladas
        String corpo = """
                {"ambienteId":"1","finalidade":"  ","participantes":null,
                 "periodos":[{"inicio":"2030-01-12T09:00","termino":"2030-01-12T11:00"}],"recursos":[]}""";

        APIGatewayV2HTTPResponse r = chamar(handler, "POST", "/api/reservas", corpo);

        assertThat(r.getStatusCode()).isEqualTo(422);
        JsonNode erros = json(r).get("erros");
        List<String> codigos = new ArrayList<>();
        erros.forEach(e -> codigos.add(e.get("codigo").asText()));
        assertThat(codigos).contains(CodigosRegra.RN2_FINALIDADE_OBRIGATORIA,
                CodigosRegra.RN2_PARTICIPANTES_OBRIGATORIO);
        // Formato padrão: cada erro tem código e mensagem; nenhum evento gravado
        assertThat(erros).allSatisfy(e -> {
            assertThat(e.get("codigo").asText()).isNotBlank();
            assertThat(e.get("mensagem").asText()).isNotBlank();
        });
        assertThat(memoria.eventos()).isEmpty();
    }

    @Test
    void post_falhaDePutEvents_mantemEventoNoOutbox() throws Exception {
        // Dublê do EventBridge: PutEvents reporta falha parcial (failedEntryCount > 0)
        EventBridgeClient cliente = mock(EventBridgeClient.class);
        PutEventsResponse resposta = PutEventsResponse.builder()
                .failedEntryCount(1)
                .entries(PutEventsResultEntry.builder().errorCode("InternalException").build())
                .build();
        when(cliente.putEvents(any(PutEventsRequest.class))).thenReturn(resposta);
        Handler handler = handler(new PublicadorEventBridge(cliente, BARRAMENTO));

        APIGatewayV2HTTPResponse r = chamar(handler, "POST", "/api/reservas", CORPO_VALIDO);

        // A resposta continua de sucesso; o evento permanece no outbox para o republicador (Req. 2.8)
        assertThat(r.getStatusCode()).isEqualTo(201);
        verify(cliente).putEvents(any(PutEventsRequest.class));
        assertThat(memoria.outbox()).singleElement()
                .satisfies(e -> assertThat(e.tipo()).isEqualTo(ServicoReservas.EVENTO_CRIADA));
    }

    @Test
    void post_publicacaoBemSucedida_publicaEventoUmaVez() throws Exception {
        PublicadorEventos publicador = mock(PublicadorEventos.class);
        Handler handler = handler(publicador);

        APIGatewayV2HTTPResponse r = chamar(handler, "POST", "/api/reservas", CORPO_VALIDO);

        assertThat(r.getStatusCode()).isEqualTo(201);
        verify(publicador).publicar(any(EventoOutbox.class));
        // Removido do outbox: a publicação não lançou
        assertThat(memoria.outbox()).isEmpty();
    }

    @Test
    void post_semToken_responde401SemGravarEvento() {
        Handler handler = handler();

        APIGatewayV2HTTPResponse r = handler.handleRequest(
                HandlerTest.evento("POST", "/api/reservas", CORPO_VALIDO, null), null);

        assertThat(r.getStatusCode()).isEqualTo(401);
        assertThat(memoria.eventos()).isEmpty();
        assertThat(memoria.outbox()).isEmpty();
    }
}
