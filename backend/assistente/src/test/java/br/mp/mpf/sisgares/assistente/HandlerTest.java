package br.mp.mpf.sisgares.assistente;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Handler do Assistente com modelo falso (sem chamadas à AWS). */
class HandlerTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneId.of("America/Fortaleza"));
    private static final String SUB = "sub-fake-123";
    private static final String EMAIL = "fulano@exemplo.gov.br";
    private static final String NOME = "Fulano de Teste";

    /** Modelo falso que registra as mensagens recebidas e devolve uma resposta fixa. */
    static final class ModeloFalso implements ModeloLinguagem {
        final List<String> mensagens = new ArrayList<>();
        final String resposta;
        final long atrasoMs;
        final RuntimeException erro;

        ModeloFalso(String resposta, long atrasoMs, RuntimeException erro) {
            this.resposta = resposta;
            this.atrasoMs = atrasoMs;
            this.erro = erro;
        }

        @Override
        public String gerar(String instrucoes, String mensagem) {
            mensagens.add(instrucoes + "\n" + mensagem);
            if (atrasoMs > 0) {
                try {
                    Thread.sleep(atrasoMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (erro != null) {
                throw erro;
            }
            return resposta;
        }
    }

    private static Handler handler(ModeloLinguagem modelo, Duration timeout) {
        return new Handler(modelo, () -> SaneadorPropostaTest.CATALOGO, new ExtratorPrincipal(false, RELOGIO),
                new LogEstruturado(RELOGIO, "assistente", new PrintStream(new ByteArrayOutputStream())),
                RELOGIO, timeout);
    }

    private static APIGatewayV2HTTPEvent evento(String corpo, String grupos) {
        var jwt = APIGatewayV2HTTPEvent.RequestContext.Authorizer.JWT.builder()
                .withClaims(Map.of("sub", SUB, "email", EMAIL, "name", NOME, "cognito:groups", grupos))
                .build();
        return APIGatewayV2HTTPEvent.builder()
                .withRawPath(Handler.ROTA)
                .withBody(corpo)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withRequestId("req-1")
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod("POST")
                                .withPath(Handler.ROTA).build())
                        .withAuthorizer(APIGatewayV2HTTPEvent.RequestContext.Authorizer.builder()
                                .withJwt(jwt).build())
                        .build())
                .build();
    }

    private static String corpo(String descricao) {
        return Json.escrever(Map.of("descricao", descricao));
    }

    @Test
    void devolvePropostaSaneadaEPromptSemDadosPessoais() throws Exception {
        var modelo = new ModeloFalso("{\"ambienteId\":12,\"data\":\"2026-10-21\",\"inicio\":\"14:00\","
                + "\"participantes\":30,\"recursos\":[{\"recursoId\":5,\"quantidade\":1},"
                + "{\"recursoId\":99,\"quantidade\":1}]}", 0, null);

        APIGatewayV2HTTPResponse r = handler(modelo, Duration.ofSeconds(10)).handleRequest(
                evento(corpo("reunião amanhã às 14h no auditório para 30 pessoas com projetor"),
                        "[Solicitante]"), null);

        assertThat(r.getStatusCode()).isEqualTo(200);
        JsonNode json = Json.mapper().readTree(r.getBody());
        assertThat(json.at("/proposta/ambienteId").asLong()).isEqualTo(12);
        assertThat(json.at("/proposta/recursos")).hasSize(1);
        assertThat(json.get("camposNaoPreenchidos").toString())
                .contains("termino", "finalidade", "recursos[1]");
        // Prompt: descrição, data atual e catálogo; nunca sub, e-mail ou nome
        String prompt = modelo.mensagens.get(0);
        assertThat(prompt).contains("2026-10-20", "auditório", "Projetor", "Sala 1")
                .doesNotContain(SUB, EMAIL, NOME);
    }

    @Test
    void descricaoAcimaDe1000CaracteresRetorna422() {
        var modelo = new ModeloFalso("{}", 0, null);
        APIGatewayV2HTTPResponse r = handler(modelo, Duration.ofSeconds(10))
                .handleRequest(evento(corpo("a".repeat(1001)), "[Solicitante]"), null);

        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(modelo.mensagens).isEmpty();
    }

    @Test
    void descricaoAusenteOuCampoExtraRetorna422() {
        var h = handler(new ModeloFalso("{}", 0, null), Duration.ofSeconds(10));
        assertThat(h.handleRequest(evento("{}", "[Solicitante]"), null).getStatusCode()).isEqualTo(422);
        assertThat(h.handleRequest(evento("{\"descricao\":\"x\",\"sub\":\"y\"}", "[Solicitante]"), null)
                .getStatusCode()).isEqualTo(422);
    }

    @Test
    void timeoutDoModeloRetorna503() {
        var modelo = new ModeloFalso("{}", 2000, null);
        APIGatewayV2HTTPResponse r = handler(modelo, Duration.ofMillis(100))
                .handleRequest(evento(corpo("reunião"), "[Solicitante]"), null);

        assertThat(r.getStatusCode()).isEqualTo(503);
        assertThat(r.getBody()).contains(Handler.ASSISTENTE_INDISPONIVEL);
    }

    @Test
    void erroDoModeloRetorna503() {
        var modelo = new ModeloFalso(null, 0, new IllegalStateException("indisponível"));
        APIGatewayV2HTTPResponse r = handler(modelo, Duration.ofSeconds(10))
                .handleRequest(evento(corpo("reunião"), "[Solicitante]"), null);

        assertThat(r.getStatusCode()).isEqualTo(503);
    }

    @Test
    void perfilSemPermissaoRetorna403() {
        var modelo = new ModeloFalso("{}", 0, null);
        APIGatewayV2HTTPResponse r = handler(modelo, Duration.ofSeconds(10))
                .handleRequest(evento(corpo("reunião"), "[Setor_Atendente]"), null);

        assertThat(r.getStatusCode()).isEqualTo(403);
        assertThat(modelo.mensagens).isEmpty();
    }
}
