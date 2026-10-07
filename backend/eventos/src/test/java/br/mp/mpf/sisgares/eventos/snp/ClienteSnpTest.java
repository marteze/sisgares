package br.mp.mpf.sisgares.eventos.snp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Seleção de vínculos do Cliente_SNP e Lambda mock do SNP.
 *
 * <p><b>Validates: Requirements 14.1, 14.2, 14.3, 14.4</b>
 */
class ClienteSnpTest {

    private static final VinculoSnp TI_PROJETOR = new VinculoSnp("EREC#1", "SRV-TI", 10, 100);
    private static final VinculoSnp COPA_AGUA = new VinculoSnp("EREC#2", null, 20, 200);
    private static final VinculoSnp SALA = new VinculoSnp("EAMB#3", "SRV-SALA", 30, 300);

    @Test
    void rn11VinculoSemCodigoNaoGeraPedido() {
        assertThat(ClienteSnp.selecionar(List.of(TI_PROJETOR, COPA_AGUA), List.of(), Set.of()))
                .containsExactly(TI_PROJETOR);
    }

    @Test
    void alteracaoGeraPedidoApenasParaVinculoNovo() {
        assertThat(ClienteSnp.selecionar(List.of(TI_PROJETOR, SALA), List.of(TI_PROJETOR), Set.of()))
                .containsExactly(SALA);
    }

    @Test
    void vinculoJaRegistradoEDuplicadoSaoIgnorados() {
        assertThat(ClienteSnp.selecionar(List.of(SALA, TI_PROJETOR, TI_PROJETOR), List.of(), Set.of("EAMB#3")))
                .containsExactly(TI_PROJETOR);
    }

    @Test
    void mockGeraNumeroDeterministicoELink() {
        HandlerSnpMock mock = new HandlerSnpMock(Clock.fixed(Instant.parse("2025-05-01T12:00:00Z"), ZoneId.of("UTC")));
        PedidoSnp pedido = new PedidoSnp(7, "EREC#1", "SRV-TI", 10, 100, 2025);
        Map<String, Object> evento = Map.of("body", br.mp.mpf.sisgares.comumaws.http.Json.escrever(pedido.comoMapa()));

        Map<String, Object> r1 = mock.handleRequest(evento, null);
        Map<String, Object> r2 = mock.handleRequest(evento, null);

        assertThat(r1.get("statusCode")).isEqualTo(201);
        assertThat(r1).isEqualTo(r2);
        RespostaSnp resposta = GatewaySnpHttp.ler((String) r1.get("body"));
        assertThat(resposta.numero()).matches("SNP-2025-\\d{6}");
        assertThat(resposta.link()).isEqualTo(HandlerSnpMock.BASE_LINK + resposta.numero());
    }

    @Test
    void mockRejeitaCorpoAusente() {
        assertThat(new HandlerSnpMock().handleRequest(Map.of(), null).get("statusCode")).isEqualTo(400);
    }

    @Test
    void endpointInvalidoGeraFalhaComMotivo() {
        assertThatThrownBy(() -> GatewaySnpHttp.validar("ftp://snp.exemplo.gov.br"))
                .isInstanceOf(FalhaSnpException.class)
                .extracting(e -> ((FalhaSnpException) e).motivo())
                .isEqualTo(FalhaSnpException.ENDPOINT_INVALIDO);
    }
}
