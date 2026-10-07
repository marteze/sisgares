package br.mp.mpf.sisgares.comumaws.auth;

/** Ações do schema Cedar ({@code Sisgares::Action}) avaliadas sobre Reservas (Req. 5.5, 5.6). */
public enum Acao {
    CRIAR_RESERVA("CriarReserva"),
    CONSULTAR_RESERVA("ConsultarReserva"),
    ALTERAR_RESERVA("AlterarReserva"),
    CANCELAR_RESERVA("CancelarReserva"),
    VER_PAINEL_ATENDENTE("VerPainelAtendente");

    private final String idCedar;

    Acao(String idCedar) {
        this.idCedar = idCedar;
    }

    /** Identificador da ação no schema Cedar (ex.: {@code CriarReserva}). */
    public String idCedar() {
        return idCedar;
    }
}
