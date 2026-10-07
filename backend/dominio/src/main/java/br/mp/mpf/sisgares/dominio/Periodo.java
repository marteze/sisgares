package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Período semiaberto [início, término) no fuso {@link ConstantesDominio#ZONA}.
 *
 * <p>O construtor não rejeita término ≤ início: essa verificação é a regra RN1, aplicada pelo
 * Validador_Reserva para que a violação seja acumulada e devolvida ao usuário.
 *
 * @param id      identificador do período (PRES); {@code null} para período novo
 * @param inicio  instante inicial (incluso)
 * @param termino instante final (excluso)
 */
public record Periodo(Long id, LocalDateTime inicio, LocalDateTime termino) {

    public Periodo {
        Objects.requireNonNull(inicio, "inicio é obrigatório");
        Objects.requireNonNull(termino, "termino é obrigatório");
    }

    /** Cria um período novo, ainda sem identificador. */
    public static Periodo de(LocalDateTime inicio, LocalDateTime termino) {
        return new Periodo(null, inicio, termino);
    }

    /** Indica se o término é posterior ao início (RN1). */
    public boolean valido() {
        return termino.isAfter(inicio);
    }
}
