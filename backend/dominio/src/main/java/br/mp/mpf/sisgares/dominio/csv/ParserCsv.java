package br.mp.mpf.sisgares.dominio.csv;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser CSV (RFC 4180) em Java puro: separador vírgula, campos opcionalmente entre aspas,
 * aspas duplicadas ("") dentro de campos entre aspas e quebras de linha CRLF, LF ou CR.
 * Cada registro guarda o número da linha física em que começa (base 1) e, se houver,
 * a descrição do erro de formato encontrado.
 */
final class ParserCsv {

    /** Registro bruto lido do CSV. {@code erro} é null quando o registro está bem formado. */
    record RegistroCsv(int linha, List<String> campos, String erro) {
    }

    private ParserCsv() {
    }

    /** Lê todo o conteúdo do Reader e o divide em registros. */
    static List<RegistroCsv> analisar(Reader in) throws IOException {
        StringBuilder conteudo = new StringBuilder();
        char[] buffer = new char[8192];
        int lidos;
        while ((lidos = in.read(buffer)) != -1) {
            conteudo.append(buffer, 0, lidos);
        }
        return analisar(conteudo.toString());
    }

    static List<RegistroCsv> analisar(String texto) {
        List<RegistroCsv> registros = new ArrayList<>();
        int n = texto.length();
        int i = 0;
        int linha = 1;
        // Ignora BOM UTF-8, se presente
        if (n > 0 && texto.charAt(0) == '\uFEFF') {
            i = 1;
        }
        StringBuilder campo = new StringBuilder();
        while (i < n) {
            int linhaInicio = linha;
            List<String> campos = new ArrayList<>();
            String erro = null;
            while (true) {
                campo.setLength(0);
                if (i < n && texto.charAt(i) == '"') {
                    // Campo entre aspas
                    i++;
                    boolean fechado = false;
                    while (i < n) {
                        char c = texto.charAt(i);
                        if (c == '"') {
                            if (i + 1 < n && texto.charAt(i + 1) == '"') {
                                campo.append('"');
                                i += 2;
                            } else {
                                i++;
                                fechado = true;
                                break;
                            }
                        } else {
                            if (c == '\n') {
                                linha++;
                            }
                            campo.append(c);
                            i++;
                        }
                    }
                    if (!fechado) {
                        erro = "aspas não fechadas";
                    } else if (i < n && !fimDeCampo(texto.charAt(i))) {
                        erro = "caractere inesperado após aspas de fechamento";
                    }
                } else {
                    // Campo sem aspas
                    while (i < n) {
                        char c = texto.charAt(i);
                        if (fimDeCampo(c)) {
                            break;
                        }
                        if (c == '"' && erro == null) {
                            erro = "aspas em campo não delimitado por aspas";
                        }
                        campo.append(c);
                        i++;
                    }
                }
                campos.add(campo.toString());
                if (erro != null) {
                    // Descarta o restante da linha física com erro
                    while (i < n && texto.charAt(i) != '\n' && texto.charAt(i) != '\r') {
                        i++;
                    }
                    i = consumirQuebra(texto, i);
                    if (i <= n) {
                        linha++;
                    }
                    break;
                }
                if (i >= n) {
                    break;
                }
                if (texto.charAt(i) == ',') {
                    i++;
                    if (i >= n) {
                        // Vírgula final: último campo vazio
                        campos.add("");
                        break;
                    }
                    continue;
                }
                // Fim de registro (CR, LF ou CRLF)
                i = consumirQuebra(texto, i);
                linha++;
                break;
            }
            registros.add(new RegistroCsv(linhaInicio, List.copyOf(campos), erro));
        }
        return registros;
    }

    private static boolean fimDeCampo(char c) {
        return c == ',' || c == '\r' || c == '\n';
    }

    /** Avança sobre uma quebra de linha (CRLF, LF ou CR) a partir de {@code i}. */
    private static int consumirQuebra(String texto, int i) {
        int n = texto.length();
        if (i < n && texto.charAt(i) == '\r') {
            i++;
            if (i < n && texto.charAt(i) == '\n') {
                i++;
            }
        } else if (i < n && texto.charAt(i) == '\n') {
            i++;
        }
        return i;
    }
}
