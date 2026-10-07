package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.dominio.Reserva;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Porta de dados do Cliente_SNP (somente GetItem/Query/PutItem; nunca altera a Reserva). */
public interface RepositorioSnp {

    /** Snapshot da Versão_Reserva {@code VERS#<numero>}. */
    Optional<Reserva> versao(long reseId, long numero);

    /** Estado atual da Reserva (usado quando a versão não existe). */
    Optional<Reserva> reservaAtual(long reseId);

    /** Vínculos EAMB do ambiente. */
    List<VinculoSnp> vinculosDoAmbiente(long ambienteId);

    /** Vínculos EREC do recurso. */
    List<VinculoSnp> vinculosDoRecurso(long recursoId);

    /** Chaves de vínculo com Pedido_SNP já registrado ({@code SNP#<vinculo>} com status REGISTRADO). */
    Set<String> vinculosRegistrados(long reseId);

    /** Grava {@code RESE#<id>/SNP#<vinculo>} com status REGISTRADO, número e link. */
    void gravarRegistro(PedidoSnp pedido, RespostaSnp resposta);

    /** Grava {@code RESE#<id>/SNP#<vinculo>} com status FALHA e motivo (Req. 14.5). */
    void gravarFalha(PedidoSnp pedido, String motivo);
}
