package br.mp.mpf.sisgares.eventos.notificador;

import java.util.Objects;

/**
 * Evento_Reserva recebido do Step Functions: {@code {eventoId, reseId, versao, tipo}}.
 *
 * @param eventoId identificador único do evento (chave de idempotência)
 * @param reseId   RESE_ID
 * @param versao   número da Versão_Reserva gerada pelo evento
 * @param tipo     tipo do evento
 */
public record EventoReserva(String eventoId, long reseId, long versao, TipoEvento tipo) {

    public EventoReserva {
        if (eventoId == null || eventoId.isBlank()) {
            throw new IllegalArgumentException("eventoId é obrigatório");
        }
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        if (versao < 0) {
            throw new IllegalArgumentException("versao não pode ser negativa");
        }
    }
}
