package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import java.util.Objects;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

/**
 * Publicação via {@code PutEvents} no barramento configurado em {@code BARRAMENTO}.
 * {@code detail-type} = tipo do evento ({@code ReservaCriada} etc.), {@code source} = {@link #ORIGEM}.
 */
public final class PublicadorEventBridge implements PublicadorEventos {

    /** Variável de ambiente com o nome do barramento. */
    public static final String VAR_BARRAMENTO = "BARRAMENTO";

    private final EventBridgeClient cliente;
    private final String barramento;

    public PublicadorEventBridge(EventBridgeClient cliente, String barramento) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        if (barramento == null || barramento.isBlank()) {
            throw new IllegalArgumentException("barramento é obrigatório");
        }
        this.barramento = barramento;
    }

    @Override
    public void publicar(EventoOutbox evento) {
        Objects.requireNonNull(evento, "evento");
        PutEventsRequestEntry entrada = PutEventsRequestEntry.builder()
                .eventBusName(barramento)
                .source(ORIGEM)
                .detailType(evento.tipo())
                .detail(Json.escrever(PublicadorEventos.detalhe(evento)))
                .build();
        PutEventsResponse resposta = cliente.putEvents(PutEventsRequest.builder().entries(entrada).build());
        // PutEvents não lança exceção em falha parcial: é preciso conferir o contador
        Integer falhas = resposta.failedEntryCount();
        if (falhas != null && falhas > 0) {
            String codigo = resposta.entries().isEmpty() ? "desconhecido" : resposta.entries().get(0).errorCode();
            throw new IllegalStateException("Falha ao publicar Evento_Reserva no barramento: " + codigo);
        }
    }

    /**
     * Publicador conforme o ambiente: EventBridge quando {@code BARRAMENTO} existe; caso contrário,
     * o publicador nulo do modo local.
     */
    static PublicadorEventos doAmbiente(String barramento, LogEstruturado log) {
        if (barramento == null || barramento.isBlank()) {
            return PublicadorEventos.nulo(log);
        }
        return new PublicadorEventBridge(EventBridgeClient.create(), barramento);
    }
}
