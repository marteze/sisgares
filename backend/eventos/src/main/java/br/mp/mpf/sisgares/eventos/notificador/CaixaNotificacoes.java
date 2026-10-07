package br.mp.mpf.sisgares.eventos.notificador;

import java.util.Optional;

/**
 * Registro das Notificações ({@code NOTI#}), implementado pela {@link CaixaSimulada}.
 */
public interface CaixaNotificacoes {

    /** Grava (ou sobrescreve) a Notificação. */
    void registrar(Notificacao notificacao);

    /** Situação já registrada para o setor neste evento, se houver. */
    Optional<StatusNotificacao> status(long reseId, String eventoId, long envoId);
}
