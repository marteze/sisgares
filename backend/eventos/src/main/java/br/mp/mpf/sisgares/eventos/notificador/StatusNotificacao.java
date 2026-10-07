package br.mp.mpf.sisgares.eventos.notificador;

/** Situação de uma Notificação registrada na Caixa_Simulada (Req. 13.5–13.7). */
public enum StatusNotificacao {
    /** Enviada pelo Amazon SES. */
    ENVIADA,
    /** Registrada somente na Caixa_Simulada (SES em sandbox ou modo local). */
    SIMULADA,
    /** Envio falhou; a Reserva permanece gravada. */
    FALHA;

    /** Indica que a notificação já foi entregue e não deve ser reenviada. */
    public boolean concluida() {
        return this == ENVIADA || this == SIMULADA;
    }
}
