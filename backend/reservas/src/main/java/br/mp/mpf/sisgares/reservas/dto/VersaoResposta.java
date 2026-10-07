package br.mp.mpf.sisgares.reservas.dto;

import br.mp.mpf.sisgares.dominio.Diferenca;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Item de GET {@code /api/reservas/{id}/versoes}.
 *
 * @param numero      número da Versão_Reserva (1 = inclusão)
 * @param alteradaEm  data/hora da versão
 * @param reserva     snapshot da Reserva na versão
 * @param diferencas  diferenças em relação à versão anterior (vazio na primeira)
 */
public record VersaoResposta(long numero, LocalDateTime alteradaEm, ReservaResposta reserva,
                             List<Diferenca> diferencas) {

    public VersaoResposta {
        diferencas = diferencas == null ? List.of() : List.copyOf(diferencas);
    }
}
