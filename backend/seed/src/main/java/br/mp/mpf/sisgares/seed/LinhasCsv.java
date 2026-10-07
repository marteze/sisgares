package br.mp.mpf.sisgares.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recupera o número da linha física de cada registro aceito pelo {@code LeitorCsv}, que devolve
 * apenas os registros (sem linha). Usado para citar a linha no relatório de ícones e de IDs
 * inexistentes.
 *
 * <p>Varre o texto respeitando aspas (campos com quebra de linha) e considera os mesmos registros
 * que o leitor: o primeiro não vazio é o cabeçalho; registros em branco são ignorados.
 */
final class LinhasCsv {

    private LinhasCsv() {
    }

    /**
     * Linhas (base 1) dos registros de dados aceitos, na mesma ordem de {@code ResultadoLeitura.registros()}.
     *
     * @param linhasRejeitadas linhas iniciais dos registros rejeitados pelo leitor
     */
    static List<Integer> linhasAceitas(String texto, Set<Integer> linhasRejeitadas) {
        List<Integer> inicios = iniciosRegistros(texto);
        List<Integer> aceitas = new ArrayList<>();
        // Descarta o cabeçalho (primeiro registro não vazio)
        for (int i = 1; i < inicios.size(); i++) {
            if (!linhasRejeitadas.contains(inicios.get(i))) {
                aceitas.add(inicios.get(i));
            }
        }
        return aceitas;
    }

    /** Linha inicial de cada registro não vazio. */
    private static List<Integer> iniciosRegistros(String texto) {
        List<Integer> inicios = new ArrayList<>();
        if (texto == null) {
            return inicios;
        }
        int linha = 1;
        int inicio = 1;
        boolean entreAspas = false;
        boolean temConteudo = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"') {
                entreAspas = !entreAspas; // "" (aspas escapadas) alterna duas vezes
                temConteudo = true;
            } else if (c == '\n') {
                if (!entreAspas) {
                    if (temConteudo) {
                        inicios.add(inicio);
                    }
                    temConteudo = false;
                    inicio = linha + 1;
                }
                linha++;
            } else if (c != '\r' && !Character.isWhitespace(c)) {
                temConteudo = true;
            }
        }
        if (temConteudo) {
            inicios.add(inicio);
        }
        return inicios;
    }
}
