package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Publica o Evento_Reserva no barramento após o commit (Req. 2.3, 2.8).
 *
 * <p>O evento leva somente IDs, versão e tipo, sem dados pessoais. O {@code detail} segue o
 * formato lido por {@code HandlerNotificador.lerEvento}: {@code eventoId}, {@code reseId},
 * {@code versao} e {@code tipo} ({@code ReservaCriada}/{@code ReservaAlterada}/{@code ReservaCancelada}).
 */
public interface PublicadorEventos {

    /** Origem ({@code source}) dos eventos no EventBridge, igual à regra da EventosStack. */
    String ORIGEM = "sisgares.reservas";

    /**
     * Publica o evento.
     *
     * @throws RuntimeException se a publicação falhar (o evento permanece no outbox)
     */
    void publicar(EventoOutbox evento);

    /** Monta o {@code detail} do evento: apenas IDs, versão e tipo. */
    static Map<String, Object> detalhe(EventoOutbox evento) {
        Objects.requireNonNull(evento, "evento");
        Map<String, Object> detalhe = new LinkedHashMap<>();
        detalhe.put("eventoId", evento.eventoId());
        detalhe.put("reseId", evento.reseId());
        detalhe.put("versao", evento.versao());
        detalhe.put("tipo", evento.tipo());
        return detalhe;
    }

    /** Publicador nulo para o modo local (sem {@code BARRAMENTO}): só registra em log. */
    static PublicadorEventos nulo(LogEstruturado log) {
        Objects.requireNonNull(log, "log");
        return evento -> log.info(evento.eventoId(),
                "Modo local: Evento_Reserva não publicado no barramento", detalhe(evento));
    }
}
