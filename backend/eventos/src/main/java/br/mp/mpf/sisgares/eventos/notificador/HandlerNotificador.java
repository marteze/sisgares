package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Lambda do Notificador, chamada pelo Step Functions com o Evento_Reserva
 * {@code {eventoId, reseId, versao, tipo}} (também aceito dentro de {@code detail}, formato EventBridge).
 *
 * <p>Variáveis de ambiente:
 * <ul>
 *   <li>{@code TABELA_SISGARES}: tabela única (obrigatória).</li>
 *   <li>{@code MODO_EMAIL}: {@code ses} ou {@code simulado} (padrão {@code simulado}; use
 *       {@code simulado} com SES em sandbox ou execução local).</li>
 *   <li>{@code SES_REMETENTE}: identidade verificada no SES (obrigatória com {@code ses}).</li>
 * </ul>
 * Exceções são propagadas para o retry do Step Functions.
 */
public final class HandlerNotificador implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    public static final String VAR_MODO_EMAIL = "MODO_EMAIL";
    public static final String VAR_REMETENTE = "SES_REMETENTE";
    static final String MODO_SES = "ses";
    static final String MODO_SIMULADO = "simulado";
    static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    private final Notificador notificador;

    /** Construtor usado pela Lambda: dependências criadas uma vez por contêiner. */
    public HandlerNotificador() {
        this(criarPadrao(System::getenv));
    }

    /** Construtor para testes. */
    public HandlerNotificador(Notificador notificador) {
        this.notificador = Objects.requireNonNull(notificador, "notificador");
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> entrada, Context context) {
        Notificador.Resultado r = notificador.processar(lerEvento(entrada));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("eventoId", r.eventoId());
        saida.put("jaProcessado", r.jaProcessado());
        saida.put("enviadas", r.enviadas());
        saida.put("ignoradas", r.ignoradas());
        return saida;
    }

    /** Converte a entrada do Step Functions em {@link EventoReserva}. */
    @SuppressWarnings("unchecked")
    static EventoReserva lerEvento(Map<String, Object> entrada) {
        if (entrada == null) {
            throw new IllegalArgumentException("Evento_Reserva ausente.");
        }
        Map<String, Object> dados = entrada.get("detail") instanceof Map<?, ?> detalhe
                ? (Map<String, Object>) detalhe
                : entrada;
        return new EventoReserva(
                texto(dados.get("eventoId"), "eventoId"),
                numero(dados.get("reseId"), "reseId"),
                numero(dados.get("versao"), "versao"),
                TipoEvento.de(texto(dados.get("tipo"), "tipo")));
    }

    private static String texto(Object valor, String campo) {
        if (valor == null || valor.toString().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no evento: " + campo);
        }
        return valor.toString().strip();
    }

    private static long numero(Object valor, String campo) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(texto(valor, campo));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Campo numérico inválido no evento: " + campo, e);
        }
    }

    /** Monta o Notificador conforme {@code MODO_EMAIL}. */
    static Notificador criarPadrao(Function<String, String> ambiente) {
        Clock clock = Clock.system(FUSO);
        ClienteDynamo dynamo = ClienteDynamo.padrao();
        String tabela = dynamo.nomeTabela();
        CaixaSimulada caixa = new CaixaSimulada(dynamo.cliente(), tabela);
        EnviadorEmail enviador = switch (modo(ambiente.apply(VAR_MODO_EMAIL))) {
            case MODO_SES -> new EnviadorSes(SesClient.create(), ambiente.apply(VAR_REMETENTE));
            default -> caixa;
        };
        return new Notificador(
                new LeitorDadosDynamo(dynamo.cliente(), tabela),
                enviador,
                caixa,
                new RegistroIdempotenciaDynamo(dynamo.cliente(), tabela, RegistroIdempotenciaDynamo.CONSUMIDOR, clock),
                clock,
                new LogEstruturado(clock, "notificador"));
    }

    /** Normaliza o modo; padrão {@code simulado}. */
    static String modo(String valor) {
        if (valor == null || valor.isBlank()) {
            return MODO_SIMULADO;
        }
        String m = valor.strip().toLowerCase(Locale.ROOT);
        if (!m.equals(MODO_SES) && !m.equals(MODO_SIMULADO)) {
            throw new IllegalStateException("MODO_EMAIL inválido: use 'ses' ou 'simulado'.");
        }
        return m;
    }
}
