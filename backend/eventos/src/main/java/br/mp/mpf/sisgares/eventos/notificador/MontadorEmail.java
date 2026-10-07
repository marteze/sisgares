package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.dominio.Diferenca;
import br.mp.mpf.sisgares.dominio.EscapadorHtml;
import java.util.List;
import java.util.Objects;

/**
 * Montagem pura (sem AWS) do e-mail de notificação (Req. 13.3, 13.4).
 *
 * <p>Todo texto variável passa por {@link EscapadorHtml#escapar}. Na alteração, cada campo
 * alterado é exibido com o valor antigo em {@code <del>} e o novo em {@code <ins>}, com estilo
 * inline (clientes de e-mail costumam ignorar {@code <style>}).
 */
public final class MontadorEmail {

    /** Estilo do valor antigo: vermelho e riscado. */
    static final String ESTILO_ANTIGO =
            "background-color:#fde2e1;color:#8a1c1c;text-decoration:line-through;padding:0 4px;";
    /** Estilo do valor novo: verde e em negrito. */
    static final String ESTILO_NOVO =
            "background-color:#dcf5e3;color:#14532d;text-decoration:none;font-weight:bold;padding:0 4px;";
    static final String VALOR_VAZIO = "(vazio)";
    private static final String ESTILO_TABELA = "border-collapse:collapse;font-family:Arial,sans-serif;font-size:14px;";
    private static final String ESTILO_TH = "text-align:left;padding:4px 8px;border:1px solid #cccccc;background-color:#f2f2f2;";
    private static final String ESTILO_TD = "padding:4px 8px;border:1px solid #cccccc;";

    private MontadorEmail() {
    }

    /** Monta assunto e HTML a partir do conteúdo resolvido. */
    public static Email montar(ConteudoEmail c) {
        Objects.requireNonNull(c, "conteúdo é obrigatório");
        return new Email(assunto(c), html(c));
    }

    /** Assunto em texto simples, sem quebras de linha (evita injeção de cabeçalho). */
    static String assunto(ConteudoEmail c) {
        return "SISGARES: " + c.tipo().rotuloMinusculo() + " da reserva nº " + c.reseId();
    }

    static String html(ConteudoEmail c) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">")
                .append("<title>").append(esc(assunto(c))).append("</title></head>")
                .append("<body style=\"font-family:Arial,sans-serif;font-size:14px;color:#1a1a1a;\">")
                .append("<h1 style=\"font-size:18px;\">").append(esc(c.tipo().rotulo()))
                .append(" da reserva nº ").append(c.reseId()).append("</h1>");

        if (c.tipo() == TipoEvento.ALTERADA && !c.diferencas().isEmpty()) {
            anexarDiferencas(sb, c.diferencas());
        }

        sb.append("<h2 style=\"font-size:16px;\">Dados da reserva</h2>")
                .append("<table role=\"presentation\" style=\"").append(ESTILO_TABELA).append("\">");
        linha(sb, "Evento", c.tipo().rotulo());
        linha(sb, "Ambiente", c.ambiente());
        linha(sb, "Finalidade", c.finalidade());
        linha(sb, "Participantes", c.participantes());
        linha(sb, "Disposição", c.disposicao());
        linhaLista(sb, "Períodos", c.periodos());
        linhaLista(sb, "Recursos", c.recursos());
        linha(sb, "Solicitante", c.solicitante());
        linhaLista(sb, "Pedidos SNP", c.pedidosSnp());
        sb.append("</table>")
                .append("<p style=\"font-size:12px;color:#555555;\">Mensagem automática do SISGARES. ")
                .append("Não responda este e-mail.</p></body></html>");
        return sb.toString();
    }

    /** Tabela de alterações: campo, valor antigo ({@code <del>}) e novo ({@code <ins>}). */
    private static void anexarDiferencas(StringBuilder sb, List<Diferenca> diferencas) {
        sb.append("<h2 style=\"font-size:16px;\">Alterações</h2>")
                .append("<table style=\"").append(ESTILO_TABELA).append("\">")
                .append("<thead><tr>")
                .append("<th scope=\"col\" style=\"").append(ESTILO_TH).append("\">Campo</th>")
                .append("<th scope=\"col\" style=\"").append(ESTILO_TH).append("\">Antes</th>")
                .append("<th scope=\"col\" style=\"").append(ESTILO_TH).append("\">Depois</th>")
                .append("</tr></thead><tbody>");
        for (Diferenca d : diferencas) {
            sb.append("<tr><th scope=\"row\" style=\"").append(ESTILO_TH).append("\">")
                    .append(esc(d.campo())).append("</th>")
                    .append("<td style=\"").append(ESTILO_TD).append("\"><del style=\"").append(ESTILO_ANTIGO)
                    .append("\">").append(esc(ouVazio(d.valorAntigo()))).append("</del></td>")
                    .append("<td style=\"").append(ESTILO_TD).append("\"><ins style=\"").append(ESTILO_NOVO)
                    .append("\">").append(esc(ouVazio(d.valorNovo()))).append("</ins></td></tr>");
        }
        sb.append("</tbody></table>");
    }

    private static void linha(StringBuilder sb, String rotulo, String valor) {
        sb.append("<tr><th scope=\"row\" style=\"").append(ESTILO_TH).append("\">").append(esc(rotulo))
                .append("</th><td style=\"").append(ESTILO_TD).append("\">").append(esc(ouVazio(valor)))
                .append("</td></tr>");
    }

    private static void linhaLista(StringBuilder sb, String rotulo, List<String> valores) {
        sb.append("<tr><th scope=\"row\" style=\"").append(ESTILO_TH).append("\">").append(esc(rotulo))
                .append("</th><td style=\"").append(ESTILO_TD).append("\">");
        if (valores.isEmpty()) {
            sb.append(esc(VALOR_VAZIO));
        } else {
            sb.append("<ul style=\"margin:0;padding-left:18px;\">");
            for (String v : valores) {
                sb.append("<li>").append(esc(ouVazio(v))).append("</li>");
            }
            sb.append("</ul>");
        }
        sb.append("</td></tr>");
    }

    private static String ouVazio(String valor) {
        return valor == null || valor.isBlank() ? VALOR_VAZIO : valor;
    }

    private static String esc(String texto) {
        return EscapadorHtml.escapar(texto);
    }
}
