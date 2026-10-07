package br.mp.mpf.sisgares.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolve a referência de ícone do CSV (ex.: {@code disp_001.jpg}) para o arquivo real
 * da categoria no formato {@code <ref>_<desc>.<ext>} (ex.: {@code disp_001_auditorio.jpg}).
 * Requisitos 4.14, 4.15 e 4.16.
 */
public final class ResolvedorIcone {

    /** Categorias de ícone e respectivo arquivo padrão {@code indefinido_*}. */
    public enum Categoria {
        DISPOSICAO("indefinido_disposicao-nao-definida.jpg"),
        RECURSO("indefinido_recurso-nao-definido.png");

        private final String iconePadrao;

        Categoria(String iconePadrao) {
            this.iconePadrao = iconePadrao;
        }

        public String iconePadrao() {
            return iconePadrao;
        }
    }

    /**
     * Resolve o ícone.
     *
     * @param refCsv   referência do CSV, no formato {@code <prefixo>.<ext>}
     * @param cat      categoria do ícone (define o padrão {@code indefinido_*})
     * @param arquivos nomes dos arquivos disponíveis na categoria
     * @return nome resolvido com a referência original preservada; em caso de 0 ou mais de
     *         um correspondente, o ícone padrão da categoria com o motivo correspondente
     */
    public ResultadoIcone resolver(String refCsv, Categoria cat, Set<String> arquivos) {
        if (cat == null) {
            throw new IllegalArgumentException("Categoria do ícone é obrigatória.");
        }
        List<String> correspondentes = buscarCorrespondentes(refCsv, arquivos);
        if (correspondentes.size() == 1) {
            return new ResultadoIcone(correspondentes.get(0), refCsv, null);
        }
        ResultadoIcone.Motivo motivo = correspondentes.isEmpty()
                ? ResultadoIcone.Motivo.ICONE_NAO_ENCONTRADO
                : ResultadoIcone.Motivo.ICONE_AMBIGUO;
        return new ResultadoIcone(cat.iconePadrao(), refCsv, motivo);
    }

    /** Lista os arquivos {@code <prefixo>_<desc>.<ext>} com a mesma extensão da referência. */
    private static List<String> buscarCorrespondentes(String refCsv, Set<String> arquivos) {
        List<String> correspondentes = new ArrayList<>();
        if (refCsv == null || arquivos == null) {
            return correspondentes;
        }
        String ref = refCsv.trim();
        int pontoRef = ref.lastIndexOf('.');
        // Referência sem prefixo ou sem extensão não pode ser resolvida
        if (pontoRef <= 0 || pontoRef == ref.length() - 1) {
            return correspondentes;
        }
        // Exige o "_" logo após o prefixo para que "disp_001" não case com "disp_0010_x.jpg"
        String prefixo = ref.substring(0, pontoRef) + "_";
        String extensao = ref.substring(pontoRef + 1).toLowerCase(Locale.ROOT);

        for (String arquivo : arquivos) {
            if (arquivo == null || !arquivo.startsWith(prefixo)) {
                continue;
            }
            int ponto = arquivo.lastIndexOf('.');
            // A descrição (entre "_" e a extensão) não pode ser vazia
            if (ponto <= prefixo.length()) {
                continue;
            }
            // Extensão comparada sem diferenciar maiúsculas e minúsculas
            if (arquivo.substring(ponto + 1).toLowerCase(Locale.ROOT).equals(extensao)) {
                correspondentes.add(arquivo);
            }
        }
        return correspondentes;
    }
}
