package br.mp.mpf.sisgares.eventos.notificador;

/**
 * Falha de envio de ao menos uma Notificação; propagada ao Step Functions para nova tentativa
 * (retry 3x com backoff) e, esgotadas as tentativas, DLQ. A falha já está registrada no {@code NOTI#}.
 */
public final class FalhaNotificacaoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FalhaNotificacaoException(String mensagem) {
        super(mensagem);
    }
}
