package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioConfiguracao;
import br.mp.mpf.sisgares.eventos.notificador.EventoReserva;
import br.mp.mpf.sisgares.eventos.notificador.RegistroIdempotenciaDynamo;
import br.mp.mpf.sisgares.eventos.notificador.TipoEvento;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lambda do Cliente_SNP, chamada pelo Step Functions com o Evento_Reserva
 * {@code {eventoId, reseId, versao, tipo}} (também aceito dentro de {@code detail}).
 *
 * <p>Variável de ambiente: {@code TABELA_SISGARES}. O endpoint do SNP vem da Configuração
 * ({@code CONFIG/GLOBAL}, atributo {@code snpUrl}). Exceções são propagadas para o retry.
 */
public final class HandlerSnp implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    /** Consumidor de idempotência em {@code EVT#<eventoId>}. */
    public static final String CONSUMIDOR = "CLIENTE_SNP";
    static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    private final ClienteSnp clienteSnp;

    /** Construtor usado pela Lambda: dependências criadas uma vez por contêiner. */
    public HandlerSnp() {
        this(criarPadrao());
    }

    /** Construtor para testes. */
    public HandlerSnp(ClienteSnp clienteSnp) {
        this.clienteSnp = Objects.requireNonNull(clienteSnp, "clienteSnp");
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> entrada, Context context) {
        ClienteSnp.Resultado r = clienteSnp.processar(lerEvento(entrada));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("eventoId", r.eventoId());
        saida.put("jaProcessado", r.jaProcessado());
        saida.put("registrados", r.registrados());
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

    static ClienteSnp criarPadrao() {
        Clock clock = Clock.system(FUSO);
        ClienteDynamo dynamo = ClienteDynamo.padrao();
        String tabela = dynamo.nomeTabela();
        RepositorioConfiguracao configuracao = new RepositorioConfiguracao(dynamo.cliente(), tabela, clock);
        return new ClienteSnp(
                new RepositorioSnpDynamo(dynamo.cliente(), tabela, clock),
                new GatewaySnpHttp(),
                configuracao::snpUrl,
                new RegistroIdempotenciaDynamo(dynamo.cliente(), tabela, CONSUMIDOR, clock),
                clock,
                new LogEstruturado(clock, "cliente-snp"));
    }
}
