package br.mp.mpf.sisgares.assistente;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.assistente.RespostaProposta.RecursoProposto;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exemplos do saneamento da saída do modelo (Req. 18.4). */
class SaneadorPropostaTest {

    static final Catalogo CATALOGO = new Catalogo(
            List.of(new Catalogo.Item(12, "Auditório"), new Catalogo.Item(13, "Sala 1")),
            List.of(new Catalogo.Item(5, "Projetor"), new Catalogo.Item(7, "Café")));

    @Test
    void propostaValidaCercadaDeTextoEhAceitaIntegralmente() {
        String saida = """
                Claro! Segue:
                ```json
                {"ambienteId":12,"data":"2026-10-21","inicio":"14:00","termino":"15:00",
                 "participantes":30,"finalidade":"Reunião","recursos":[{"recursoId":5,"quantidade":1},
                 {"recursoId":7,"quantidade":30}]}
                ```""";

        RespostaProposta r = SaneadorProposta.sanear(saida, CATALOGO);

        assertThat(r.camposNaoPreenchidos()).isEmpty();
        assertThat(r.proposta().ambienteId()).isEqualTo(12L);
        assertThat(r.proposta().data()).isEqualTo("2026-10-21");
        assertThat(r.proposta().participantes()).isEqualTo(30);
        assertThat(r.proposta().recursos())
                .containsExactly(new RecursoProposto(5, 1), new RecursoProposto(7, 30));
    }

    @Test
    void idsForaDoCatalogoEFormatosInvalidosSaoDescartados() {
        String saida = """
                {"proposta":{"ambienteId":99,"data":"2026-02-30","inicio":"25:00","termino":"9h",
                 "participantes":0,"finalidade":"  ","recursos":[{"recursoId":5,"quantidade":2},
                 {"recursoId":42,"quantidade":1},{"recursoId":5,"quantidade":1}],
                 "solicitanteEmail":"fulano@exemplo.gov.br"}}""";

        RespostaProposta r = SaneadorProposta.sanear(saida, CATALOGO);

        assertThat(r.proposta().ambienteId()).isNull();
        assertThat(r.proposta().data()).isNull();
        assertThat(r.proposta().recursos()).containsExactly(new RecursoProposto(5, 2));
        assertThat(r.camposNaoPreenchidos()).containsExactly("ambienteId", "data", "inicio", "termino",
                "participantes", "finalidade", "recursos[1]", "recursos[2]");
    }

    @Test
    void terminoAnteriorAoInicioEhDescartado() {
        RespostaProposta r = SaneadorProposta.sanear(
                "{\"inicio\":\"15:00\",\"termino\":\"14:00\"}", CATALOGO);

        assertThat(r.proposta().inicio()).isEqualTo("15:00");
        assertThat(r.proposta().termino()).isNull();
        assertThat(r.camposNaoPreenchidos()).contains("termino").doesNotContain("inicio");
    }

    @Test
    void entradaMalformadaNaoLancaExcecaoEMarcaTodosOsCampos() {
        for (String saida : new String[] {null, "", "sem json", "{", "[1,2]", "{\"ambienteId\":", "{}"}) {
            RespostaProposta r = SaneadorProposta.sanear(saida, CATALOGO);
            assertThat(r.camposNaoPreenchidos()).containsExactlyElementsOf(SaneadorProposta.CAMPOS);
        }
    }
}
