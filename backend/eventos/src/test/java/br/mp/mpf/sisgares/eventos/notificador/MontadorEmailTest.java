package br.mp.mpf.sisgares.eventos.notificador;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.dominio.Diferenca;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Montagem do e-mail: escape HTML e destaque das diferenças.
 *
 * <p><b>Validates: Requirements 13.3, 13.4</b>
 */
class MontadorEmailTest {

    @Test
    void alteracaoDestacaValorAntigoENovo() {
        ConteudoEmail c = new ConteudoEmail(TipoEvento.ALTERADA, 7, "Auditório", "Reunião", "10", "",
                List.of("01/03/2030 15:00 a 01/03/2030 16:00"), List.of("Projetor: 1"), "sub-ficticio",
                List.of(), List.of(new Diferenca("Períodos", "14:00", "15:00")));

        Email email = MontadorEmail.montar(c);

        assertThat(email.assunto()).isEqualTo("SISGARES: alteração da reserva nº 7");
        assertThat(email.html())
                .contains("<del style=\"" + MontadorEmail.ESTILO_ANTIGO + "\">14:00</del>")
                .contains("<ins style=\"" + MontadorEmail.ESTILO_NOVO + "\">15:00</ins>");
    }

    @Test
    void textoDoUsuarioEhEscapado() {
        ConteudoEmail c = new ConteudoEmail(TipoEvento.CRIADA, 1, "Sala", "<script>alert('x')</script>",
                "", "", List.of(), List.of(), "sub", List.of(), List.of());

        String html = MontadorEmail.montar(c).html();

        assertThat(html).doesNotContain("<script>")
                .contains("&lt;script&gt;alert(&#x27;x&#x27;)&lt;&#x2F;script&gt;")
                .doesNotContain("<del").doesNotContain("<ins");
    }
}
