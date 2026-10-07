package br.mp.mpf.sisgares.assistente;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Saída do Assistente conforme o JSON Schema do design ({@code additionalProperties:false}):
 * {@code {"proposta":{...},"camposNaoPreenchidos":[...]}}. Campos nulos são omitidos.
 */
public record RespostaProposta(Proposta proposta, List<String> camposNaoPreenchidos) {

    public RespostaProposta {
        camposNaoPreenchidos = camposNaoPreenchidos == null ? List.of() : List.copyOf(camposNaoPreenchidos);
    }

    /** Proposta de preenchimento do formulário; nunca é gravada como Reserva (Req. 18.6). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Proposta(Long ambienteId, String data, String inicio, String termino,
                           Integer participantes, String finalidade, List<RecursoProposto> recursos) {
        public Proposta {
            recursos = recursos == null ? null : List.copyOf(recursos);
        }
    }

    /** Recurso proposto com quantidade. */
    public record RecursoProposto(long recursoId, int quantidade) {
    }
}
