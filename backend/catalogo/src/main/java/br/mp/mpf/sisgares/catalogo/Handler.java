package br.mp.mpf.sisgares.catalogo;

import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.AmbienteRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.DisposicaoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.GrupoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.ImagemRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.RecursoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.SetorRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.VinculoAmbienteRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.VinculoSetorRequisicao;
import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoNaoEncontrado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Violacao;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lambda das rotas {@code /api/catalogo/{proxy+}} (HTTP API v2). Referenciada pela ApiStack por este nome.
 *
 * <p>Somente roteamento, leitura do corpo e mapeamento de erros; a lógica fica no {@link ServicoCatalogo}.
 * Entidades: {@code setores}, {@code ambientes}, {@code disposicoes}, {@code grupos}, {@code recursos}.
 *
 * <ul>
 *   <li>{@code GET /api/catalogo/{entidade}[?todos=true]} (setores e {@code todos=true}: só Admin)</li>
 *   <li>{@code GET /api/catalogo/{entidade}/{id}}</li>
 *   <li>{@code POST /api/catalogo/{entidade}} → 201; {@code PUT /api/catalogo/{entidade}/{id}}</li>
 *   <li>{@code PATCH /api/catalogo/{entidade}/{id}/inativar}</li>
 *   <li>{@code POST /api/catalogo/disposicoes/{id}/imagem} (base64)</li>
 *   <li>{@code GET|POST /api/catalogo/{ambientes|recursos}/{id}/setores}; {@code DELETE .../setores/{setorId}} (EAMB/EREC)</li>
 *   <li>{@code POST /api/catalogo/recursos/{id}/ambientes}; {@code DELETE .../ambientes/{ambienteId}} (VREC)</li>
 * </ul>
 */
public final class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String BASE = "/api/catalogo";
    private static final Map<String, String> CABECALHOS =
            Map.of("Content-Type", "application/json; charset=utf-8");

    private final ServicoCatalogo servico;
    private final ExtratorPrincipal extrator;
    private final MapeadorErros mapeadorErros;

    /** Construtor usado pelo runtime da Lambda (dependências reais, criadas no cold start). */
    public Handler() {
        this(Producao.SERVICO, ExtratorPrincipal.doAmbiente(Producao.RELOGIO),
                new MapeadorErros(new LogEstruturado(Producao.RELOGIO, "catalogo")));
    }

    /** Construtor com dependências injetadas (testes). */
    public Handler(ServicoCatalogo servico, ExtratorPrincipal extrator, MapeadorErros mapeadorErros) {
        this.servico = Objects.requireNonNull(servico, "servico");
        this.extrator = Objects.requireNonNull(extrator, "extrator");
        this.mapeadorErros = Objects.requireNonNull(mapeadorErros, "mapeadorErros");
    }

    /** Inicialização preguiçosa das dependências de produção (somente no runtime). */
    private static final class Producao {
        static final Clock RELOGIO = ConstantesDominio.relogioPadrao();
        static final ServicoCatalogo SERVICO = criar();

        private static ServicoCatalogo criar() {
            ClienteDynamo dynamo = ClienteDynamo.padrao();
            return new ServicoCatalogo(new AdaptadorDynamo(dynamo.cliente(), dynamo.nomeTabela()),
                    ArmazenamentoS3.doAmbiente());
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

    private APIGatewayV2HTTPResponse rotear(APIGatewayV2HTTPEvent evento, Principal p)
            throws JsonProcessingException {
        String metodo = metodo(evento);
        String[] partes = segmentos(caminho(evento));
        if (partes.length == 0) {
            throw naoEncontrado();
        }
        String entidade = partes[0];
        if (partes.length == 1) {
            boolean todos = "true".equalsIgnoreCase(parametro(evento, "todos"));
            return switch (metodo) {
                case "GET" -> ok(200, listar(entidade, p, todos));
                case "POST" -> ok(201, criar(entidade, evento, p));
                default -> throw naoEncontrado();
            };
        }
        long id = id(partes[1]);
        if (partes.length == 2) {
            return switch (metodo) {
                case "GET" -> ok(200, obter(entidade, id, p));
                case "PUT" -> ok(200, alterar(entidade, id, evento, p));
                default -> throw naoEncontrado();
            };
        }
        String acao = partes[2];
        if (partes.length == 3) {
            if ("inativar".equals(acao) && "PATCH".equals(metodo)) {
                return ok(200, inativar(entidade, id, p));
            }
            if ("disposicoes".equals(entidade) && "imagem".equals(acao) && "POST".equals(metodo)) {
                return ok(200, servico.enviarImagem(id, imagem(evento), p));
            }
            if ("setores".equals(acao) && "GET".equals(metodo)) {
                return switch (entidade) {
                    case "ambientes" -> ok(200, servico.listarSetoresDoAmbiente(p, id));
                    case "recursos" -> ok(200, servico.listarSetoresDoRecurso(p, id));
                    default -> throw naoEncontrado();
                };
            }
            if ("setores".equals(acao) && "POST".equals(metodo)) {
                VinculoSetorRequisicao req = corpo(evento, VinculoSetorRequisicao.class);
                return switch (entidade) {
                    case "ambientes" -> ok(201, servico.vincularSetorAmbiente(id, req, p));
                    case "recursos" -> ok(201, servico.vincularSetorRecurso(id, req, p));
                    default -> throw naoEncontrado();
                };
            }
            if ("recursos".equals(entidade) && "ambientes".equals(acao) && "POST".equals(metodo)) {
                return ok(201, servico.vincularRecursoAmbiente(id,
                        corpo(evento, VinculoAmbienteRequisicao.class), p));
            }
            throw naoEncontrado();
        }
        if (partes.length == 4 && "DELETE".equals(metodo)) {
            long alvo = id(partes[3]);
            if ("setores".equals(acao) && "ambientes".equals(entidade)) {
                servico.desvincularSetorAmbiente(id, alvo, p);
                return semConteudo();
            }
            if ("setores".equals(acao) && "recursos".equals(entidade)) {
                servico.desvincularSetorRecurso(id, alvo, p);
                return semConteudo();
            }
            if ("ambientes".equals(acao) && "recursos".equals(entidade)) {
                return ok(200, servico.desvincularRecursoAmbiente(id, alvo, p));
            }
        }
        throw naoEncontrado();
    }

    // ---------- Despacho por entidade ----------

    private Object listar(String entidade, Principal p, boolean todos) {
        return switch (entidade) {
            case "ambientes" -> servico.listarAmbientes(p, todos);
            case "recursos" -> servico.listarRecursos(p, todos);
            case "disposicoes" -> servico.listarDisposicoes(p, todos);
            case "grupos" -> servico.listarGrupos(p, todos);
            case "setores" -> servico.listarSetores(p, todos);
            default -> throw naoEncontrado();
        };
    }

    private Object obter(String entidade, long id, Principal p) {
        return switch (entidade) {
            case "ambientes" -> servico.obterAmbiente(p, id);
            case "recursos" -> servico.obterRecurso(p, id);
            case "disposicoes" -> servico.obterDisposicao(p, id);
            case "grupos" -> servico.obterGrupo(p, id);
            case "setores" -> servico.obterSetor(p, id);
            default -> throw naoEncontrado();
        };
    }

    private Object criar(String entidade, APIGatewayV2HTTPEvent e, Principal p) throws JsonProcessingException {
        return switch (entidade) {
            case "ambientes" -> servico.criarAmbiente(corpo(e, AmbienteRequisicao.class), p);
            case "recursos" -> servico.criarRecurso(corpo(e, RecursoRequisicao.class), p);
            case "disposicoes" -> servico.criarDisposicao(corpo(e, DisposicaoRequisicao.class), p);
            case "grupos" -> servico.criarGrupo(corpo(e, GrupoRequisicao.class), p);
            case "setores" -> servico.criarSetor(corpo(e, SetorRequisicao.class), p);
            default -> throw naoEncontrado();
        };
    }

    private Object alterar(String entidade, long id, APIGatewayV2HTTPEvent e, Principal p)
            throws JsonProcessingException {
        return switch (entidade) {
            case "ambientes" -> servico.alterarAmbiente(id, corpo(e, AmbienteRequisicao.class), p);
            case "recursos" -> servico.alterarRecurso(id, corpo(e, RecursoRequisicao.class), p);
            case "disposicoes" -> servico.alterarDisposicao(id, corpo(e, DisposicaoRequisicao.class), p);
            case "grupos" -> servico.alterarGrupo(id, corpo(e, GrupoRequisicao.class), p);
            case "setores" -> servico.alterarSetor(id, corpo(e, SetorRequisicao.class), p);
            default -> throw naoEncontrado();
        };
    }

    private Object inativar(String entidade, long id, Principal p) {
        return switch (entidade) {
            case "ambientes" -> servico.inativarAmbiente(id, p);
            case "recursos" -> servico.inativarRecurso(id, p);
            case "disposicoes" -> servico.inativarDisposicao(id, p);
            case "grupos" -> servico.inativarGrupo(id, p);
            case "setores" -> servico.inativarSetor(id, p);
            default -> throw naoEncontrado();
        };
    }

    // ---------- Extração de dados da requisição ----------

    private static String metodo(APIGatewayV2HTTPEvent evento) {
        var ctx = evento.getRequestContext();
        if (ctx != null && ctx.getHttp() != null && ctx.getHttp().getMethod() != null) {
            return ctx.getHttp().getMethod().toUpperCase();
        }
        // Fallback: routeKey no formato "METODO /caminho"
        String routeKey = evento.getRouteKey();
        return routeKey != null && routeKey.contains(" ")
                ? routeKey.substring(0, routeKey.indexOf(' ')).toUpperCase() : "";
    }

    private static String caminho(APIGatewayV2HTTPEvent evento) {
        String caminho = evento.getRawPath();
        if (caminho == null && evento.getRequestContext() != null && evento.getRequestContext().getHttp() != null) {
            caminho = evento.getRequestContext().getHttp().getPath();
        }
        return caminho == null ? "" : caminho;
    }

    /** Segmentos após {@code /api/catalogo}; 404 para caminhos fora da base. */
    static String[] segmentos(String caminho) {
        String c = caminho.endsWith("/") && caminho.length() > 1 ? caminho.substring(0, caminho.length() - 1) : caminho;
        // Estágio nomeado da HTTP API pode prefixar o caminho (ex.: /prod/api/catalogo)
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

    private static String parametro(APIGatewayV2HTTPEvent evento, String nome) {
        Map<String, String> parametros = evento.getQueryStringParameters();
        return parametros == null ? null : parametros.get(nome);
    }

    private static <T> T corpo(APIGatewayV2HTTPEvent evento, Class<T> tipo) throws JsonProcessingException {
        String texto = textoCorpo(evento);
        // Corpo ausente: o serviço responde 422 CAMPO_OBRIGATORIO
        return texto == null || texto.isBlank() ? null : Json.ler(texto, tipo);
    }

    private static String textoCorpo(APIGatewayV2HTTPEvent evento) {
        String corpo = evento.getBody();
        if (corpo != null && evento.getIsBase64Encoded()) {
            return new String(Base64.getDecoder().decode(corpo), StandardCharsets.UTF_8);
        }
        return corpo;
    }

    /**
     * Bytes da imagem: JSON {@code {"conteudo":"<base64>"}} ou corpo com a própria string base64
     * (aceita também data URL {@code data:image/...;base64,}). Base64 inválido → 422.
     */
    static byte[] imagem(APIGatewayV2HTTPEvent evento) throws JsonProcessingException {
        String texto = textoCorpo(evento);
        if (texto == null || texto.isBlank()) {
            return new byte[0];
        }
        String base64 = texto.trim();
        if (base64.startsWith("{")) {
            ImagemRequisicao req = Json.ler(base64, ImagemRequisicao.class);
            base64 = req.conteudo() == null ? "" : req.conteudo().trim();
        }
        int virgula = base64.indexOf(',');
        if (base64.startsWith("data:") && virgula > 0) {
            base64 = base64.substring(virgula + 1);
        }
        // Limite antes de decodificar (base64 ≈ 4/3 do tamanho) para não alocar corpos enormes
        if (base64.length() > (ValidadorImagem.LIMITE_BYTES / 3 + 1) * 4 + 16) {
            throw new ExcecaoValidacao(List.of(new Violacao(ValidadorImagem.IMAGEM_GRANDE,
                    "A imagem excede 2 MB. Reduza o arquivo e tente novamente.", "imagem")));
        }
        try {
            return Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ExcecaoValidacao(List.of(new Violacao(ValidadorImagem.IMAGEM_INVALIDA,
                    "Conteúdo base64 inválido. Envie a imagem codificada em base64.", "imagem")));
        }
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

    private static APIGatewayV2HTTPResponse semConteudo() {
        return APIGatewayV2HTTPResponse.builder().withStatusCode(204).withIsBase64Encoded(false).build();
    }
}
