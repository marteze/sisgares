package br.mp.mpf.sisgares.reservas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Corpo do PUT {@code /api/reservas/{id}}.
 *
 * <p>O frontend reenvia a Reserva recebida no GET, com campos somente leitura. Eles são ignorados
 * explicitamente; qualquer outro campo desconhecido continua gerando 422 {@code CAMPO_NAO_PERMITIDO}
 * (whitelist, Req. 6.3/6.4). As restrições de Bean Validation são aplicadas ao converter em
 * {@link ReservaRequisicao}.
 */
@JsonIgnoreProperties(value = {"id", "status", "solicitanteNome", "ultimaAlteracao", "pedidosSnp"})
public record ReservaAlteracaoRequisicao(String ambienteId, String complemento, String disposicaoId,
                                         String finalidade, Integer participantes, List<PeriodoDto> periodos,
                                         List<SolicitacaoDto> recursos) {

    /** Converte no DTO validado de criação/alteração. */
    public ReservaRequisicao paraRequisicao() {
        return new ReservaRequisicao(ambienteId, complemento, disposicaoId, finalidade, participantes,
                periodos, recursos);
    }
}
