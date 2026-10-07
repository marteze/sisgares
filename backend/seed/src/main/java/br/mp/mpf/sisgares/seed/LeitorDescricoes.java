package br.mp.mpf.sisgares.seed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extrai de {@code imagens/descricoes.md} o texto alternativo de cada Imagem_Ícone (Requisito 4.17).
 *
 * <p>Percorre as tabelas Markdown; em cada uma localiza pelo cabeçalho as colunas "Novo nome" e
 * "Texto alternativo" (as tabelas têm quantidades diferentes de colunas).
 */
final class LeitorDescricoes {

    private static final String COLUNA_NOME = "novo nome";
    private static final String COLUNA_ALT = "texto alternativo";

    private LeitorDescricoes() {
    }

    /** Mapa nome do arquivo → texto alternativo. Texto nulo ou vazio resulta em mapa vazio. */
    static Map<String, String> ler(String markdown) {
        Map<String, String> alts = new HashMap<>();
        if (markdown == null || markdown.isBlank()) {
            return alts;
        }
        int indiceNome = -1;
        int indiceAlt = -1;
        for (String bruta : markdown.split("\\R")) {
            String linha = bruta.strip();
            if (!linha.startsWith("|")) {
                // Fim da tabela: o próximo cabeçalho redefine as colunas
                indiceNome = -1;
                indiceAlt = -1;
                continue;
            }
            List<String> celulas = celulas(linha);
            if (indiceNome < 0 || indiceAlt < 0) {
                indiceNome = indice(celulas, COLUNA_NOME);
                indiceAlt = indice(celulas, COLUNA_ALT);
                continue;
            }
            if (linha.matches("\\|[\\s:|-]+\\|?")) {
                continue; // linha separadora |---|---|
            }
            if (celulas.size() <= Math.max(indiceNome, indiceAlt)) {
                continue;
            }
            String nome = limpar(celulas.get(indiceNome));
            String alt = limpar(celulas.get(indiceAlt));
            if (!nome.isEmpty() && !alt.isEmpty()) {
                alts.put(nome, alt);
            }
        }
        return alts;
    }

    private static List<String> celulas(String linha) {
        String miolo = linha.substring(1);
        if (miolo.endsWith("|")) {
            miolo = miolo.substring(0, miolo.length() - 1);
        }
        List<String> resultado = new ArrayList<>();
        for (String c : miolo.split("\\|", -1)) {
            resultado.add(c.strip());
        }
        return resultado;
    }

    private static int indice(List<String> celulas, String coluna) {
        for (int i = 0; i < celulas.size(); i++) {
            if (celulas.get(i).toLowerCase(java.util.Locale.ROOT).equals(coluna)) {
                return i;
            }
        }
        return -1;
    }

    /** Remove crases e espaços (nomes podem vir como {@code `arquivo.png`}). */
    private static String limpar(String valor) {
        return valor.replace("`", "").strip();
    }
}
