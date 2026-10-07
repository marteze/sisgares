package br.mp.mpf.sisgares.paineis;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.CalculadoraGrade;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Roteamento do {@link Handler} de painéis com token fictício (modo mock) e fontes de dados em
 * memória, sem AWS (tarefa 11.3).
 *
 * <p>Cobre as duas rotas ({@code /api/paineis/solicitante} e {@code /api/paineis/atendente}), os
 * códigos de rota/método (404/405), a autenticação (401), a visibilidade do {@code reservaId} ao
 * dono e a ocultação de Dados_Pessoais aos demais (Req. 6.17), além dos cards do Atendente.
 *
 * <p><b>Validates: Requirements 16.4, 16.5, 22.6</b>
 */
class HandlerTest {

    static final String UNIDADE = "PR/CE";
    private static final LocalDate DIA = LocalDate.of(2030, 3, 4); // segunda-feira
    private static final String DONO = "sub-dono";
    private static final long SETOR = 7;

    /** Relógio uma semana antes do DIA: a data consultada está no futuro (há antecedência). */
    private static final Clock RELOGIO = Clock.fixed(
            DIA.minusDays(7).atStartOfDay(ConstantesDominio.ZONA).toInstant(), ConstantesDominio.ZONA);

    private Handler handler;

    /** Fonte do Painel do Solicitante: raiz 1 com filho 2; ocupado no filho às 10:00–11:00. */
    private final ServicoPainelSolicitante.FonteDados fonteSolicitante =
            new ServicoPainelSolicitante.FonteDados() {
                private final List<Ambiente> ambientes = List.of(
                        new Ambiente(1, "Auditório", true, null, UNIDADE),
                        new Ambiente(2, "Sala A", true, 1L, UNIDADE));

                @Override
                public Optional<Ambiente> ambiente(long id) {
                    return ambientes.stream().filter(a -> a.id() == id).findFirst();
                }

                @Override
                public List<Ambiente> ambientesDaUnidade(String unidade) {
                    return ambientes;
                }

                @Override
                public List<PeriodoOcupado> ocupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim) {
                    return List.of(new PeriodoOcupado(99, 2, Periodo.de(DIA.atTime(10, 0), DIA.atTime(11, 0))));
                }

                @Override
                public Optional<String> solicitante(long reservaId) {
                    return reservaId == 99 ? Optional.of(DONO) : Optional.empty();
                }

                @Override
                public Configuracao configuracao() {
                    return new Configuracao(0,
                            new Configuracao.FaixaHoraria(LocalTime.of(8, 0), LocalTime.of(18, 0)), Map.of());
                }
            };

    /** Reserva do setor 7 com Período no DIA e um Pedido_SNP. */
    private final Reserva reserva1 = new Reserva(1L, UNIDADE, "sub-1", 10L, null, null, "Reunião", 10,
            List.of(new Periodo(1L, DIA.atTime(9, 0), DIA.atTime(10, 0))),
            List.of(new Solicitacao(100, 2)), false, null, null, 1);

    /** Fonte do Painel do Atendente. */
    private final ServicoPainelAtendente.FonteDados fonteAtendente =
            new ServicoPainelAtendente.FonteDados() {
                @Override
                public List<Reserva> porSetor(long envoId, LocalDateTime de, LocalDateTime ate) {
                    return envoId == SETOR ? List.of(reserva1) : List.of();
                }

                @Override
                public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
                    return UNIDADE.equals(unidade) ? List.of(reserva1) : List.of();
                }

                @Override
                public Optional<String> descricaoAmbiente(long ambienteId) {
                    return ambienteId == 10 ? Optional.of("Auditório") : Optional.empty();
                }

                @Override
                public Optional<String> descricaoRecurso(long recursoId) {
                    return recursoId == 100 ? Optional.of("Projetor") : Optional.empty();
                }

                @Override
                public List<ServicoPainelAtendente.PedidoSnpCard> pedidosSnp(long reservaId) {
                    return reservaId == 1
                            ? List.of(new ServicoPainelAtendente.PedidoSnpCard("SNP-123",
                                    "https://snp.exemplo.gov.br/123"))
                            : List.of();
                }
            };

    @BeforeEach
    void preparar() {
        LogEstruturado log = new LogEstruturado(RELOGIO, "teste",
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        handler = new Handler(
                new ServicoPainelSolicitante(fonteSolicitante, new CalculadoraGrade(RELOGIO)),
                new ServicoPainelAtendente(fonteAtendente),
                new ExtratorPrincipal(true, RELOGIO), log);
    }

    /** JWT fictício não assinado (alg "none") no formato do frontend em modo local. */
    static String token(String sub, String grupo, String setor) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        StringBuilder payload = new StringBuilder("{\"sub\":\"" + sub + "\",\"name\":\"Fulano Exemplo\","
                + "\"cognito:groups\":[\"" + grupo + "\"],\"custom:unidade\":\"" + UNIDADE + "\"");
        if (setor != null) {
            payload.append(",\"custom:setor\":\"").append(setor).append('"');
        }
        payload.append('}');
        String corpo = b64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return cabecalho + "." + corpo + ".";
    }

    static APIGatewayV2HTTPEvent evento(String metodo, String caminho, Map<String, String> query, String token) {
        APIGatewayV2HTTPEvent.RequestContext.Http http = APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                .withMethod(metodo).withPath(caminho).build();
        APIGatewayV2HTTPEvent.RequestContext ctx = APIGatewayV2HTTPEvent.RequestContext.builder()
                .withHttp(http).withRequestId("req-1").build();
        return APIGatewayV2HTTPEvent.builder()
                .withRouteKey(metodo + " " + caminho)
                .withRawPath(caminho)
                .withRequestContext(ctx)
                .withHeaders(token == null ? Map.of() : Map.of("Authorization", "Bearer " + token))
                .withQueryStringParameters(query)
                .build();
    }

    private APIGatewayV2HTTPResponse chamar(String caminho, Map<String, String> query, String token) {
        return handler.handleRequest(evento("GET", caminho, query, token), null);
    }

    private static JsonNode json(APIGatewayV2HTTPResponse r) throws Exception {
        return Json.mapper().readTree(r.getBody());
    }

    private JsonNode celula10h(JsonNode resposta) {
        JsonNode celulas = resposta.get("colunas").get(0).get("celulas");
        for (JsonNode c : celulas) {
            if ("10:00".equals(c.get("inicio").asText())) {
                return c;
            }
        }
        throw new IllegalStateException("célula das 10:00 não encontrada");
    }

    @Test
    void solicitante_dono_veLinkDaReservaDeAmbienteRelacionado() throws Exception {
        Map<String, String> query = Map.of("ambiente", "1", "data", DIA.toString(), "colunas", "1");

        APIGatewayV2HTTPResponse r = chamar(Handler.ROTA_SOLICITANTE, query, token(DONO, "Solicitante", null));

        assertThat(r.getStatusCode()).isEqualTo(200);
        JsonNode c = celula10h(json(r));
        assertThat(c.get("estado").asText()).isEqualTo("OCUPADO");
        assertThat(c.get("reservaId").asText()).isEqualTo("99");
    }

    @Test
    void solicitante_outro_veOcupadoSemReservaIdNemDadosPessoais() throws Exception {
        Map<String, String> query = Map.of("ambiente", "1", "data", DIA.toString(), "colunas", "1");

        APIGatewayV2HTTPResponse r = chamar(Handler.ROTA_SOLICITANTE, query,
                token("sub-outro", "Solicitante", null));

        assertThat(r.getStatusCode()).isEqualTo(200);
        JsonNode c = celula10h(json(r));
        assertThat(c.get("estado").asText()).isEqualTo("OCUPADO");
        // reservaId omitido do JSON (Req. 6.17) e nenhum dado pessoal do dono vaza
        assertThat(c.has("reservaId")).isFalse();
        assertThat(r.getBody()).doesNotContain(DONO);
    }

    @Test
    void solicitante_parametrosInvalidos_responde422ComFormatoPadrao() throws Exception {
        Map<String, String> query = Map.of("ambiente", "x", "data", "04/03/2030");

        APIGatewayV2HTTPResponse r = chamar(Handler.ROTA_SOLICITANTE, query, token(DONO, "Solicitante", null));

        assertThat(r.getStatusCode()).isEqualTo(422);
        JsonNode corpo = json(r);
        assertThat(corpo.get("correlationId").asText()).isNotBlank();
        assertThat(corpo.get("erros")).isNotEmpty();
        assertThat(corpo.at("/erros/0/codigo").asText())
                .isEqualTo(ServicoPainelSolicitante.PARAMETRO_INVALIDO);
    }

    @Test
    void atendente_veCardDoSetorComRecursosEPedidosSnp() throws Exception {
        Map<String, String> query = Map.of("data", DIA.toString(), "colunas", "1", "fds", "false");

        APIGatewayV2HTTPResponse r = chamar(Handler.ROTA_ATENDENTE, query,
                token("sub-atend", "Setor_Atendente", "7"));

        assertThat(r.getStatusCode()).isEqualTo(200);
        JsonNode cards = json(r).get("colunas").get(0).get("cards");
        assertThat(cards).hasSize(1);
        JsonNode card = cards.get(0);
        assertThat(card.get("reservaId").asText()).isEqualTo("1");
        assertThat(card.get("ambiente").asText()).isEqualTo("Auditório");
        assertThat(card.at("/recursos/0/descricao").asText()).isEqualTo("Projetor");
        assertThat(card.at("/pedidosSnp/0/numero").asText()).isEqualTo("SNP-123");
        assertThat(card.get("cancelada").asBoolean()).isFalse();
    }

    @Test
    void atendente_solicitante_recebe403() throws Exception {
        Map<String, String> query = Map.of("data", DIA.toString(), "colunas", "1");

        APIGatewayV2HTTPResponse r = chamar(Handler.ROTA_ATENDENTE, query, token(DONO, "Solicitante", null));

        assertThat(r.getStatusCode()).isEqualTo(403);
        assertThat(json(r).at("/erros/0/codigo").asText())
                .isEqualTo(br.mp.mpf.sisgares.comumaws.http.MapeadorErros.ACESSO_NEGADO);
    }

    @Test
    void semToken_responde401EmQualquerRota() {
        Map<String, String> query = Map.of("ambiente", "1", "data", DIA.toString());

        assertThat(chamar(Handler.ROTA_SOLICITANTE, query, null).getStatusCode()).isEqualTo(401);
        assertThat(chamar(Handler.ROTA_ATENDENTE, Map.of("data", DIA.toString()), null).getStatusCode())
                .isEqualTo(401);
    }

    @Test
    void rotaInexistente_responde404_eMetodoNaoGet_responde405() {
        APIGatewayV2HTTPResponse r404 = chamar("/api/paineis/inexistente",
                Map.of(), token(DONO, "Solicitante", null));
        assertThat(r404.getStatusCode()).isEqualTo(404);

        APIGatewayV2HTTPResponse r405 = handler.handleRequest(
                evento("POST", Handler.ROTA_SOLICITANTE, Map.of("ambiente", "1", "data", DIA.toString()),
                        token(DONO, "Solicitante", null)), null);
        assertThat(r405.getStatusCode()).isEqualTo(405);
    }
}
