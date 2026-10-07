package br.mp.mpf.sisgares.assistente;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoNaoEncontrado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.comumaws.http.RespostaErro;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Violacao;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Lambda do Assistente_Reserva: {@code POST /api/assistente/propostas} com corpo {@code {descricao}}.
 *
 * <ul>
 *   <li>Descrição obrigatória, até 1000 caracteres; caso contrário 422 (Req. 18.1).</li>
 *   <li>Prompt só com descrição, data atual e catálogos (Req. 18.2) — ver {@link MontadorPrompt}.</li>
 *   <li>Saída saneada por {@link SaneadorProposta} com {@code camposNaoPreenchidos} (Req. 18.4).</li>
 *   <li>Apenas devolve a proposta; não grava Reservas (Req. 18.6).</li>
 *   <li>Modelo indisponível ou acima de 10 s → 503 {@code ASSISTENTE_INDISPONIVEL} (Req. 18.8).</li>
 * </ul>
 */
public final class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String ROTA = "/api/assistente/propostas";
    static final int DESCRICAO_MAX = 1000;
    static final Duration TIMEOUT_PADRAO = Duration.ofSeconds(10);
    static final String GRUPO_SOLICITANTE = "Solicitante";
    static final String GRUPO_ADMINISTRADOR = "Administrador";
    public static final String ASSISTENTE_INDISPONIVEL = "ASSISTENTE_INDISPONIVEL";
    public static final String DESCRICAO_INVALIDA = "DESCRICAO_INVALIDA";

    private static final Map<String, String> CABECALHOS =
            Map.of("Content-Type", "application/json; charset=utf-8");

    /** Threads daemon para a chamada ao modelo (não impedem o congelamento da Lambda). */
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "assistente-modelo");
        t.setDaemon(true);
        return t;
    });

    /** Corpo da requisição; campos extras são rejeitados pelo {@link Json} (whitelist). */
    public record RequisicaoProposta(String descricao) {
    }

    private final ModeloLinguagem modelo;
    private final FonteCatalogo catalogo;
    private final ExtratorPrincipal extrator;
    private final LogEstruturado log;
    private final MapeadorErros mapeadorErros;
    private final Clock relogio;
    private final Duration timeout;

    /** Construtor usado pelo runtime da Lambda. */
    public Handler() {
        this(Producao.MODELO, Producao.CATALOGO, ExtratorPrincipal.doAmbiente(Producao.RELOGIO),
                new LogEstruturado(Producao.RELOGIO, "assistente"), Producao.RELOGIO, TIMEOUT_PADRAO);
    }

    /** Construtor com dependências injetadas (testes). */
    public Handler(ModeloLinguagem modelo, FonteCatalogo catalogo, ExtratorPrincipal extrator,
                   LogEstruturado log, Clock relogio, Duration timeout) {
        this.modelo = Objects.requireNonNull(modelo, "modelo");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.extrator = Objects.requireNonNull(extrator, "extrator");
        this.log = Objects.requireNonNull(log, "log");
        this.mapeadorErros = new MapeadorErros(log);
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** Dependências de produção criadas no cold start. */
    private static final class Producao {
        static final Clock RELOGIO = ConstantesDominio.relogioPadrao();
        static final ModeloLinguagem MODELO = ModeloBedrock.doAmbiente(System.getenv(), TIMEOUT_PADRAO);
        static final FonteCatalogo CATALOGO = criarCatalogo();

        private static FonteCatalogo criarCatalogo() {
            ClienteDynamo dynamo = ClienteDynamo.padrao();
            return FonteCatalogo.de(new RepositorioCatalogo(dynamo.cliente(), dynamo.nomeTabela()));
        }
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context contexto) {
        String correlationId = MapeadorErros.correlationId(evento);
        try {
            Principal principal = extrator.extrair(evento);
            validarRota(evento);
            if (!principal.pertenceA(GRUPO_SOLICITANTE) && !principal.pertenceA(GRUPO_ADMINISTRADOR)) {
                throw new ExcecaoAcessoNegado("Assistente restrito a Solicitante e Administrador.");
            }
            String descricao = descricao(evento);
            Catalogo cat = catalogo.carregar();
            // Prompt sem dados do Principal (nome, e-mail, sub): Req. 18.2
            String mensagem = MontadorPrompt.mensagem(descricao, LocalDate.now(relogio), cat);
            String saida;
            try {
                saida = chamarModelo(mensagem);
            } catch (Exception e) {
                // Sem descrição nem saída no log (podem conter dados pessoais)
                log.aviso(correlationId, "Assistente indisponível: " + e.getClass().getSimpleName());
                return MapeadorErros.resposta(503, RespostaErro.unico(ASSISTENTE_INDISPONIVEL,
                        "O assistente está indisponível no momento. Preencha o formulário manualmente.",
                        correlationId));
            }
            return ok(SaneadorProposta.sanear(saida, cat));
        } catch (Exception e) {
            return mapeadorErros.mapear(e, correlationId);
        }
    }

    /** Invoca o modelo com limite de tempo; estouro gera {@link java.util.concurrent.TimeoutException}. */
    private String chamarModelo(String mensagem) throws Exception {
        Future<String> futuro = EXECUTOR.submit(() -> modelo.gerar(MontadorPrompt.INSTRUCOES, mensagem));
        try {
            return futuro.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            futuro.cancel(true);
        }
    }

    private static void validarRota(APIGatewayV2HTTPEvent evento) {
        String metodo = "";
        String caminho = evento.getRawPath();
        var ctx = evento.getRequestContext();
        if (ctx != null && ctx.getHttp() != null) {
            metodo = ctx.getHttp().getMethod() == null ? "" : ctx.getHttp().getMethod();
            if (caminho == null) {
                caminho = ctx.getHttp().getPath();
            }
        }
        if (metodo.isEmpty() && evento.getRouteKey() != null && evento.getRouteKey().contains(" ")) {
            metodo = evento.getRouteKey().substring(0, evento.getRouteKey().indexOf(' '));
        }
        String c = caminho == null ? "" : caminho.replaceAll("/+$", "");
        // Estágio nomeado da HTTP API pode prefixar o caminho
        if (!"POST".equalsIgnoreCase(metodo) || !c.endsWith(ROTA)) {
            throw new ExcecaoNaoEncontrado("Rota não encontrada.");
        }
    }

    /** Lê e valida a descrição (obrigatória, até 1000 caracteres). */
    private static String descricao(APIGatewayV2HTTPEvent evento) throws Exception {
        String corpo = evento.getBody();
        if (corpo != null && evento.getIsBase64Encoded()) {
            corpo = new String(Base64.getDecoder().decode(corpo), StandardCharsets.UTF_8);
        }
        RequisicaoProposta req = corpo == null || corpo.isBlank() ? null : Json.ler(corpo, RequisicaoProposta.class);
        String descricao = req == null || req.descricao() == null ? "" : req.descricao().strip();
        if (descricao.isEmpty()) {
            throw invalida("Informe a descrição do evento.");
        }
        if (descricao.codePointCount(0, descricao.length()) > DESCRICAO_MAX) {
            throw invalida("A descrição excede 1000 caracteres. Resuma o texto e tente novamente.");
        }
        return descricao;
    }

    private static ExcecaoValidacao invalida(String mensagem) {
        return new ExcecaoValidacao(List.of(new Violacao(DESCRICAO_INVALIDA, mensagem, "descricao")));
    }

    private static APIGatewayV2HTTPResponse ok(Object corpo) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(CABECALHOS)
                .withBody(Json.escrever(corpo))
                .withIsBase64Encoded(false)
                .build();
    }
}
