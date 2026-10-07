package br.mp.mpf.sisgares.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.dominio.ArvoreAmbientes;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes do {@link Handler} de catálogo: as validações do serviço são mapeadas pelo
 * {@link MapeadorErros} para HTTP 422, com o código da regra no corpo (Tarefa 23.3).
 *
 * <p><b>Validates: Requirements 7.3, 7.5, 7.8, 7.12</b>
 */
class HandlerCatalogoTest {

    private static final String ADMIN = ServicoCatalogo.GRUPO_ADMINISTRADOR;

    private MemoriaCatalogo memoria;
    private ServicoCatalogo servico;

    @BeforeEach
    void preparar() {
        memoria = new MemoriaCatalogo();
        servico = new ServicoCatalogo(memoria, memoria);
    }

    /** Token fictício (alg "none") aceito pelo {@link ExtratorPrincipal} em modo mock. */
    private static String token(String grupo) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"sub\":\"sub-1\",\"name\":\"Fulano Exemplo\","
                + "\"cognito:groups\":[\"" + grupo + "\"],\"custom:unidade\":\"PRDF\"}")
                .getBytes(StandardCharsets.UTF_8));
        return cabecalho + "." + payload + ".";
    }

    private APIGatewayV2HTTPResponse chamar(String metodo, String caminho, String corpo, String grupo) {
        Handler handler = new Handler(servico,
                new ExtratorPrincipal(true, ConstantesDominio.relogioPadrao()),
                new MapeadorErros(new LogEstruturado(ConstantesDominio.relogioPadrao(), "catalogo")));
        APIGatewayV2HTTPEvent evento = APIGatewayV2HTTPEvent.builder()
                .withRawPath(caminho)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withRequestId("req-1")
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(metodo).build())
                        .build())
                .withHeaders(Map.of("Authorization", "Bearer " + token(grupo)))
                .withBody(corpo)
                .build();
        return handler.handleRequest(evento, null);
    }

    /** Cria um ambiente ativo e devolve o id gerado (sequência começa em 1001). */
    private long criarAmbiente(String corpo) {
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/catalogo/ambientes", corpo, ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(201);
        // id é o único campo numérico "id":"...."
        return Long.parseLong(r.getBody().replaceAll(".*\"id\":\"(\\d+)\".*", "$1"));
    }

    @Test
    void setorComEmailInvalidoResponde422() { // Req. 7.3
        String corpo = "{\"descricao\":\"TI\",\"unidade\":\"PRDF\",\"email\":\"sem-arroba\"}";
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/catalogo/setores", corpo, ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ServicoCatalogo.EMAIL_INVALIDO);
    }

    @Test
    void setorComEmailAlternativoInvalidoResponde422() { // Req. 7.3
        String corpo = "{\"descricao\":\"TI\",\"unidade\":\"PRDF\",\"email\":\"ok@exemplo.gov.br\","
                + "\"emailsAlternativos\":[\"invalido\"]}";
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/catalogo/setores", corpo, ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ServicoCatalogo.EMAIL_INVALIDO);
    }

    @Test
    void ambientePaiProprioOuDescendenteResponde422() { // Req. 7.5
        long raiz = criarAmbiente("{\"descricao\":\"Auditório\",\"unidade\":\"PRDF\"}");

        // Pai = o próprio ambiente
        String proprio = "{\"descricao\":\"Auditório\",\"unidade\":\"PRDF\",\"idPai\":" + raiz + "}";
        APIGatewayV2HTTPResponse r = chamar("PUT", "/api/catalogo/ambientes/" + raiz, proprio, ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ArvoreAmbientes.PAI_PROPRIO_AMBIENTE);
    }

    @Test
    void ambientePaiDeOutraUnidadeResponde422() { // Req. 7.5
        long outra = criarAmbiente("{\"descricao\":\"Sala X\",\"unidade\":\"PRSP\"}");
        String corpo = "{\"descricao\":\"Sala Y\",\"unidade\":\"PRDF\",\"idPai\":" + outra + "}";
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/catalogo/ambientes", corpo, ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ArvoreAmbientes.PAI_OUTRA_UNIDADE);
    }

    @Test
    void imagemComBase64InvalidoResponde422() { // Req. 7.8
        long id = Long.parseLong(chamar("POST", "/api/catalogo/disposicoes",
                "{\"descricao\":\"Auditório\",\"alt\":\"Cadeiras em fileiras\"}", ADMIN)
                .getBody().replaceAll(".*\"id\":\"(\\d+)\".*", "$1"));
        // "@@@" não é base64 válido
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/catalogo/disposicoes/" + id + "/imagem",
                "{\"conteudo\":\"@@@\"}", ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ValidadorImagem.IMAGEM_INVALIDA);
    }

    @Test
    void imagemSvgInseguroResponde422() { // Req. 7.8
        long id = Long.parseLong(chamar("POST", "/api/catalogo/disposicoes",
                "{\"descricao\":\"Mesa única\",\"alt\":\"Mesa central\"}", ADMIN)
                .getBody().replaceAll(".*\"id\":\"(\\d+)\".*", "$1"));
        String svgInseguro = Base64.getEncoder().encodeToString(
                "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8));
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/catalogo/disposicoes/" + id + "/imagem",
                "{\"conteudo\":\"" + svgInseguro + "\"}", ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ValidadorImagem.SVG_INSEGURO);
    }

    @Test
    void recursoLimitadoSemDisponibilidadeResponde422() { // Req. 7.12
        long grupo = Long.parseLong(chamar("POST", "/api/catalogo/grupos",
                "{\"descricao\":\"Equipamento\",\"ordem\":3}", ADMIN)
                .getBody().replaceAll(".*\"id\":\"(\\d+)\".*", "$1"));
        String corpo = "{\"descricao\":\"Projetor\",\"grupoId\":" + grupo
                + ",\"icone\":\"projetor.svg\",\"limitado\":true,\"disponibilidade\":0}";
        APIGatewayV2HTTPResponse r = chamar("POST", "/api/catalogo/recursos", corpo, ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(422);
        assertThat(r.getBody()).contains(ServicoCatalogo.DISPONIBILIDADE_INVALIDA);
    }
}
