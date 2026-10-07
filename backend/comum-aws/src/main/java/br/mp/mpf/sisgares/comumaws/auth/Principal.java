package br.mp.mpf.sisgares.comumaws.auth;

import java.util.List;
import java.util.Objects;

/**
 * Usuário autenticado extraído do JWT.
 *
 * @param sub     identificador do usuário no Cognito (claim {@code sub})
 * @param nome    nome de exibição (claim {@code name}, ou {@code email}); não registrar em log sem mascarar
 * @param grupos  perfis (claim {@code cognito:groups})
 * @param unidade Unidade_Macro (claim {@code custom:unidade}); pode ser null
 * @param setor   Setor_Envolvido (claim {@code custom:setor}); pode ser null
 */
public record Principal(String sub, String nome, List<String> grupos, String unidade, String setor) {

    public Principal {
        Objects.requireNonNull(sub, "sub é obrigatório");
        grupos = grupos == null ? List.of() : List.copyOf(grupos);
    }

    /** Indica se o usuário pertence ao grupo informado. */
    public boolean pertenceA(String grupo) {
        return grupos.contains(grupo);
    }
}
