package br.mp.mpf.sisgares.exportacao;

import br.mp.mpf.sisgares.comumaws.auth.Autorizador;
import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.comumaws.http.RespostaErro;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioReservas;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Consulta;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Formato;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Grupo;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lambda de exportação: {@code POST /api/exportacoes} com corpo {@code {formato, data, colunas, fds}}
 * (Req. 17.1–17.3). Responde {@code {url, expiraEm}} com URL pré-assinada de 5 minutos.
 *
 * <p>O handler apenas traduz HTTP; visibilidade em {@link ServicoExportacao}, formatos em
 * {@link GeradorCsv}/{@link GeradorPdf} e gravação em {@link ArmazenamentoS3}.
 */
public final class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String ROTA = "/api/exportacoes";
    static final String ROTA_INEXISTENTE = "ROTA_INEXISTENTE";
    private static final Map<String, String> CABECALHOS =
            Map.of("Content-Type", "application/json; charset=utf-8");

    /** Corpo da resposta. */
    public record RespostaExportacao(String url, String expiraEm) {
    }

    private final ServicoExportacao servico;
    private final Armazenamento armazenamento;
    private final ExtratorPrincipal extrator;
    private final LogEstruturado log;
    private final MapeadorErros erros;
    private final Clock relogio;

    /** Construtor usado pela Lambda: dependências criadas uma vez por contêiner. */
    public Handler() {
        this(Clock.system(ConstantesDominio.ZONA), ClienteDynamo.padrao());
    }

    private Handler(Clock relogio, ClienteDynamo dynamo) {
        this(new ServicoExportacao(new FonteDynamo(
                        new RepositorioCatalogo(dynamo.cliente(), dynamo.nomeTabela()),
                        new RepositorioReservas(dynamo.cliente(), dynamo.nomeTabela())),
                        Autorizador.doAmbiente()),
                ArmazenamentoS3.doAmbiente(),
                ExtratorPrincipal.doAmbiente(relogio),
                new LogEstruturado(relogio, "exportacao"),
                relogio);
    }

    /** Construtor para testes, sem AWS. */
    Handler(ServicoExportacao servico, Armazenamento armazenamento, ExtratorPrincipal extrator,
            LogEstruturado log, Clock relogio) {
        this.servico = Objects.requireNonNull(servico, "servico");
        this.armazenamento = Objects.requireNonNull(armazenamento, "armazenamento");
        this.extrator = Objects.requireNonNull(extrator, "extrator");
        this.log = Objects.requireNonNull(log, "log");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.erros = new MapeadorErros(log);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context contexto) {
        String correlationId = MapeadorErros.correlationId(evento);
        try {
            Principal usuario = extrator.extrair(evento);
            if (!ROTA.equals(rota(evento))) {
                return erro(404, "Rota não encontrada. Verifique o endereço da API.", correlationId);
            }
            if (!"POST".equals(metodo(evento))) {
                return erro(405, "Método não suportado. Use POST.", correlationId);
            }
            Map<String, Object> corpo;
            try {
                corpo = lerCorpo(evento);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                return MapeadorErros.requisicaoInvalida(correlationId);
            }
            Consulta consulta = ServicoExportacao.lerParametros(corpo);
            List<Grupo> grupos = servico.consultar(consulta, usuario);

            byte[] conteudo = consulta.formato() == Formato.CSV
                    ? GeradorCsv.gerar(grupos)
                    : GeradorPdf.gerar(grupos, "SISGARES - Reservas a partir de " + consulta.data());
            String extensao = consulta.formato().extensao();
            String chave = ArmazenamentoS3.chave(usuario.sub(), relogio, extensao);
            Armazenamento.UrlAssinada url = armazenamento.gravar(chave, conteudo,
                    consulta.formato().contentType(), "reservas-" + consulta.data() + "." + extensao);

            // Log sem dados pessoais: formato, parâmetros e contagem de linhas (sem URL nem chave)
            log.info(correlationId, "Exportação gerada", Map.of(
                    "formato", extensao,
                    "data", consulta.data().toString(),
                    "colunas", consulta.colunas(),
                    "linhas", grupos.stream().mapToInt(g -> g.linhas().size()).sum(),
                    "bytes", conteudo.length));
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(200)
                    .withHeaders(CABECALHOS)
                    .withBody(Json.escrever(new RespostaExportacao(url.url(), url.expiraEm().toString())))
                    .withIsBase64Encoded(false)
                    .build();
        } catch (RuntimeException e) {
            return erros.mapear(e, correlationId);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lerCorpo(APIGatewayV2HTTPEvent evento) throws JsonProcessingException {
        String corpo = evento.getBody();
        if (corpo == null || corpo.isBlank()) {
            throw new IllegalArgumentException("corpo ausente");
        }
        if (evento.getIsBase64Encoded()) {
            corpo = new String(Base64.getDecoder().decode(corpo), StandardCharsets.UTF_8);
        }
        Map<String, Object> mapa = Json.ler(corpo, Map.class);
        if (mapa == null) {
            throw new IllegalArgumentException("corpo nulo");
        }
        return mapa;
    }

    private static String metodo(APIGatewayV2HTTPEvent evento) {
        var ctx = evento.getRequestContext();
        if (ctx == null || ctx.getHttp() == null || ctx.getHttp().getMethod() == null) {
            return "";
        }
        return ctx.getHttp().getMethod().toUpperCase();
    }

    private static String rota(APIGatewayV2HTTPEvent evento) {
        String caminho = evento.getRawPath();
        if (caminho == null) {
            return "";
        }
        return caminho.length() > 1 && caminho.endsWith("/") ? caminho.substring(0, caminho.length() - 1) : caminho;
    }

    private static APIGatewayV2HTTPResponse erro(int status, String mensagem, String correlationId) {
        return MapeadorErros.resposta(status, RespostaErro.unico(ROTA_INEXISTENTE, mensagem, correlationId));
    }

    /** Adaptador dos repositórios de comum-aws (somente Query/GetItem). */
    static final class FonteDynamo implements ServicoExportacao.FonteDados {
        private final RepositorioCatalogo catalogo;
        private final RepositorioReservas reservas;

        FonteDynamo(RepositorioCatalogo catalogo, RepositorioReservas reservas) {
            this.catalogo = catalogo;
            this.reservas = reservas;
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
    }
}
