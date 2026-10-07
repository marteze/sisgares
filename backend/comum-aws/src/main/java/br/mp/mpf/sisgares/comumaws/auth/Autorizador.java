package br.mp.mpf.sisgares.comumaws.auth;

import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;

/**
 * Autorização baseada nas políticas Cedar de {@code infra/cedar} (Req. 5.5, 5.6).
 *
 * <p>Implementações: {@link AutorizadorVerifiedPermissions} (AVP {@code IsAuthorized}) e
 * {@link AutorizadorLocal} (mesmas políticas em Java, para modo local e testes).
 */
public interface Autorizador {

    /** Variável de ambiente com o id do Policy Store do Verified Permissions. */
    String VAR_POLICY_STORE = "POLICY_STORE_ID";

    /** Indica se o principal pode executar a ação sobre o recurso. */
    boolean permitido(Principal principal, Acao acao, RecursoAutorizacao recurso);

    /** Exige a permissão; negação lança {@link ExcecaoAcessoNegado} (HTTP 403 {@code ACESSO_NEGADO}). */
    default void exigir(Principal principal, Acao acao, RecursoAutorizacao recurso) {
        if (!permitido(principal, acao, recurso)) {
            throw new ExcecaoAcessoNegado("Você não tem permissão para realizar esta operação.");
        }
    }

    /** AVP quando {@code POLICY_STORE_ID} está definido; caso contrário, {@link AutorizadorLocal}. */
    static Autorizador doAmbiente() {
        String policyStore = System.getenv(VAR_POLICY_STORE);
        return policyStore == null || policyStore.isBlank()
                ? new AutorizadorLocal()
                : AutorizadorVerifiedPermissions.padrao(policyStore.trim());
    }
}
