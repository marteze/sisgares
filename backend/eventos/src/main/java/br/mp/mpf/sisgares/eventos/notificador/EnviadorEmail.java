package br.mp.mpf.sisgares.eventos.notificador;

import java.util.List;

/**
 * Envio de e-mail: {@link EnviadorSes} (Amazon SES) ou {@link CaixaSimulada} (sandbox/local).
 */
public interface EnviadorEmail {

    /**
     * Envia o e-mail aos endereços informados.
     *
     * @return {@link StatusNotificacao#ENVIADA} ou {@link StatusNotificacao#SIMULADA}
     * @throws RuntimeException se o envio falhar (o Notificador registra a falha)
     */
    StatusNotificacao enviar(List<String> para, Email email);
}
