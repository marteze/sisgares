package br.mp.mpf.sisgares.eventos.notificador;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Notificação gravada em {@code RESE#<id>} / {@code NOTI#<eventoId>#<envoId>} (design, Data Models).
 *
 * @param reseId        reserva
 * @param eventoId      evento de origem
 * @param envoId        Setor_Envolvido
 * @param tipo          tipo do evento
 * @param destinatarios endereços efetivos
 * @param assunto       assunto do e-mail
 * @param html          corpo HTML escapado
 * @param status        situação do envio
 * @param motivoFalha   motivo resumido da falha (sem dados pessoais); {@code null} se não falhou
 * @param registradaEm  instante do registro
 * @param expiraEm      TTL (epoch em segundos), 90 dias após o registro
 */
public record Notificacao(long reseId, String eventoId, long envoId, TipoEvento tipo,
                          List<String> destinatarios, String assunto, String html,
                          StatusNotificacao status, String motivoFalha, Instant registradaEm,
                          long expiraEm) {

    public Notificacao {
        Objects.requireNonNull(eventoId, "eventoId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(assunto, "assunto");
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registradaEm, "registradaEm");
        destinatarios = List.copyOf(destinatarios);
    }
}
