package br.mp.mpf.sisgares.reservas.dto;

import br.mp.mpf.sisgares.dominio.Status;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reserva devolvida pela API; compatível com a interface {@code Reserva} de
 * {@code frontend/src/app/features/reservas/reservas.service.ts}. IDs como String.
 *
 * @param id              RESE_ID
 * @param status          status calculado (RN13)
 * @param solicitanteNome nome do Solicitante; {@code null} quando o usuário não pode vê-lo (Req. 6.17)
 * @param ambienteId      AMBI_ID; {@code null} para Local_Proprio
 * @param complemento     complemento do ambiente
 * @param disposicaoId    DISP_ID opcional
 * @param finalidade      finalidade do evento
 * @param participantes   quantidade estimada de participantes
 * @param periodos        períodos da reserva
 * @param recursos        solicitações de recursos
 * @param ultimaAlteracao data/hora da última alteração (ISO-8601 local)
 * @param pedidosSnp      pedidos gerados no SNP simulado
 */
public record ReservaResposta(String id, Status status, String solicitanteNome, String ambienteId,
                              String complemento, String disposicaoId, String finalidade,
                              Integer participantes, List<PeriodoDto> periodos,
                              List<SolicitacaoDto> recursos, LocalDateTime ultimaAlteracao,
                              List<PedidoSnp> pedidosSnp) {

    public ReservaResposta {
        periodos = periodos == null ? List.of() : List.copyOf(periodos);
        recursos = recursos == null ? List.of() : List.copyOf(recursos);
        pedidosSnp = pedidosSnp == null ? List.of() : List.copyOf(pedidosSnp);
    }

    /**
     * Pedido no SNP simulado vinculado à Reserva.
     *
     * @param numero número do pedido
     * @param link   link para o pedido
     */
    public record PedidoSnp(String numero, String link) {
    }
}
