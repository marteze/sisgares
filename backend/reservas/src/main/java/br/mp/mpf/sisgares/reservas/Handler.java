package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoNaoEncontrado;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.reservas.dto.CancelamentoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.ReservaAlteracaoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.ReservaRequisicao;
import br.mp.mpf.sisgares.reservas.dto.VerificarPeriodoRequisicao;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Lambda das rotas {@code /api/reservas} (HTTP API v2). Referenciada pela ApiStack por este nome.
 *
 * <p>Faz somente o roteamento por método + caminho (usa {@code rawPath}, o que cobre tanto rotas
 * explícitas quanto {@code $default}/proxy), a leitura do corpo e o mapeamento de erros via
 * {@link MapeadorErros}. A lógica fica no {@link ServicoReservas}.
 *
 * <ul>
 *   <li>{@code POST /api/reservas} → 201</li>
 *   <li>{@code GET /api/reservas[?minhas=true|false&de=&ate=]}</li>
 *   <li>{@code GET /api/reservas/{id}}</li>
 *   <li>{@code PUT /api/reservas/{id}}</li>
 *   <li>{@code POST /api/reservas/{id}/cancelamento}</li>
 *   <li>{@code POST /api/reservas/verificar-periodo}</li>
 *   <li>{@code GET /api/reservas/{id}/versoes}</li>
 * </ul>
 */
public final class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String BASE = "/api/reservas";
    private static final Map<String, String> CABECALHOS =
            Map.of("Content-Type", "application/json; charset=utf-8");

    private final ServicoReservas servico;
    private final ExtratorPrincipal extrator;
    private final MapeadorErros mapeadorErros;

    /** Construtor usado pelo runtime da Lambda (dependências reais, criadas no cold start). */
    public Handler() {
        this(Producao.SERVICO, ExtratorPrincipal.doAmbiente(Producao.RELOGIO),
                new MapeadorErros(new LogEstruturado(Producao.RELOGIO, "reservas")));
    }

    /** Construtor com dependências injetadas (testes). */
    public Handler(ServicoReservas servico, ExtratorPrincipal extrator, MapeadorErros mapeadorErros) {
        this.servico = Objects.requireNonNull(servico, "servico");
        this.extrator = Objects.requireNonNull(extrator, "extrator");
        this.mapeadorErros = Objects.requireNonNull(mapeadorErros, "mapeadorErros");
    }

    /** Inicialização preguiçosa das dependências de produção (somente no runtime). */
    private static final class Producao {
        static final Clock RELOGIO = ConstantesDominio.relogioPadrao();
        static final ServicoReservas SERVICO = criar();

        private static ServicoReservas criar() {
            ClienteDynamo dynamo = ClienteDynamo.padrao();
            AdaptadorDynamo adaptador = new AdaptadorDynamo(dynamo.cliente(), dynamo.nomeTabela(), RELOGIO);
            LogEstruturado log = new LogEstruturado(RELOGIO, "reservas");
            // Sem BARRAMENTO (modo local), o publicador nulo só registra o evento em log
            PublicadorEventos publicador = PublicadorEventBridge.doAmbiente(
                    System.getenv(PublicadorEventBridge.VAR_BARRAMENTO), log);
            return new ServicoReservas(adaptador, adaptador, RELOGIO, ServicoReservas.ResolvedorNome.doPrincipal(),
                    publicador, log);
        }
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context contexto) {
        String correlationId = MapeadorErros.correlationId(evento);
        try {
            Principal principal = extrator.extrair(evento);
            return rotear(evento, principal);
        } catch (Exception e) {
            return mapeadorErros.mapear(e, correlationId);
        }
    }

    private APIGatewayV2HTTPResponse rotear(APIGatewayV2HTTPEvent evento, Principal principal)
            throws JsonProcessingException {
        String metodo = metodo(evento);
        String[] partes = segmentos(caminho(evento));

        if (partes.length == 0) {
            return switch (metodo) {
                case "GET" -> ok(200, servico.listar(principal, minhas(evento),
                        data(evento, "de"), data(evento, "ate")));
                case "POST" -> ok(201, servico.criar(corpo(evento, ReservaRequisicao.class), principal));
                default -> throw naoEncontrado();
            };
        }
        if (partes.length == 1 && "verificar-periodo".equals(partes[0])) {
            if (!"POST".equals(metodo)) {
                throw naoEncontrado();
            }
            return ok(200, servico.verificarPeriodo(corpo(evento, VerificarPeriodoRequisicao.class), principal));
        }
        long id = id(partes[0]);
        if (partes.length == 1) {
            return switch (metodo) {
                case "GET" -> ok(200, servico.obter(id, principal));
                case "PUT" -> ok(200, servico.alterar(id, corpo(evento, ReservaAlteracaoRequisicao.class), principal));
                default -> throw naoEncontrado();
            };
        }
        if (partes.length == 2 && "cancelamento".equals(partes[1]) && "POST".equals(metodo)) {
            return ok(200, servico.cancelar(id, corpo(evento, CancelamentoRequisicao.class), principal));
        }
        if (partes.length == 2 && "versoes".equals(partes[1]) && "GET".equals(metodo)) {
            return ok(200, servico.versoes(id, principal));
        }
        throw naoEncontrado();
    }

    // ---------- Extração de dados da requisição ----------

    private static String metodo(APIGatewayV2HTTPEvent evento) {
        var ctx = evento.getRequestContext();
        if (ctx != null && ctx.getHttp() != null && ctx.getHttp().getMethod() != null) {
            return ctx.getHttp().getMethod().toUpperCase();
        }
        // Fallback: routeKey no formato "METODO /caminho"
        String routeKey = evento.getRouteKey();
        return routeKey != null && routeKey.contains(" ") ? routeKey.substring(0, routeKey.indexOf(' ')).toUpperCase() : "";
    }

    private static String caminho(APIGatewayV2HTTPEvent evento) {
        String caminho = evento.getRawPath();
        if (caminho == null && evento.getRequestContext() != null && evento.getRequestContext().getHttp() != null) {
            caminho = evento.getRequestContext().getHttp().getPath();
        }
        return caminho == null ? "" : caminho;
    }

    /** Segmentos após {@code /api/reservas}; 404 para caminhos fora da base. */
    static String[] segmentos(String caminho) {
        String c = caminho.endsWith("/") && caminho.length() > 1 ? caminho.substring(0, caminho.length() - 1) : caminho;
        // Estágio nomeado da HTTP API pode prefixar o caminho (ex.: /prod/api/reservas)
        int base = c.indexOf(BASE);
        if (base < 0) {
            throw naoEncontrado();
        }
        String resto = c.substring(base + BASE.length());
        if (resto.isEmpty()) {
            return new String[0];
        }
        if (resto.charAt(0) != '/') {
            throw naoEncontrado();
        }
        return resto.substring(1).split("/", -1);
    }

    private static long id(String texto) {
        if (texto == null || !texto.matches("\\d{1,18}")) {
            throw naoEncontrado();
        }
        return Long.parseLong(texto);
    }

    private static boolean minhas(APIGatewayV2HTTPEvent evento) {
        String valor = parametro(evento, "minhas");
        return valor == null || !"false".equalsIgnoreCase(valor.trim());
    }

    /** Data ISO-8601 ({@code yyyy-MM-dd}); valor inválido vira 400. */
    private static LocalDate data(APIGatewayV2HTTPEvent evento, String nome) throws JsonProcessingException {
        String valor = parametro(evento, nome);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new ParametroInvalido(nome);
        }
    }

    private static String parametro(APIGatewayV2HTTPEvent evento, String nome) {
        Map<String, String> parametros = evento.getQueryStringParameters();
        return parametros == null ? null : parametros.get(nome);
    }

    private static <T> T corpo(APIGatewayV2HTTPEvent evento, Class<T> tipo) throws JsonProcessingException {
        String texto = textoCorpo(evento);
        // Corpo ausente: o DTO nulo gera 422 no ValidadorEntrada
        return texto == null || texto.isBlank() ? null : Json.ler(texto, tipo);
    }

    private static String textoCorpo(APIGatewayV2HTTPEvent evento) {
        String corpo = evento.getBody();
        // Lombok gera isBase64Encoded() para o campo boolean "isBase64Encoded"
        if (corpo != null && evento.isBase64Encoded()) {
            return new String(Base64.getDecoder().decode(corpo), StandardCharsets.UTF_8);
        }
        return corpo;
    }

    private static ExcecaoNaoEncontrado naoEncontrado() {
        return new ExcecaoNaoEncontrado("Rota não encontrada.");
    }

    private static APIGatewayV2HTTPResponse ok(int status, Object corpo) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(CABECALHOS)
                .withBody(Json.escrever(corpo))
                .withIsBase64Encoded(false)
                .build();
    }

    /** Parâmetro de consulta malformado; como {@link JsonProcessingException}, vira 400 SCHEMA_INVALIDO. */
    static final class ParametroInvalido extends JsonProcessingException {
        ParametroInvalido(String nome) {
            super("Parâmetro inválido: " + nome);
        }
    }
}
