package br.mp.mpf.sisgares.dominio;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mascaramento de Dados_Pessoais (e-mail, CPF e nomes) em textos livres de log (LGPD, Req. 6.7).
 *
 * <p>Exemplos: {@code joao@exemplo.gov.br} → {@code j***@exemplo.gov.br};
 * {@code 123.456.789-09} → {@code ***.***.***-**}; {@code Maria Silva} → {@code M*** S***}.
 * A saída nunca contém o valor original mascarado (Property 15).
 */
public final class MascaradorDados {

    /** E-mail: parte local + domínio com TLD. */
    private static final Pattern EMAIL =
            Pattern.compile("([A-Za-z0-9._%+\\-]+)@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");

    /** CPF formatado ou só dígitos, sem dígitos adjacentes. */
    private static final Pattern CPF =
            Pattern.compile("(?<![\\p{N}])\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?![\\p{N}])");

    /**
     * Heurística de nome próprio: duas ou mais palavras iniciadas por maiúscula, admitindo
     * conectivos (de, da, do, das, dos, e) entre elas.
     */
    private static final Pattern NOME = Pattern.compile(
            "(?<![\\p{L}\\p{N}])\\p{Lu}[\\p{L}']+"
                    + "(?:\\s+(?:(?:d[aeo]s?|e)\\s+)?\\p{Lu}[\\p{L}']+)+"
                    + "(?![\\p{L}\\p{N}])");

    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private static final String MASCARA_CPF = "***.***.***-**";

    private MascaradorDados() {
    }

    /**
     * Mascara e-mails, CPFs e nomes (heurística) em um texto livre de log.
     *
     * @param texto linha de log; {@code null} devolve {@code null}
     * @return texto com Dados_Pessoais mascarados
     */
    public static String mascarar(String texto) {
        return mascarar(texto, List.of());
    }

    /**
     * Mascara e-mails, CPFs, os nomes conhecidos informados e nomes detectados pela heurística.
     *
     * @param texto linha de log; {@code null} devolve {@code null}
     * @param nomesConhecidos nomes que devem ser mascarados mesmo que a heurística não os detecte
     * @return texto com Dados_Pessoais mascarados
     */
    public static String mascarar(String texto, Collection<String> nomesConhecidos) {
        if (texto == null) {
            return null;
        }
        String resultado = mascararEmails(texto);
        resultado = CPF.matcher(resultado).replaceAll(Matcher.quoteReplacement(MASCARA_CPF));
        if (nomesConhecidos != null) {
            for (String nome : nomesConhecidos) {
                if (nome == null || nome.isBlank()) {
                    continue;
                }
                Pattern p = Pattern.compile(Pattern.quote(nome.strip()),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                resultado = p.matcher(resultado)
                        .replaceAll(Matcher.quoteReplacement(mascararNome(nome)));
            }
        }
        Matcher m = NOME.matcher(resultado);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(mascararNome(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Mascara um nome mantendo apenas a inicial de cada palavra: {@code Maria Silva} → {@code M*** S***}.
     * Palavras de uma letra viram {@code *}.
     *
     * @param nome nome completo; {@code null} devolve {@code null}
     * @return nome mascarado
     */
    public static String mascararNome(String nome) {
        if (nome == null) {
            return null;
        }
        String limpo = nome.strip();
        if (limpo.isEmpty()) {
            return limpo;
        }
        StringBuilder sb = new StringBuilder();
        for (String palavra : ESPACOS.split(limpo)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            int primeiro = palavra.codePointAt(0);
            if (palavra.codePointCount(0, palavra.length()) <= 1) {
                sb.append('*');
            } else {
                sb.appendCodePoint(primeiro).append("***");
            }
        }
        return sb.toString();
    }

    /**
     * Mascara um e-mail mantendo a inicial e o domínio: {@code joao@exemplo.gov.br} →
     * {@code j***@exemplo.gov.br}.
     *
     * @param email e-mail; {@code null} devolve {@code null}
     * @return e-mail mascarado
     */
    public static String mascararEmail(String email) {
        if (email == null) {
            return null;
        }
        return mascararEmails(email);
    }

    /** Substitui todos os e-mails do texto pela forma mascarada. */
    private static String mascararEmails(String texto) {
        Matcher m = EMAIL.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String local = m.group(1);
            String mascarado = local.charAt(0) + "***@" + m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(mascarado));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
