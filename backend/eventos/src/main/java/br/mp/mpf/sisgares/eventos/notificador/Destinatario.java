package br.mp.mpf.sisgares.eventos.notificador;

import java.util.List;

/**
 * Setor_Envolvido a notificar e os endereços efetivos (lista alternativa ou ENVO_EMAIL).
 */
public record Destinatario(long envoId, List<String> emails) {

    public Destinatario {
        emails = List.copyOf(emails);
        if (emails.isEmpty()) {
            throw new IllegalArgumentException("destinatário sem e-mail");
        }
    }
}
