package br.mp.mpf.sisgares.comumaws.repositorio;

import java.util.Objects;

/**
 * Usuário (Req. 3.5): {@code PK=USER#<sub>}, {@code SK=META}.
 *
 * @param sub     identificador do Cognito (não é dado pessoal exibível)
 * @param perfil  perfil de acesso
 * @param unidade Unidade_Macro do usuário
 * @param envoId  Setor_Envolvido vinculado (opcional; usado pelo Atendente)
 */
public record Usuario(String sub, Perfil perfil, String unidade, Long envoId) {
    public Usuario {
        Objects.requireNonNull(sub, "sub é obrigatório");
        Objects.requireNonNull(perfil, "perfil é obrigatório");
        Objects.requireNonNull(unidade, "unidade é obrigatória");
    }
}
