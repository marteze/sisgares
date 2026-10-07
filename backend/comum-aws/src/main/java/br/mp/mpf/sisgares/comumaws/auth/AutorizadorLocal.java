package br.mp.mpf.sisgares.comumaws.auth;

import java.util.Objects;

/**
 * Reprodução em Java das políticas Cedar de {@code infra/cedar} para Reservas, usada quando
 * {@code POLICY_STORE_ID} está ausente (modo local e testes). Negação por padrão, como no Cedar.
 *
 * <ul>
 *   <li>{@code criar-reserva}: dono == principal e mesma unidade;</li>
 *   <li>{@code dono-reserva}: dono consulta, altera e cancela;</li>
 *   <li>{@code admin-reservas-unidade}: Administrador da mesma unidade consulta, altera, cancela e vê o painel;</li>
 *   <li>{@code atendente-setor}: Setor_Atendente com setor contido nos setores da Reserva e mesma
 *       unidade consulta e vê o painel.</li>
 * </ul>
 */
public final class AutorizadorLocal implements Autorizador {

    static final String GRUPO_ADMINISTRADOR = "Administrador";
    static final String GRUPO_ATENDENTE = "Setor_Atendente";

    @Override
    public boolean permitido(Principal p, Acao acao, RecursoAutorizacao r) {
        Objects.requireNonNull(p, "principal é obrigatório");
        Objects.requireNonNull(acao, "acao é obrigatória");
        Objects.requireNonNull(r, "recurso é obrigatório");
        boolean ehDono = p.sub().equals(r.dono());
        // No Cedar, unidade ausente do principal é representada como "" (atributo obrigatório)
        boolean mesmaUnidade = r.unidade().equals(p.unidade() == null ? "" : p.unidade());
        boolean admin = p.pertenceA(GRUPO_ADMINISTRADOR) && mesmaUnidade;
        boolean atendente = p.pertenceA(GRUPO_ATENDENTE) && p.setor() != null && !p.setor().isBlank()
                && r.setores().contains(p.setor().trim()) && mesmaUnidade;
        return switch (acao) {
            case CRIAR_RESERVA -> ehDono && mesmaUnidade;
            case CONSULTAR_RESERVA -> ehDono || admin || atendente;
            case ALTERAR_RESERVA, CANCELAR_RESERVA -> ehDono || admin;
            case VER_PAINEL_ATENDENTE -> admin || atendente;
        };
    }
}
