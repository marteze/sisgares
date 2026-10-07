package br.mp.mpf.sisgares.eventos.notificador;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Envio pelo Amazon SES com identidade verificada (Req. 13.5). Usado somente com
 * {@code MODO_EMAIL=ses}; em sandbox ou local, use {@link CaixaSimulada}.
 */
public final class EnviadorSes implements EnviadorEmail {

    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private final SesClient ses;
    private final String remetente;

    /**
     * @param ses       cliente SES
     * @param remetente identidade verificada no SES (fictícia, ex.: {@code sisgares@exemplo.gov.br})
     */
    public EnviadorSes(SesClient ses, String remetente) {
        this.ses = Objects.requireNonNull(ses, "ses");
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalArgumentException("remetente SES é obrigatório");
        }
        this.remetente = remetente.strip();
    }

    @Override
    public StatusNotificacao enviar(List<String> para, Email email) {
        if (para == null || para.isEmpty()) {
            throw new IllegalArgumentException("sem destinatários");
        }
        ses.sendEmail(SendEmailRequest.builder()
                .source(remetente)
                .destination(Destination.builder().toAddresses(para).build())
                .message(Message.builder()
                        .subject(Content.builder().charset(UTF8).data(email.assunto()).build())
                        .body(Body.builder()
                                .html(Content.builder().charset(UTF8).data(email.html()).build())
                                .build())
                        .build())
                .build());
        return StatusNotificacao.ENVIADA;
    }
}
