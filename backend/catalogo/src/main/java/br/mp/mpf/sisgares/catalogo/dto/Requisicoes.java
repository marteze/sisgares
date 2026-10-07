package br.mp.mpf.sisgares.catalogo.dto;

import java.util.List;

/**
 * Corpos de requisição do catálogo. Campos não previstos geram 422 {@code CAMPO_NAO_PERMITIDO}
 * (whitelist do {@code Json}); ids aceitam número ou texto numérico.
 */
public final class Requisicoes {

    private Requisicoes() {
    }

    /** Setor_Envolvido (Req. 7.2). */
    public record SetorRequisicao(String descricao, String unidade, String email, List<String> emailsAlternativos) {
    }

    /** Ambiente (Req. 7.4); {@code idPai} nulo torna o Ambiente raiz. */
    public record AmbienteRequisicao(String descricao, String unidade, Long idPai) {
    }

    /** Disposição (Req. 7.7); a imagem é enviada por {@code POST .../{id}/imagem}. */
    public record DisposicaoRequisicao(String descricao, String alt) {
    }

    /** Grupo_Recurso (Req. 7.9). */
    public record GrupoRequisicao(String descricao, Integer ordem) {
    }

    /** Recurso (Req. 7.11, 7.12); {@code unidade} nula indica todas as unidades. */
    public record RecursoRequisicao(String descricao, Long grupoId, String icone, String unidade,
                                    Boolean limitado, Integer disponibilidade) {
    }

    /** Imagem da Disposição em base64. */
    public record ImagemRequisicao(String conteudo) {
    }

    /** Vínculo EAMB/EREC (Req. 7.6, 7.13). */
    public record VinculoSetorRequisicao(Long setorId, String codServicoSnp) {
    }

    /** Vínculo VREC (Req. 7.13). */
    public record VinculoAmbienteRequisicao(Long ambienteId) {
    }
}
