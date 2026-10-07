package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.dominio.Ambiente;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roteamento do {@link Handler} com token fictício (modo mock) e portas em memória.
 *
 * <p>**Validates: Requirements 9.14, 12.4, 12.6**
 */
class HandlerTest {

    static final String CORPO = """
            {"ambienteId":"1","finalidade":"Reunião","participantes":5,
             "periodos":[{"inicio":"2030-01-12T09:00","termino":"2030-01-12T11:00"}],"recursos":[]}""";

    MemoriaReservas memoria;
    Handler handler;

    @BeforeEach
    void preparar() {
        memoria = new MemoriaReservas();
        memoria.ambientes.add(new Ambiente(1L, "Auditório", true, null, ServicoReservasTest.UNIDADE));
        ServicoReservas servico = new ServicoReservas(memoria, memoria, ServicoReservasTest.RELOGIO,
                ServicoReservas.ResolvedorNome.doPrincipal());
        LogEstruturado log = new LogEstruturado(ServicoReservasTest.RELOGIO, "teste",
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        handler = new Handler(servico, new ExtratorPrincipal(true, ServicoReservasTest.RELOGIO),
                new MapeadorErros(log));
    }

    /** JWT fictício não assinado, no formato do frontend em modo local. */
    static String token(String sub) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"sub\":\"" + sub + "\",\"name\":\"Fulano Exemplo\","
                + "\"cognito:groups\":[\"Solicitante\"],\"custom:unidade\":\"PRDF\"}")
                .getBytes(StandardCharsets.UTF_8));
        return cabecalho + "." + payload + ".";
    }

    static APIGatewayV2HTTPEvent evento(String metodo, String caminho, String corpo, String sub) {
        APIGatewayV2HTTPEvent.RequestContext.Http http = APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                .withMethod(metodo).withPath(caminho).build();
        APIGatewayV2HTTPEvent.RequestContext ctx = APIGatewayV2HTTPEvent.RequestContext.builder()
                .withHttp(http).withRequestId("req-1").build();
        return APIGatewayV2HTTPEvent.builder()
                .withRouteKey(metodo + " " + caminho)
                .withRawPath(caminho)
                .withRequestContext(ctx)
                .withHeaders(sub == null ? Map.of() : Map.of("Authorization", "Bearer " + token(sub)))
                .withBody(corpo)
                .build();
    }

    private APIGatewayV2HTTPResponse chamar(String metodo, String caminho, String corpo) {
        return handler.handleRequest(evento(metodo, caminho, corpo, "sub-1"), null);
    }

    private static JsonNode json(APIGatewayV2HTTPResponse r) throws Exception {
        return Json.mapper().readTree(r.getBody());
    }

    @Test
    void post_responde201ComNomeDoSolicitante() throws Exception {
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/reservas", CORPO);

        assertThat(r.getStatusCode()).isEqualTo(201);
        assertThat(json(r).get("solicitanteNome").asText()).isEqualTo("Fulano Exemplo");
        assertThat(json(r).get("status").asText()).isEqualTo("PREVISTA");
    }

    @Test
    void put_aceitaCamposSomenteLeituraReenviadosPeloFrontend() throws Exception {
        JsonNode criada = json(chamar("POST", "/api/reservas", CORPO));
        String id = criada.get("id").asText();
        // Reenvia a resposta do GET (com id, status, solicitanteNome, ultimaAlteracao e pedidosSnp)
        String corpoPut = criada.toString().replace("\"Reunião\"", "\"Reunião alterada\"");

        APIGatewayV2HTTPResponse r = chamar("PUT", "/api/reservas/" + id, corpoPut);

        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(json(r).get("finalidade").asText()).isEqualTo("Reunião alterada");
    }

    @Test
    void put_campoDesconhecido_422() throws Exception {
        String id = json(chamar("POST", "/api/reservas", CORPO)).get("id").asText();

        APIGatewayV2HTTPResponse r = chamar("PUT", "/api/reservas/" + id,
                CORPO.replace("\"recursos\":[]", "\"recursos\":[],\"solicitante\":\"x\""));

        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(json(r).at("/erros/0/codigo").asText()).isEqualTo(MapeadorErros.CAMPO_NAO_PERMITIDO);
    }

    @Test
    void cancelamento_RN12_semConfirmado_422() throws Exception {
        String id = json(chamar("POST", "/api/reservas", CORPO)).get("id").asText();

        APIGatewayV2HTTPResponse r = chamar("POST", "/api/reservas/" + id + "/cancelamento", "{}");

        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(json(r).at("/erros/0/codigo").asText()).isEqualTo("RN12_CONFIRMACAO_OBRIGATORIA");
    }

    @Test
    void listaVersoesEVerificarPeriodo_respondem200() throws Exception {
        String id = json(chamar("POST", "/api/reservas", CORPO)).get("id").asText();

        assertThat(json(chamar("GET", "/api/reservas", null)).size()).isEqualTo(1);
        assertThat(json(chamar("GET", "/api/reservas/" + id + "/versoes", null)).size()).isEqualTo(1);
        APIGatewayV2HTTPResponse v = chamar("POST", "/api/reservas/verificar-periodo",
                "{\"ambienteId\":\"1\",\"periodo\":{\"inicio\":\"2030-01-12T11:10\",\"termino\":\"2030-01-12T12:00\"}}");
        assertThat(v.getStatusCode()).isEqualTo(200);
        assertThat(json(v).get("conflitos").size()).isEqualTo(1);
    }

    @Test
    void rotaInexistenteOuIdInvalido_404_eSemToken_401() {
        assertThat(chamar("DELETE", "/api/reservas/1", null).getStatusCode()).isEqualTo(404);
        assertThat(chamar("GET", "/api/reservas/abc", null).getStatusCode()).isEqualTo(404);
        assertThat(chamar("GET", "/api/reservas/999", null).getStatusCode()).isEqualTo(404);
        assertThat(handler.handleRequest(evento("GET", "/api/reservas", null, null), null).getStatusCode())
                .isEqualTo(401);
    }
}
