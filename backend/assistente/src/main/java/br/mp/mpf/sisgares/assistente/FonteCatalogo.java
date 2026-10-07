package br.mp.mpf.sisgares.assistente;

import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import java.util.Objects;

/** Fonte dos catálogos enviados ao modelo. */
@FunctionalInterface
public interface FonteCatalogo {

    /** Ambientes e Recursos ativos, apenas com id e descrição. */
    Catalogo carregar();

    /** Implementação de produção sobre o {@link RepositorioCatalogo} (Query nas partições índice). */
    static FonteCatalogo de(RepositorioCatalogo repositorio) {
        Objects.requireNonNull(repositorio, "repositorio");
        return () -> new Catalogo(
                repositorio.listarAmbientes().stream()
                        .map(r -> r.ambiente())
                        .filter(a -> a.ativo())
                        .map(a -> new Catalogo.Item(a.id(), a.descricao()))
                        .toList(),
                repositorio.listarRecursos().stream()
                        .map(r -> r.recurso())
                        .filter(r -> r.ativo())
                        .map(r -> new Catalogo.Item(r.id(), r.descricao()))
                        .toList());
    }
}
