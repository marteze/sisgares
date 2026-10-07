package br.mp.mpf.sisgares.configuracao;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.comumaws.http.RespostaErro;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioConfiguracao;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lambda de configuração (referenciada pela ApiStack como {@code br.mp.mpf.sisgares.configuracao.Handler}).
 *
 * <ul>
 *   <li>{@code GET /api/configuracao}: qualquer usuário autenticado; {@code snpUrl} só para o
 *       Administrador. Sem configuração global, 404.</li>
 *   <li>{@code PUT /api/configuracao}: exclusivo do Administrador; validação com violações
 *       acumuladas em 422 (Req. 8.1–8.5). O cache de 60 s do repositório garante o Req. 8.6.</li>
 * </ul>
 */
public final class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String ROTA = "/api/configuracao";
    static final String GRUPO_ADMINISTRADOR = "Administrador";
    static final String ROTA_INEXISTENTE = "ROTA_INEXISTENTE";

    private static final Map<String, String> CABECALHOS =
            Map.of("Content-Type", "application/json; charset=utf-8");

    private final ArmazemConfiguracao armazem;
    private final ExtratorPrincipal extrator;
    private final ValidadorConfiguracao validador;
    private final LogEstruturado log;
    private final MapeadorErros erros;

    /** Construtor usado pela Lambda: dependências criadas uma vez por contêiner. */
    public Handler() {
        this(Clock.system(ConstantesDominio.ZONA), ClienteDynamo.padrao(),
                ExtratorPrincipal.MODO_MOCK.equalsIgnoreCase(System.getenv(ExtratorPrincipal.VARIAVEL_MODO)));
    }

    private Handler(Clock relogio, ClienteDynamo dynamo, boolean modoLocal) {
        this(ArmazemConfiguracao.de(new RepositorioConfiguracao(dynamo.cliente(), dynamo.nomeTabela(), relogio)),
                new ExtratorPrincipal(modoLocal, relogio), modoLocal, new LogEstruturado(relogio, "configuracao"));
    }

    /** Construtor para testes, sem AWS. */
    Handler(ArmazemConfiguracao armazem, ExtratorPrincipal extrator, boolean modoLocal, LogEstruturado log) {
        this.armazem = Objects.requireNonNull(armazem, "armazem");
        this.extrator = Objects.requireNonNull(extrator, "extrator");
        this.validador = new ValidadorConfiguracao(modoLocal);
        this.log = Objects.requireNonNull(log, "log");
        this.erros = new MapeadorErros(log);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context contexto) {
        String correlationId = MapeadorErros.correlationId(evento);
        try {
            // Autenticação antes do roteamento: sem token, 401
            Principal usuario = extrator.extrair(evento);
            if (!ROTA.equals(rota(evento))) {
                return erro(404, ROTA_INEXISTENTE, "Rota não encontrada. Verifique o endereço da API.", correlationId);
            }
            return switch (metodo(evento)) {
                case "GET" -> obter(usuario, correlationId);
                case "PUT" -> salvar(evento, usuario, correlationId);
                default -> erro(405, ROTA_INEXISTENTE, "Método não suportado. Use GET ou PUT.", correlationId);
            };
        } catch (JsonProcessingException e) {
            return erros.mapear(e, correlationId);
        } catch (RuntimeException e) {
            return erros.mapear(e, correlationId);
        }
    }

    private APIGatewayV2HTTPResponse obter(Principal usuario, String correlationId) {
        Optional<ArmazemConfiguracao.Vigente> vigente = armazem.obter();
        if (vigente.isEmpty()) {
            return erro(404, MapeadorErros.NAO_ENCONTRADO,
                    "Configuração global ainda não cadastrada. Peça ao Administrador para salvá-la.",
                    correlationId);
        }
        // snpUrl visível apenas ao Administrador
        String snpUrl = admin(usuario) ? vigente.get().snpUrl() : null;
        return ok(Dtos.SaidaConfiguracao.de(vigente.get().configuracao(), snpUrl));
    }

    private APIGatewayV2HTTPResponse salvar(APIGatewayV2HTTPEvent evento, Principal usuario, String correlationId)
            throws JsonProcessingException {
        // Autorização no backend: PUT exclusivo do Administrador
        if (!admin(usuario)) {
            throw new ExcecaoAcessoNegado("Somente o Administrador altera a configuração.");
        }
        String corpo = evento.getBody();
        if (corpo == null || corpo.isBlank()) {
            return MapeadorErros.requisicaoInvalida(correlationId);
        }
        Dtos.EntradaConfiguracao entrada = Json.ler(corpo, Dtos.EntradaConfiguracao.class);
        ValidadorConfiguracao.Resultado resultado = validador.validar(entrada);
        if (!resultado.violacoes().isEmpty()) {
            throw new ExcecaoValidacao(resultado.violacoes());
        }
        armazem.salvar(resultado.configuracao(), resultado.snpUrl());
        // Log sem dados pessoais
        log.info(correlationId, "Configuração atualizada", Map.of(
                "antecedenciaMinutos", resultado.configuracao().antecedenciaMinutos(),
                "unidadesComFaixa", resultado.configuracao().faixasPorUnidade().size()));
        return ok(Dtos.SaidaConfiguracao.de(resultado.configuracao(), resultado.snpUrl()));
    }

    private static boolean admin(Principal usuario) {
        return usuario.pertenceA(GRUPO_ADMINISTRADOR);
    }

    private static APIGatewayV2HTTPResponse ok(Object corpo) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(CABECALHOS)
                .withBody(Json.escrever(corpo))
                .withIsBase64Encoded(false)
                .build();
    }

    private static String metodo(APIGatewayV2HTTPEvent evento) {
        var ctx = evento.getRequestContext();
        if (ctx == null || ctx.getHttp() == null || ctx.getHttp().getMethod() == null) {
            return "";
        }
        return ctx.getHttp().getMethod().toUpperCase();
    }

    /** Caminho sem barra final. */
    private static String rota(APIGatewayV2HTTPEvent evento) {
        String caminho = evento.getRawPath();
        if (caminho == null) {
            return "";
        }
        return caminho.length() > 1 && caminho.endsWith("/") ? caminho.substring(0, caminho.length() - 1) : caminho;
    }

    private static APIGatewayV2HTTPResponse erro(int status, String codigo, String mensagem, String correlationId) {
        return MapeadorErros.resposta(status, RespostaErro.unico(codigo, mensagem, correlationId));
    }
}
