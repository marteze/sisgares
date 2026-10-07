package br.mp.mpf.sisgares.dominio;

/**
 * Escape de texto livre para inserção em HTML (corpo de e-mail, páginas).
 *
 * <p>Implementação em Java puro, equivalente ao {@code Encode.forHtml} do OWASP Encoder para os
 * caracteres relevantes, mantendo o módulo {@code dominio} sem dependências de terceiros.
 * Escapa {@code & < > " ' /}; a saída nunca contém {@code <}, {@code >}, {@code "} ou {@code '}
 * literais. {@link #desescapar(String)} é o inverso exato de {@link #escapar(String)}
 * (Property 14).
 */
public final class EscapadorHtml {

    private EscapadorHtml() {
    }

    /**
     * Escapa os caracteres especiais de HTML como entidades.
     *
     * @param texto texto livre vindo do usuário; {@code null} devolve {@code null}
     * @return texto seguro para inserção em conteúdo ou atributo HTML
     */
    public static String escapar(String texto) {
        if (texto == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                case '/' -> sb.append("&#x2F;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Desfaz o escape produzido por {@link #escapar(String)}.
     *
     * <p>Reconhece as entidades geradas por {@code escapar} e também as formas decimais
     * {@code &#39;} e {@code &#47;}. Sequências desconhecidas são mantidas como estão.
     *
     * @param texto texto escapado; {@code null} devolve {@code null}
     * @return texto original
     */
    public static String desescapar(String texto) {
        if (texto == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(texto.length());
        int i = 0;
        while (i < texto.length()) {
            char c = texto.charAt(i);
            if (c == '&') {
                int fim = texto.indexOf(';', i);
                if (fim > i) {
                    String entidade = texto.substring(i, fim + 1);
                    Character original = traduzir(entidade);
                    if (original != null) {
                        sb.append(original.charValue());
                        i = fim + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Converte uma entidade conhecida no caractere original, ou {@code null} se desconhecida. */
    private static Character traduzir(String entidade) {
        return switch (entidade) {
            case "&amp;" -> '&';
            case "&lt;" -> '<';
            case "&gt;" -> '>';
            case "&quot;" -> '"';
            case "&#x27;", "&#39;" -> '\'';
            case "&#x2F;", "&#x2f;", "&#47;" -> '/';
            default -> null;
        };
    }
}
