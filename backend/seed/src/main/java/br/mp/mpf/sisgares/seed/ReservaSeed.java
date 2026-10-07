package br.mp.mpf.sisgares.seed;

import br.mp.mpf.sisgares.dominio.Reserva;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reserva gerada pelo seed, com os dados necessários para gravar os itens filhos.
 *
 * @param reserva       Reserva do domínio (Períodos com PRES_ID originais)
 * @param raizId        raiz da árvore do Ambiente; {@code null} em Local_Proprio
 * @param setores       ENVO_IDs envolvidos (Ambiente e Recursos), para o GSI2
 * @param solicitacoes  solicitações com SOLI_ID original e quantidade possivelmente ausente
 */
public record ReservaSeed(Reserva reserva, Long raizId, Set<Long> setores, List<SolicitacaoSeed> solicitacoes) {

    public ReservaSeed {
        Objects.requireNonNull(reserva, "reserva é obrigatória");
        setores = setores == null ? Set.of() : Set.copyOf(setores);
        solicitacoes = solicitacoes == null ? List.of() : List.copyOf(solicitacoes);
    }

    /**
     * Linha SOLI preservada.
     *
     * @param quantidade {@code null} quando SOLI_QTD veio vazio para Recurso não limitado (Requisito 4.5)
     */
    public record SolicitacaoSeed(long soliId, long recursoId, Integer quantidade) {
    }
}
