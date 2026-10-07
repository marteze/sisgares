package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.dominio.Diferenca;
import java.util.List;
import java.util.Objects;

/**
 * Dados já resolvidos (descrições, não IDs) que compõem o e-mail (Req. 13.3, 13.4).
 *
 * <p>Os textos chegam crus; o escape HTML é responsabilidade do {@link MontadorEmail}.
 *
 * @param tipo          tipo do evento
 * @param reseId        número da reserva
 * @param ambiente      descrição do Ambiente ou complemento de Local_Proprio
 * @param finalidade    finalidade
 * @param participantes participantes (texto; vazio se ausente)
 * @param disposicao    descrição da Disposição (vazio se ausente)
 * @param periodos      Períodos formatados
 * @param recursos      Recursos com quantidades formatados
 * @param solicitante   identificação do Solicitante
 * @param pedidosSnp    números de Pedido_SNP
 * @param diferencas    diferenças em relação à versão anterior (somente alteração)
 */
public record ConteudoEmail(TipoEvento tipo, long reseId, String ambiente, String finalidade,
                            String participantes, String disposicao, List<String> periodos,
                            List<String> recursos, String solicitante, List<String> pedidosSnp,
                            List<Diferenca> diferencas) {

    public ConteudoEmail {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        periodos = periodos == null ? List.of() : List.copyOf(periodos);
        recursos = recursos == null ? List.of() : List.copyOf(recursos);
        pedidosSnp = pedidosSnp == null ? List.of() : List.copyOf(pedidosSnp);
        diferencas = diferencas == null ? List.of() : List.copyOf(diferencas);
    }
}
