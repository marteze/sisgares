package br.mp.mpf.sisgares.eventos.notificador;

/**
 * Controle de eventos processados em {@code EVT#<eventoId>} / {@code <consumidor>} (Req. 2.6).
 */
public interface RegistroIdempotencia {

    /**
     * Tenta iniciar o processamento do evento (Put com {@code attribute_not_exists(PK)}).
     *
     * @return {@code false} se o evento já foi concluído ou está em processamento por outra entrega
     */
    boolean iniciar(String eventoId);

    /** Marca o evento como concluído; novas entregas serão ignoradas. */
    void concluir(String eventoId);

    /** Marca o evento como falho, liberando nova tentativa do Step Functions. */
    void falhar(String eventoId);
}
