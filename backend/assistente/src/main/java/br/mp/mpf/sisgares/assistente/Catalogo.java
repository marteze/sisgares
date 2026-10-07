package br.mp.mpf.sisgares.assistente;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogos permitidos enviados ao modelo: apenas id e descrição de Ambientes e Recursos ativos
 * (Req. 18.2). Também é a referência do saneamento de IDs (Req. 18.4).
 */
public record Catalogo(List<Item> ambientes, List<Item> recursos) {

    /** Item do catálogo: somente id e descrição (sem dados pessoais). */
    public record Item(long id, String descricao) {
        public Item {
            Objects.requireNonNull(descricao, "descricao é obrigatória");
        }
    }

    public Catalogo {
        ambientes = ambientes == null ? List.of() : List.copyOf(ambientes);
        recursos = recursos == null ? List.of() : List.copyOf(recursos);
    }

    /** IDs de Ambientes do catálogo. */
    public Set<Long> idsAmbientes() {
        return ambientes.stream().map(Item::id).collect(Collectors.toUnmodifiableSet());
    }

    /** IDs de Recursos do catálogo. */
    public Set<Long> idsRecursos() {
        return recursos.stream().map(Item::id).collect(Collectors.toUnmodifiableSet());
    }
}
