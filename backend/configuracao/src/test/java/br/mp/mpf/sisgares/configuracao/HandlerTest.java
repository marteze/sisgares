package br.mp.mpf.sisgares.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.Configuracao.FaixaHoraria;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/** Testes em memória do handler de configuração (Req. 8.1–8.5). */
class HandlerTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2025-03-10T12:00:00Z"), ZoneId.of("America/Fortaleza"));

    /** Armazém em memória. */
    static final class ArmazemMemoria implements ArmazemConfiguracao {
        Vigente atual;

        @Override
        public Optional<Vigente> obter() {
            return Optional.ofNullable(atual);
        }

        @Override
        public void salvar(Configuracao configuracao, String snpUrl) {
            atual = new Vigente(configuracao, snpUrl);
        }
    }

    private final ArmazemMemoria armazem = new ArmazemMemoria();

    private Handler handler(boolean modoLocal) {
        LogEstruturado log = new LogEstruturado(RELOGIO, "teste", new PrintStream(new ByteArrayOutputStream()));
        // Extrator em modo mock: decodifica o JWT fictício montado pelo teste
        return new Handler(armazem, new ExtratorPrincipal(true, RELOGIO), modoLocal, log);
    }

    private static String token(String grupo) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String payload = "{\"sub\":\"u-1\",\"name\":\"Usuario Ficticio\",\"cognito:groups\":[\"" + grupo + "\"]}";
        return b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";
    }

    private static APIGatewayV2HTTPEvent evento(String metodo, String grupo, String corpo) {
        var http = new APIGatewayV2HTTPEvent.RequestContext.Http();
        http.setMethod(metodo);
        var ctx = new APIGatewayV2HTTPEvent.RequestContext();
        ctx.setHttp(http);
        ctx.setRequestId("req-1");
        var evento = new APIGatewayV2HTTPEvent();
        evento.setRequestContext(ctx);
        evento.setRawPath(Handler.ROTA);
        evento.setBody(corpo);
        if (grupo != null) {
            evento.setHeaders(Map.of("Authorization", "Bearer " + token(grupo)));
        }
        return evento;
    }

    private static String corpo(int antecedencia, String min, String max, String snpUrl) {
        return "{\"antecedenciaMinutos\":" + antecedencia
                + ",\"faixaGlobal\":{\"minimo\":\"" + min + "\",\"maximo\":\"" + max + "\"}"
                + ",\"faixasPorUnidade\":{\"PR/CE\":{\"minimo\":\"08:00\",\"maximo\":\"18:00\"}}"
                + (snpUrl == null ? "" : ",\"snpUrl\":\"" + snpUrl + "\"") + "}";
    }

    private void cadastrar() {
        armazem.salvar(new Configuracao(60, new FaixaHoraria(LocalTime.of(7, 0), LocalTime.of(20, 0)), Map.of()),
                "https://snp.exemplo.gov.br");
    }

    @Test
    void getSemConfiguracaoResponde404() {
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(evento("GET", "Solicitante", null), null);
        assertThat(r.getStatusCode()).isEqualTo(404);
        assertThat(r.getBody()).contains("Configuração global ainda não cadastrada");
    }

    @Test
    void getEscondeSnpUrlDeNaoAdministrador() {
        cadastrar();
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(evento("GET", "Solicitante", null), null);
        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"antecedenciaMinutos\":60").doesNotContain("snpUrl");
    }

    @Test
    void getMostraSnpUrlAoAdministrador() {
        cadastrar();
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(evento("GET", "Administrador", null), null);
        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"snpUrl\":\"https://snp.exemplo.gov.br\"");
    }

    @Test
    void semTokenResponde401() {
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(evento("GET", null, null), null);
        assertThat(r.getStatusCode()).isEqualTo(401);
    }

    @Test
    void putDeNaoAdministradorResponde403() {
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(
                evento("PUT", "Solicitante", corpo(60, "07:00", "20:00", null)), null);
        assertThat(r.getStatusCode()).isEqualTo(403);
        assertThat(armazem.atual).isNull();
    }

    @Test
    void putValidoSalvaConfiguracao() {
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(
                evento("PUT", "Administrador", corpo(120, "07:00", "20:00", "https://snp.exemplo.gov.br/api")), null);
        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(armazem.atual.configuracao().antecedenciaMinutos()).isEqualTo(120);
        assertThat(armazem.atual.configuracao().faixaAplicavel("PR/CE").minimo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(armazem.atual.snpUrl()).isEqualTo("https://snp.exemplo.gov.br/api");
    }

    @Test
    void putInvalidoAcumulaViolacoesEm422() {
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(
                evento("PUT", "Administrador", corpo(10081, "20:00", "07:00", "http://snp.exemplo.gov.br")), null);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ValidadorConfiguracao.CONFIG_ANTECEDENCIA_INVALIDA,
                ValidadorConfiguracao.CONFIG_FAIXA_INVALIDA, ValidadorConfiguracao.CONFIG_SNP_URL_INVALIDA);
        assertThat(armazem.atual).isNull();
    }

    @Test
    void faixaComMinimoIgualAoMaximoResponde422() {
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(
                evento("PUT", "Administrador", corpo(60, "08:00", "08:00", null)), null);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains("faixaGlobal");
    }

    @Test
    void httpAceitoApenasNoModoLocal() {
        APIGatewayV2HTTPResponse r = handler(true).handleRequest(
                evento("PUT", "Administrador", corpo(60, "07:00", "20:00", "http://localhost:4010")), null);
        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(armazem.atual.snpUrl()).isEqualTo("http://localhost:4010");
    }

    @Test
    void campoNaoPermitidoResponde422() {
        APIGatewayV2HTTPResponse r = handler(false).handleRequest(evento("PUT", "Administrador",
                "{\"antecedenciaMinutos\":60,\"faixaGlobal\":{\"minimo\":\"07:00\",\"maximo\":\"20:00\"},\"extra\":1}"),
                null);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains("CAMPO_NAO_PERMITIDO");
    }

    /**
     * Antecedência válida apenas no intervalo [0, 10080].
     *
     * <p><b>Validates: Requirements 8.1</b>
     */
    @Property(tries = 200)
    void antecedenciaAceitaSomenteEntre0e10080(@ForAll @IntRange(min = -20000, max = 20000) int antecedencia) {
        Dtos.EntradaConfiguracao entrada = new Dtos.EntradaConfiguracao(antecedencia,
                new Dtos.Faixa("07:00", "20:00"), null, null);
        boolean valida = new ValidadorConfiguracao(false).validar(entrada).violacoes().isEmpty();
        assertThat(valida).isEqualTo(antecedencia >= 0 && antecedencia <= 10080);
    }

    /**
     * Faixa aceita se e somente se mínimo &lt; máximo.
     *
     * <p><b>Validates: Requirements 8.3</b>
     */
    @Property(tries = 200)
    void faixaAceitaSomenteComMinimoAnteriorAoMaximo(@ForAll @IntRange(min = 0, max = 1439) int min,
                                                     @ForAll @IntRange(min = 0, max = 1439) int max) {
        String hMin = LocalTime.MIN.plusMinutes(min).toString();
        String hMax = LocalTime.MIN.plusMinutes(max).toString();
        Dtos.EntradaConfiguracao entrada = new Dtos.EntradaConfiguracao(60, new Dtos.Faixa("08:00", "18:00"),
                Map.of("PR/CE", new Dtos.Faixa(hMin, hMax)), null);
        boolean valida = new ValidadorConfiguracao(false).validar(entrada).violacoes().isEmpty();
        assertThat(valida).isEqualTo(min < max);
    }
}
