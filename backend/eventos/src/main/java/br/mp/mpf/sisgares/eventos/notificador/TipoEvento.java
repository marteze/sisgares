package br.mp.mpf.sisgares.eventos.notificador;

import java.util.Arrays;

/**
 * Tipo do Evento_Reserva tratado pelo Notificador (Req. 13.3).
 */
public enum TipoEvento {
    CRIADA("ReservaCriada", "Inclusão", "inclusão"),
    ALTERADA("ReservaAlterada", "Alteração", "alteração"),
    CANCELADA("ReservaCancelada", "Cancelamento", "cancelamento");

    private final String nomeEvento;
    private final String rotulo;
    private final String rotuloMinusculo;

    TipoEvento(String nomeEvento, String rotulo, String rotuloMinusculo) {
        this.nomeEvento = nomeEvento;
        this.rotulo = rotulo;
        this.rotuloMinusculo = rotuloMinusculo;
    }

    /** Nome do evento no barramento (ex.: {@code ReservaAlterada}). */
    public String nomeEvento() {
        return nomeEvento;
    }

    /** Rótulo exibido no e-mail (ex.: "Alteração"). */
    public String rotulo() {
        return rotulo;
    }

    /** Rótulo em minúsculas, usado no assunto. */
    public String rotuloMinusculo() {
        return rotuloMinusculo;
    }

    /**
     * Converte o nome do evento ({@code ReservaCriada}) ou o nome da constante ({@code CRIADA}).
     *
     * @throws IllegalArgumentException se o tipo for desconhecido
     */
    public static TipoEvento de(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("tipo do evento é obrigatório");
        }
        String v = valor.strip();
        return Arrays.stream(values())
                .filter(t -> t.nomeEvento.equalsIgnoreCase(v) || t.name().equalsIgnoreCase(v))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tipo de evento desconhecido: " + v));
    }
}
