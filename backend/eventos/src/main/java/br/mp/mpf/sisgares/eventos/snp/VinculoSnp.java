package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import java.util.Objects;

/**
 * Vínculo EAMB/EREC envolvido numa Reserva, candidato a Pedido_SNP (Req. 14.1, 14.2).
 *
 * @param chave          chave do vínculo, ex.: {@code EAMB#<id>} ou {@code EREC#<id>}
 * @param codigoServico  código de serviço do SNP (opcional; sem código não há pedido)
 * @param envoId         Setor_Envolvido do vínculo
 * @param alvoId         Ambiente (EAMB) ou Recurso (EREC) vinculado
 */
public record VinculoSnp(String chave, String codigoServico, long envoId, long alvoId) {

    public VinculoSnp {
        Objects.requireNonNull(chave, "chave");
        if (chave.isBlank()) {
            throw new IllegalArgumentException("chave do vínculo vazia");
        }
        codigoServico = codigoServico == null || codigoServico.isBlank() ? null : codigoServico.strip();
    }

    /** Vínculo Setor–Ambiente (EAMB). */
    public static VinculoSnp doAmbiente(VinculoSetor v) {
        return new VinculoSnp(Chaves.EAMB + v.id(), v.codServicoSnp(), v.envoId(), v.alvoId());
    }

    /** Vínculo Setor–Recurso (EREC). */
    public static VinculoSnp doRecurso(VinculoSetor v) {
        return new VinculoSnp(Chaves.EREC + v.id(), v.codServicoSnp(), v.envoId(), v.alvoId());
    }

    /** Indica se o vínculo gera Pedido_SNP (RN11: somente com código de serviço). */
    public boolean temCodigo() {
        return codigoServico != null;
    }
}
