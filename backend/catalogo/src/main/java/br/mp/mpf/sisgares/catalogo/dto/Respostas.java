package br.mp.mpf.sisgares.catalogo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Respostas do catálogo. Ids em texto, como esperam os contratos do frontend
 * ({@code AmbienteCatalogo}, {@code RecursoCatalogo}, {@code DisposicaoCatalogo}, {@code AmbienteResumo}).
 */
public final class Respostas {

    private Respostas() {
    }

    /** Serve a {@code AmbienteCatalogo} e {@code AmbienteResumo} ({@code id}, {@code nome}, {@code ativo}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AmbienteResposta(String id, String nome, boolean ativo, String idPai, String unidade,
                                   String raizId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecursoResposta(String id, String descricao, boolean ativo, boolean limitado,
                                  Integer disponibilidade, String grupoId, String grupoNome, Integer grupoOrdem,
                                  List<String> ambientesPermitidos, String unidade, String icone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DisposicaoResposta(String id, String descricao, boolean ativo, String imagem, String alt) {
    }

    public record GrupoResposta(String id, String descricao, int ordem, boolean ativo) {
    }

    public record SetorResposta(String id, String descricao, String email, List<String> emailsAlternativos,
                                String unidade, boolean ativo) {
    }

    /** Vínculo de Setor_Envolvido (EAMB/EREC). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VinculoSetorResposta(String id, String setorId, String alvoId, String codServicoSnp) {
    }
}
