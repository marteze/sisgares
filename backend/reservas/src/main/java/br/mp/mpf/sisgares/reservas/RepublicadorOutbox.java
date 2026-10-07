package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lambda agendada (a cada 1 minuto, EventosStack) que republica os Evento_Reserva pendentes no
 * outbox: para cada um, {@code PutEvents} e remoção do item {@code OUTBOX} (Req. 2.8).
 *
 * <p>Uma falha em um evento é registrada e não interrompe os demais; o evento continua no outbox
 * para a próxima execução. Republicar um evento já entregue é seguro, pois os consumidores são
 * idempotentes por {@code eventoId}.
 */
public final class RepublicadorOutbox implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final PortaReservas reservas;
    private final PublicadorEventos publicador;
    private final LogEstruturado log;

    /** Construtor usado pelo runtime da Lambda (dependências reais, criadas no cold start). */
    public RepublicadorOutbox() {
        this(Producao.ADAPTADOR, Producao.PUBLICADOR, Producao.LOG);
    }

    /** Construtor com dependências injetadas (testes). */
    public RepublicadorOutbox(PortaReservas reservas, PublicadorEventos publicador, LogEstruturado log) {
        this.reservas = Objects.requireNonNull(reservas, "reservas");
        this.publicador = Objects.requireNonNull(publicador, "publicador");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Inicialização preguiçosa das dependências de produção (somente no runtime). */
    private static final class Producao {
        static final Clock RELOGIO = ConstantesDominio.relogioPadrao();
        static final LogEstruturado LOG = new LogEstruturado(RELOGIO, "republicador-outbox");
        static final AdaptadorDynamo ADAPTADOR = criarAdaptador();
        static final PublicadorEventos PUBLICADOR = PublicadorEventBridge.doAmbiente(
                System.getenv(PublicadorEventBridge.VAR_BARRAMENTO), LOG);

        private static AdaptadorDynamo criarAdaptador() {
            ClienteDynamo dynamo = ClienteDynamo.padrao();
            return new AdaptadorDynamo(dynamo.cliente(), dynamo.nomeTabela(), RELOGIO);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> entrada, Context contexto) {
        return republicar();
    }

    /** Publica e remove cada evento pendente; devolve as contagens da execução. */
    public Map<String, Object> republicar() {
        List<EventoOutbox> pendentes = reservas.listarOutboxPendentes();
        int publicados = 0;
        int falhas = 0;
        for (EventoOutbox evento : pendentes) {
            try {
                publicador.publicar(evento);
                reservas.removerOutbox(evento);
                publicados++;
            } catch (RuntimeException e) {
                falhas++;
                log.erro(evento.eventoId(), "Falha ao republicar Evento_Reserva; mantido no outbox", e);
            }
        }
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("pendentes", pendentes.size());
        saida.put("publicados", publicados);
        saida.put("falhas", falhas);
        log.info(null, "Republicação do outbox concluída", saida);
        return saida;
    }
}
