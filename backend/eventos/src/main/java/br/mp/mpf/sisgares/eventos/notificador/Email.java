package br.mp.mpf.sisgares.eventos.notificador;

import java.util.Objects;

/**
 * E-mail montado pelo {@link MontadorEmail}: assunto em texto simples e corpo HTML já escapado.
 */
public record Email(String assunto, String html) {

    public Email {
        Objects.requireNonNull(assunto, "assunto é obrigatório");
        Objects.requireNonNull(html, "html é obrigatório");
    }
}
