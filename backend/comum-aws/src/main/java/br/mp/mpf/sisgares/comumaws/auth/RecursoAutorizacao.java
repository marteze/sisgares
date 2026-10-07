package br.mp.mpf.sisgares.comumaws.auth;

import java.util.Objects;
import java.util.Set;

/**
 * Entidade {@code Sisgares::Reserva} avaliada pelo Autorizador.
 *
 * @param id      identificador da Reserva (ou um id sintético, ex.: painel)
 * @param dono    {@code sub} do Solicitante da Reserva; pode ser null (sem dono, ex.: painel)
 * @param unidade Unidade_Macro da Reserva; null é tratado como vazio
 * @param setores ids dos Setor_Envolvido do Ambiente e dos Recursos
 */
public record RecursoAutorizacao(String id, String dono, String unidade, Set<String> setores) {

    /** Id sintético usado no recurso do Painel do Atendente. */
    public static final String ID_PAINEL = "painel";

    public RecursoAutorizacao {
        Objects.requireNonNull(id, "id é obrigatório");
        unidade = unidade == null ? "" : unidade;
        setores = setores == null ? Set.of() : Set.copyOf(setores);
    }

    /**
     * Recurso que representa a visão do Painel do Atendente do próprio usuário: unidade do
     * principal e, se houver, o seu Setor_Envolvido. Sem dono.
     */
    public static RecursoAutorizacao painelDe(Principal p) {
        Set<String> setores = p.setor() == null || p.setor().isBlank() ? Set.of() : Set.of(p.setor().trim());
        return new RecursoAutorizacao(ID_PAINEL, null, p.unidade(), setores);
    }
}
