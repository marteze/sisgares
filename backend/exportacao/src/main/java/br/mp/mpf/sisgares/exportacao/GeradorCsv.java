package br.mp.mpf.sisgares.exportacao;

import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Grupo;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Linha;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera o CSV da exportação (Req. 17.1): UTF-8, separador {@code ;}, fim de linha CRLF e uma linha
 * por Período.
 *
 * <p>Escape conforme RFC 4180 (adaptado ao {@code ;}): campos com {@code ;}, aspas ou quebras de
 * linha ficam entre aspas, com aspas internas duplicadas. Contra injeção de fórmula em planilhas,
 * campos iniciados por {@code = + - @} (ou tabulação/CR) recebem o prefixo {@code '}.
 */
public final class GeradorCsv {

    public static final char SEPARADOR = ';';
    public static final String FIM_LINHA = "\r\n";
    public static final List<String> CABECALHO = List.of(
            "data", "inicio", "termino", "reserva", "ambiente", "finalidade", "solicitante", "recursos", "cancelada");

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private GeradorCsv() {
    }

    /** Conteúdo em UTF-8 (sem BOM). */
    public static byte[] gerar(List<Grupo> grupos) {
        StringBuilder sb = new StringBuilder();
        linha(sb, CABECALHO);
        for (Grupo g : grupos) {
            for (Linha l : g.linhas()) {
                linha(sb, List.of(
                        g.data().toString(),
                        HORA.format(l.inicio()),
                        // Término com data: o Período pode terminar em outro dia
                        DATA_HORA.format(l.termino()),
                        Long.toString(l.reservaId()),
                        nulo(l.ambiente()),
                        nulo(l.finalidade()),
                        nulo(l.solicitante()),
                        nulo(l.recursos()),
                        l.cancelada() ? "sim" : "nao"));
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void linha(StringBuilder sb, List<String> campos) {
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                sb.append(SEPARADOR);
            }
            sb.append(escapar(campos.get(i)));
        }
        sb.append(FIM_LINHA);
    }

    /** Neutraliza fórmulas e aplica as aspas quando necessário. */
    static String escapar(String campo) {
        String valor = campo == null ? "" : campo;
        if (!valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0) {
            valor = "'" + valor;
        }
        boolean precisaAspas = valor.indexOf(SEPARADOR) >= 0 || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0;
        return precisaAspas ? '"' + valor.replace("\"", "\"\"") + '"' : valor;
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }
}
