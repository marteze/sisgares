package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pedido enviado ao SNP para um vínculo com código de serviço (Req. 14.1, 14.3).
 * Não contém dados pessoais: apenas identificadores e o código de serviço.
 *
 * @param reseId        RESE_ID
 * @param vinculo       chave do vínculo ({@code EAMB#<id>} / {@code EREC#<id>})
 * @param codigoServico código de serviço do SNP
 * @param envoId        Setor_Envolvido
 * @param alvoId        Ambiente ou Recurso vinculado
 * @param ano           ano de referência do pedido (início da reserva)
 */
public record PedidoSnp(long reseId, String vinculo, String codigoServico, long envoId, long alvoId, int ano) {

    public PedidoSnp {
        Objects.requireNonNull(vinculo, "vinculo");
        Objects.requireNonNull(codigoServico, "codigoServico");
    }

    /** Chave de idempotência enviada ao SNP: {@code RESE#<id>/SNP#<vinculo>}. */
    public String chaveIdempotencia() {
        return Chaves.pkReserva(reseId) + "/" + Chaves.skPedidoSnp(vinculo);
    }

    /** Corpo JSON do pedido, com ordem estável dos campos. */
    public Map<String, Object> comoMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reseId", reseId);
        m.put("vinculo", vinculo);
        m.put("codigoServico", codigoServico);
        m.put("envoId", envoId);
        m.put("alvoId", alvoId);
        m.put("ano", ano);
        m.put("chaveIdempotencia", chaveIdempotencia());
        return m;
    }
}
