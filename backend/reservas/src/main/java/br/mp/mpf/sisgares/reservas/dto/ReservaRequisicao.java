package br.mp.mpf.sisgares.reservas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo de POST/PUT {@code /api/reservas} (whitelist: somente estes campos são aceitos, Req. 6.3/6.4).
 *
 * <p>Os limites de tamanho e faixa são verificados por Bean Validation. A obrigatoriedade de
 * finalidade, participantes, complemento e períodos (RN1/RN2) fica no Validador_Reserva do domínio,
 * que acumula todas as violações.
 *
 * @param ambienteId    AMBI_ID como String; {@code null} indica Local_Proprio
 * @param complemento   complemento do ambiente (até 200 caracteres)
 * @param disposicaoId  DISP_ID opcional como String
 * @param finalidade    finalidade do evento (até 2000 caracteres)
 * @param participantes quantidade estimada de participantes (1 a 10000)
 * @param periodos      períodos da reserva
 * @param recursos      solicitações de recursos
 */
public record ReservaRequisicao(
        @Pattern(regexp = ValidadorEntrada.REGEX_ID, message = ValidadorEntrada.MSG_ID) String ambienteId,
        @Size(max = 200, message = "O complemento deve ter no máximo {max} caracteres.") String complemento,
        @Pattern(regexp = ValidadorEntrada.REGEX_ID, message = ValidadorEntrada.MSG_ID) String disposicaoId,
        @Size(max = 2000, message = "A finalidade deve ter no máximo {max} caracteres.") String finalidade,
        @Min(value = 1, message = "Informe ao menos {value} participante.")
        @Max(value = 10_000, message = "Informe no máximo {value} participantes.") Integer participantes,
        List<@NotNull(message = "Período vazio não é permitido.") @Valid PeriodoDto> periodos,
        List<@NotNull(message = "Recurso vazio não é permitido.") @Valid SolicitacaoDto> recursos) {
}
