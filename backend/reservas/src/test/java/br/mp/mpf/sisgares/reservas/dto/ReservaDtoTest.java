package br.mp.mpf.sisgares.reservas.dto;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Status;
import br.mp.mpf.sisgares.dominio.Violacao;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes dos DTOs de reserva: whitelist, limites de Bean Validation e conversão.
 *
 * <p><b>Validates: Requirements 6.3, 6.4</b>
 */
class ReservaDtoTest {

    private static final Principal PRINCIPAL =
            new Principal("sub-ficticio-1", "Usuário Fictício", List.of("SOLICITANTE"), "PR/CE", null);
    private static final LocalDateTime INICIO = LocalDateTime.of(2030, 3, 10, 9, 0);

    private static ReservaRequisicao requisicao(String finalidade, String complemento, Integer participantes) {
        return new ReservaRequisicao("10", complemento, null, finalidade, participantes,
                List.of(new PeriodoDto(null, INICIO, INICIO.plusHours(2))),
                List.of(new SolicitacaoDto("7", null)));
    }

    @Test
    void campoForaDaWhitelistGeraUnrecognizedProperty() {
        String json = """
                {"ambienteId":"10","finalidade":"Reunião","participantes":5,"periodos":[],"recursos":[],
                 "solicitante":"outro"}""";
        assertThatThrownBy(() -> Json.ler(json, ReservaRequisicao.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void jsonValidoComDatasIsoEhDesserializado() throws Exception {
        String json = """
                {"ambienteId":"10","finalidade":"Reunião","participantes":5,
                 "periodos":[{"inicio":"2030-03-10T09:00","termino":"2030-03-10T11:00"}],
                 "recursos":[{"recursoId":"7","quantidade":2}]}""";
        ReservaRequisicao req = Json.ler(json, ReservaRequisicao.class);
        assertThat(req.periodos().get(0).inicio()).isEqualTo(INICIO);
        assertThat(ValidadorEntrada.violacoes(req)).isEmpty();
    }

    @Test
    void limitesNasFronteirasSaoAceitos() {
        assertThat(ValidadorEntrada.violacoes(requisicao("a".repeat(2000), "b".repeat(200), 1))).isEmpty();
        assertThat(ValidadorEntrada.violacoes(requisicao("x", null, 10_000))).isEmpty();
    }

    @Test
    void limitesExcedidosGeramUmaViolacaoPorCampo() {
        List<Violacao> v = ValidadorEntrada.violacoes(requisicao("a".repeat(2001), "b".repeat(201), 0));
        assertThat(v).extracting(Violacao::campo)
                .containsExactly("complemento", "finalidade", "participantes");
        assertThat(v).allMatch(x -> x.codigo().equals(ValidadorEntrada.CAMPO_INVALIDO));
        assertThat(ValidadorEntrada.violacoes(requisicao("x", null, 10_001)))
                .extracting(Violacao::campo).containsExactly("participantes");
    }

    @Test
    void idNaoNumericoEPeriodoSemInicioSaoRejeitados() {
        ReservaRequisicao req = new ReservaRequisicao("abc", null, null, "x", 1,
                List.of(new PeriodoDto(null, null, INICIO)), List.of());
        assertThatThrownBy(() -> ValidadorEntrada.validar(req))
                .isInstanceOf(ExcecaoValidacao.class)
                .satisfies(e -> assertThat(((ExcecaoValidacao) e).violacoes())
                        .extracting(Violacao::campo).containsExactly("ambienteId", "periodos[0].inicio"));
    }

    @Test
    void conversaoUsaPrincipalEIdaEVoltaPreservaCampos() {
        Reserva r = ConversorReserva.paraDominio(requisicao("Reunião", null, 5), PRINCIPAL);
        assertThat(r.solicitante()).isEqualTo("sub-ficticio-1");
        assertThat(r.unidade()).isEqualTo("PR/CE");
        assertThat(r.ambienteId()).isEqualTo(10L);
        assertThat(r.solicitacoes().get(0).quantidade()).isEqualTo(ConversorReserva.QUANTIDADE_PADRAO);

        ReservaResposta resp = ConversorReserva.paraResposta(r, Status.PREVISTA, null, null);
        assertThat(resp.ambienteId()).isEqualTo("10");
        assertThat(resp.recursos().get(0).recursoId()).isEqualTo("7");
        assertThat(resp.pedidosSnp()).isEmpty();
        // IDs serializados como String e datas em ISO-8601
        assertThat(Json.escrever(resp)).contains("\"ambienteId\":\"10\"").contains("\"inicio\":\"2030-03-10T09:00");
    }
}
