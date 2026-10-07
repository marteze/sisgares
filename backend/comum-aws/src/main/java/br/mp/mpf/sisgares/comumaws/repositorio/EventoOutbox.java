package br.mp.mpf.sisgares.comumaws.repositorio;

import java.time.Instant;
import java.util.Objects;

/**
 * Evento de alteração de Reserva gravado no outbox na mesma transação (Req. 12.4, 12.8).
 *
 * <p>Não carrega dados pessoais: os consumidores leem a Reserva pelo {@code reseId}.
 *
 * @param eventoId   identificador único (idempotência nos consumidores)
 * @param tipo       tipo do evento (ex.: {@code RESERVA_CRIADA}, {@code RESERVA_ALTERADA})
 * @param reseId     RESE_ID da Reserva afetada
 * @param versao     versão da Reserva gravada com o evento
 * @param ocorridoEm instante do evento (compõe a SK do outbox)
 */
public record EventoOutbox(String eventoId, String tipo, long reseId, long versao, Instant ocorridoEm) {

    public EventoOutbox {
        Objects.requireNonNull(eventoId, "eventoId é obrigatório");
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm é obrigatório");
        if (eventoId.isBlank() || tipo.isBlank()) {
            throw new IllegalArgumentException("eventoId e tipo não podem ser vazios");
        }
    }
}
