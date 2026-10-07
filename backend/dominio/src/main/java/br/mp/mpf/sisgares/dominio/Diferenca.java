package br.mp.mpf.sisgares.dominio;

import java.util.Objects;

/**
 * Diferença de um campo entre duas Versões_Reserva (Req. 13.4).
 *
 * <p>Os valores já vêm formatados para exibição; o escape HTML é feito por quem monta o e-mail
 * ({@link EscapadorHtml#escapar}).
 *
 * @param campo       nome do campo alterado
 * @param valorAntigo valor formatado na versão anterior
 * @param valorNovo   valor formatado na nova versão
 */
public record Diferenca(String campo, String valorAntigo, String valorNovo) {
    public Diferenca {
        Objects.requireNonNull(campo, "campo é obrigatório");
        Objects.requireNonNull(valorAntigo, "valorAntigo é obrigatório");
        Objects.requireNonNull(valorNovo, "valorNovo é obrigatório");
    }
}
