package br.mp.mpf.sisgares.paineis;

import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.comumaws.http.RespostaErro;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioConfiguracao;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioReservas;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.CalculadoraGrade;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Reserva;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Lambda dos painéis (referenciada pela ApiStack como {@code br.mp.mpf.sisgares.paineis.Handler}).
 *
 * <p>Rotas:
 * <ul>
 *   <li>{@code GET /api/paineis/solicitante}: grade do Painel do Solicitante (Req. 15).</li>
 *   <li>{@code GET /api/paineis/atendente}: Painel do Atendente (Req. 16): GSI2 para Setor_Atendente,
 *       GSI3 para Administrador.</li>
 * </ul>
 * O handler apenas traduz HTTP; as regras ficam no domínio, no {@link ServicoPainelSolicitante}
 * e no {@link ServicoPainelAtendente}.
 */
public final class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String ROTA_SOLICITANTE = "/api/paineis/solicitante";
    static final String ROTA_ATENDENTE = "/api/paineis/atendente";
    static final String ROTA_INEXISTENTE = "ROTA_INEXISTENTE";

    private static final Map<String, String> CABECALHOS =
            Map.of("Content-Type", "application/json; charset=utf-8");

    private final ServicoPainelSolicitante servicoSolicitante;
    private final ServicoPainelAtendente servicoAtendente;
    private final ExtratorPrincipal extrator;
    private final LogEstruturado log;
    private final MapeadorErros erros;

    /** Construtor usado pela Lambda: dependências criadas uma vez por contêiner (SnapStart). */
    public Handler() {
        this(Clock.system(ConstantesDominio.ZONA), ClienteDynamo.padrao());
    }

    private Handler(Clock relogio, ClienteDynamo dynamo) {
        this(new ServicoPainelSolicitante(new FonteDynamo(
                        new RepositorioCatalogo(dynamo.cliente(), dynamo.nomeTabela()),
                        new RepositorioReservas(dynamo.cliente(), dynamo.nomeTabela()),
                        new RepositorioConfiguracao(dynamo.cliente(), dynamo.nomeTabela(), relogio)),
                        new CalculadoraGrade(relogio)),
                new ServicoPainelAtendente(new FonteAtendenteDynamo(
                        new RepositorioCatalogo(dynamo.cliente(), dynamo.nomeTabela()),
                        new RepositorioReservas(dynamo.cliente(), dynamo.nomeTabela()),
                        dynamo.cliente(), dynamo.nomeTabela())),
                ExtratorPrincipal.doAmbiente(relogio),
                new LogEstruturado(relogio, "paineis"));
    }

    /** Construtor para testes, sem AWS. */
    Handler(ServicoPainelSolicitante servicoSolicitante, ServicoPainelAtendente servicoAtendente,
            ExtratorPrincipal extrator, LogEstruturado log) {
        this.servicoSolicitante = Objects.requireNonNull(servicoSolicitante, "servicoSolicitante");
        this.servicoAtendente = Objects.requireNonNull(servicoAtendente, "servicoAtendente");
        this.extrator = Objects.requireNonNull(extrator, "extrator");
        this.log = Objects.requireNonNull(log, "log");
        this.erros = new MapeadorErros(log);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context contexto) {
        String correlationId = MapeadorErros.correlationId(evento);
        try {
            // Autenticação antes do roteamento: sem token, 401 em qualquer rota
            Principal usuario = extrator.extrair(evento);
            String metodo = metodo(evento);
            String rota = rota(evento);
            if (!"GET".equals(metodo)) {
                return erro(405, ROTA_INEXISTENTE, "Método não suportado. Use GET.", correlationId);
            }
            return switch (rota) {
                case ROTA_SOLICITANTE -> painelSolicitante(evento, usuario, correlationId);
                case ROTA_ATENDENTE -> painelAtendente(evento, usuario, correlationId);
                default -> erro(404, ROTA_INEXISTENTE,
                        "Rota não encontrada. Verifique o endereço da API.", correlationId);
            };
        } catch (RuntimeException e) {
            return erros.mapear(e, correlationId);
        }
    }

    private APIGatewayV2HTTPResponse painelSolicitante(APIGatewayV2HTTPEvent evento, Principal usuario,
                                                       String correlationId) {
        ServicoPainelSolicitante.Consulta consulta =
                ServicoPainelSolicitante.lerParametros(evento.getQueryStringParameters());
        ServicoPainelSolicitante.RespostaPainel resposta = servicoSolicitante.consultar(consulta, usuario);
        // Log sem dados pessoais: apenas ids técnicos e parâmetros
        log.info(correlationId, "Painel do Solicitante consultado", Map.of(
                "ambienteId", consulta.ambienteId(),
                "colunas", consulta.colunas(),
                "fds", consulta.fds()));
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(CABECALHOS)
                .withBody(Json.escrever(resposta))
                .withIsBase64Encoded(false)
                .build();
    }

    private APIGatewayV2HTTPResponse painelAtendente(APIGatewayV2HTTPEvent evento, Principal usuario,
                                                     String correlationId) {
        ServicoPainelAtendente.Consulta consulta =
                ServicoPainelAtendente.lerParametros(evento.getQueryStringParameters());
        ServicoPainelAtendente.RespostaPainel resposta = servicoAtendente.consultar(consulta, usuario);
        // Log sem dados pessoais: apenas parâmetros e contagem de cards
        log.info(correlationId, "Painel do Atendente consultado", Map.of(
                "data", consulta.data().toString(),
                "colunas", consulta.colunas(),
                "fds", consulta.fds(),
                "cards", resposta.colunas().stream().mapToInt(c -> c.cards().size()).sum()));
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(CABECALHOS)
                .withBody(Json.escrever(resposta))
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

    /** Adaptador dos repositórios DynamoDB para a fonte de dados do serviço (somente Query/GetItem). */
    static final class FonteDynamo implements ServicoPainelSolicitante.FonteDados {

        private final RepositorioCatalogo catalogo;
        private final RepositorioReservas reservas;
        private final RepositorioConfiguracao configuracao;

        FonteDynamo(RepositorioCatalogo catalogo, RepositorioReservas reservas,
                    RepositorioConfiguracao configuracao) {
            this.catalogo = catalogo;
            this.reservas = reservas;
            this.configuracao = configuracao;
        }

        @Override
        public Optional<Ambiente> ambiente(long id) {
            return catalogo.obterAmbiente(id).map(RegistroAmbiente::ambiente);
        }

        @Override
        public List<Ambiente> ambientesDaUnidade(String unidade) {
            return catalogo.listarAmbientes().stream()
                    .map(RegistroAmbiente::ambiente)
                    .filter(a -> a.unidade().equals(unidade))
                    .toList();
        }

        @Override
        public List<PeriodoOcupado> ocupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim) {
            return reservas.periodosOcupadosPorRaiz(raizId, ini, fim);
        }

        @Override
        public Optional<String> solicitante(long reservaId) {
            return reservas.obter(reservaId).map(Reserva::solicitante);
        }

        @Override
        public Configuracao configuracao() {
            return configuracao.obter();
        }
    }

    /**
     * Adaptador DynamoDB do Painel do Atendente (somente Query/GetItem, nunca Scan).
     * Pedidos_SNP: Query {@code PK=RESE#<id>} com {@code begins_with(SK, "SNP#")}, apenas status REGISTRADO
     * (formato gravado por {@code eventos/snp/RepositorioSnpDynamo}: {@code numero}, {@code link}, {@code status}).
     */
    static final class FonteAtendenteDynamo implements ServicoPainelAtendente.FonteDados {
        static final String STATUS = "status";
        static final String NUMERO = "numero";
        static final String LINK = "link";
        static final String REGISTRADO = "REGISTRADO";

        private final RepositorioCatalogo catalogo;
        private final RepositorioReservas reservas;
        private final DynamoDbClient cliente;
        private final String tabela;

        FonteAtendenteDynamo(RepositorioCatalogo catalogo, RepositorioReservas reservas,
                             DynamoDbClient cliente, String tabela) {
            this.catalogo = catalogo;
            this.reservas = reservas;
            this.cliente = cliente;
            this.tabela = tabela;
        }

        @Override
        public List<Reserva> porSetor(long envoId, LocalDateTime de, LocalDateTime ate) {
            return reservas.porSetor(envoId, de, ate);
        }

        @Override
        public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
            return reservas.porUnidade(unidade, de, ate);
        }

        @Override
        public Optional<String> descricaoAmbiente(long ambienteId) {
            return catalogo.obterAmbiente(ambienteId).map(r -> r.ambiente().descricao());
        }

        @Override
        public Optional<String> descricaoRecurso(long recursoId) {
            return catalogo.obterRecurso(recursoId).map(RegistroRecurso::recurso).map(r -> r.descricao());
        }

        @Override
        public List<ServicoPainelAtendente.PedidoSnpCard> pedidosSnp(long reservaId) {
            List<ServicoPainelAtendente.PedidoSnpCard> pedidos = new ArrayList<>();
            Map<String, AttributeValue> valores = new HashMap<>();
            valores.put(":pk", AttributeValue.fromS(Chaves.pkReserva(reservaId)));
            valores.put(":prefixo", AttributeValue.fromS(Chaves.SNP));
            Map<String, AttributeValue> inicio = null;
            do {
                QueryRequest.Builder req = QueryRequest.builder()
                        .tableName(tabela)
                        .keyConditionExpression("#pk = :pk AND begins_with(#sk, :prefixo)")
                        .expressionAttributeNames(Map.of("#pk", NomesTabela.PK, "#sk", NomesTabela.SK))
                        .expressionAttributeValues(valores);
                if (inicio != null) {
                    req.exclusiveStartKey(inicio);
                }
                QueryResponse resposta = cliente.query(req.build());
                for (Map<String, AttributeValue> item : resposta.items()) {
                    String status = texto(item, STATUS);
                    String numero = texto(item, NUMERO);
                    if (REGISTRADO.equals(status) && numero != null) {
                        pedidos.add(new ServicoPainelAtendente.PedidoSnpCard(numero, texto(item, LINK)));
                    }
                }
                inicio = resposta.hasLastEvaluatedKey() && !resposta.lastEvaluatedKey().isEmpty()
                        ? resposta.lastEvaluatedKey()
                        : null;
            } while (inicio != null);
            return pedidos;
        }

        private static String texto(Map<String, AttributeValue> item, String nome) {
            AttributeValue v = item.get(nome);
            return v == null ? null : v.s();
        }
    }
}
