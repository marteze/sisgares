package br.mp.mpf.sisgares.dominio;

import java.util.Objects;

/**
 * Violação de regra de negócio, mapeada para um item de {@code erros} no HTTP 422.
 *
 * @param codigo   código da regra (ver {@link CodigosRegra})
 * @param mensagem mensagem em português, sem dados pessoais
 * @param campo    campo relacionado (ex.: "periodos[0]"); {@code null} quando não aplicável
 */
public record Violacao(String codigo, String mensagem, String campo) {

    public Violacao {
        Objects.requireNonNull(codigo, "codigo é obrigatório");
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        if (codigo.isBlank()) {
            throw new IllegalArgumentException("codigo não pode ser vazio");
        }
    }

    /** Cria violação sem campo associado. */
    public static Violacao de(String codigo, String mensagem) {
        return new Violacao(codigo, mensagem, null);
    }
}
