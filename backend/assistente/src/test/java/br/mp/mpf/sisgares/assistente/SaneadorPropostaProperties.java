package br.mp.mpf.sisgares.assistente;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.assistente.RespostaProposta.Proposta;
import br.mp.mpf.sisgares.assistente.RespostaProposta.RecursoProposto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propriedades do saneamento da saída do Assistente. */
class SaneadorPropostaProperties {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> CAMPOS_PROPOSTA = Set.copyOf(SaneadorProposta.CAMPOS);

    /**
     * Property 17: Saída do Assistente restrita ao catálogo. Para qualquer saída do modelo
     * (válida ou não), a proposta saneada obedece ao JSON Schema e contém só IDs do catálogo;
     * campos descartados ou ausentes aparecem em {@code camposNaoPreenchidos}.
     *
     * <p><b>Validates: Requirements 18.4, 22.4</b>
     */
    @Property(tries = 500)
    void saidaSaneadaObedeceAoSchemaERestritaAoCatalogo(@ForAll("saidas") String saida,
                                                        @ForAll("catalogos") Catalogo catalogo)
            throws Exception {
        RespostaProposta r = SaneadorProposta.sanear(saida, catalogo);
        Proposta p = r.proposta();

        // IDs somente do catálogo enviado
        if (p.ambienteId() != null) {
            assertThat(catalogo.idsAmbientes()).contains(p.ambienteId());
        }
        if (p.recursos() != null) {
            assertThat(p.recursos()).isNotEmpty();
            Set<Long> vistos = new HashSet<>();
            for (RecursoProposto rp : p.recursos()) {
                assertThat(catalogo.idsRecursos()).contains(rp.recursoId());
                assertThat(rp.quantidade()).isBetween(1, SaneadorProposta.QUANTIDADE_MAX);
                assertThat(vistos.add(rp.recursoId())).isTrue();
            }
        }
        // Formatos do schema
        if (p.data() != null) {
            assertThat(LocalDate.parse(p.data()).toString()).isEqualTo(p.data());
        }
        if (p.inicio() != null) {
            assertThat(p.inicio()).matches("([01]\\d|2[0-3]):[0-5]\\d");
        }
        if (p.termino() != null) {
            assertThat(p.termino()).matches("([01]\\d|2[0-3]):[0-5]\\d");
        }
        if (p.participantes() != null) {
            assertThat(p.participantes()).isBetween(1, 10_000);
        }
        if (p.finalidade() != null) {
            assertThat(p.finalidade()).isNotBlank().hasSizeLessThanOrEqualTo(2000);
        }
        // Cada campo do schema: preenchido XOR em camposNaoPreenchidos
        Map<String, Object> valores = new java.util.HashMap<>();
        valores.put("ambienteId", p.ambienteId());
        valores.put("data", p.data());
        valores.put("inicio", p.inicio());
        valores.put("termino", p.termino());
        valores.put("participantes", p.participantes());
        valores.put("finalidade", p.finalidade());
        valores.put("recursos", p.recursos());
        valores.forEach((campo, valor) ->
                assertThat(r.camposNaoPreenchidos().contains(campo)).isEqualTo(valor == null));
        // Serialização: additionalProperties:false
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(r));
        assertThat(iteravel(json.fieldNames())).containsOnly("proposta", "camposNaoPreenchidos");
        assertThat(iteravel(json.get("proposta").fieldNames())).isSubsetOf(CAMPOS_PROPOSTA);
    }

    private static List<String> iteravel(java.util.Iterator<String> it) {
        List<String> lista = new java.util.ArrayList<>();
        it.forEachRemaining(lista::add);
        return lista;
    }

    @Provide
    Arbitrary<Catalogo> catalogos() {
        Arbitrary<List<Catalogo.Item>> itens = Arbitraries.longs().between(1, 30)
                .map(id -> new Catalogo.Item(id, "Item " + id)).list().ofMaxSize(8);
        return Combinators.combine(itens, itens).as(Catalogo::new);
    }

    /** Strings arbitrárias e JSONs quase válidos (IDs dentro/fora do catálogo, formatos variados). */
    @Provide
    Arbitrary<String> saidas() {
        Arbitrary<String> id = Arbitraries.oneOf(Arbitraries.longs().between(-2, 40).map(String::valueOf),
                Arbitraries.of("\"7\"", "null", "1.5", "true", "\"abc\"", "[]", "{}"));
        Arbitrary<String> texto = Arbitraries.oneOf(
                Arbitraries.of("\"2026-10-21\"", "\"2026-02-30\"", "\"14:00\"", "\"23:59\"", "\"24:00\"",
                        "\"9h\"", "\"\"", "null", "30", "0", "10001", "\"Reunião\"", "-1"),
                Arbitraries.strings().ofMaxLength(20).map(s -> MAPPER.valueToTree(s).toString()));
        Arbitrary<String> recurso = Combinators.combine(id, id)
                .as((r, q) -> "{\"recursoId\":" + r + ",\"quantidade\":" + q + "}");
        Arbitrary<String> recursos = recurso.list().ofMaxSize(4).map(l -> "[" + String.join(",", l) + "]");
        Arbitrary<String> json = Combinators.combine(id, texto, texto, texto, texto, texto, recursos)
                .as((a, d, i, t, p, f, r) -> "{\"ambienteId\":" + a + ",\"data\":" + d + ",\"inicio\":" + i
                        + ",\"termino\":" + t + ",\"participantes\":" + p + ",\"finalidade\":" + f
                        + ",\"recursos\":" + r + ",\"extra\":1}");
        Arbitrary<String> cercado = Combinators.combine(Arbitraries.strings().ofMaxLength(10), json,
                Arbitraries.strings().ofMaxLength(10)).as((x, j, y) -> x + j + y);
        return Arbitraries.oneOf(Arbitraries.strings().ofMaxLength(200), json, cercado,
                json.map(j -> "{\"proposta\":" + j + "}"));
    }
}
