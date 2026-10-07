package br.mp.mpf.sisgares.comumaws.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Extração do usuário autenticado e resposta 401.
 *
 * <p><b>Validates: Requirements 5.1, 5.3, 5.4</b>
 */
class ExtratorPrincipalTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2025-03-10T12:00:00Z"), ZoneId.of("America/Fortaleza"));
    private static final long AGORA = RELOGIO.instant().getEpochSecond();

    /** Mesmo formato de gerarJwtFicticio (frontend): cabeçalho.payload. com alg "none". */
    private static String jwtFicticio(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
                + "." + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".";
    }

    private static APIGatewayV2HTTPEvent comHeader(String autorizacao) {
        return APIGatewayV2HTTPEvent.builder().withHeaders(Map.of("authorization", autorizacao)).build();
    }

    @Test
    void modoMockDecodificaTokenFicticio() {
        String token = jwtFicticio("{\"sub\":\"u-1\",\"name\":\"Usuária Exemplo\",\"email\":\"u1@exemplo.gov.br\","
                + "\"cognito:groups\":[\"Solicitante\",\"Setor_Atendente\"],\"custom:unidade\":\"PR-CE\","
                + "\"custom:setor\":\"STI\",\"iss\":\"sisgares-mock\",\"exp\":" + (AGORA + 3600) + "}");

        Principal p = new ExtratorPrincipal(true, RELOGIO).extrair(comHeader("Bearer " + token));

        assertEquals(new Principal("u-1", "Usuária Exemplo", List.of("Solicitante", "Setor_Atendente"),
                "PR-CE", "STI"), p);
    }

    @Test
    void modoMockRejeitaTokenAusenteMalformadoOuExpirado() {
        ExtratorPrincipal extrator = new ExtratorPrincipal(true, RELOGIO);
        assertThrows(ExcecaoNaoAutenticado.class, () -> extrator.extrair(new APIGatewayV2HTTPEvent()));
        assertThrows(ExcecaoNaoAutenticado.class, () -> extrator.extrair(comHeader("Bearer abc")));
        assertThrows(ExcecaoNaoAutenticado.class, () -> extrator.extrair(comHeader("Bearer x.%%%.")));
        String expirado = jwtFicticio("{\"sub\":\"u-1\",\"exp\":" + AGORA + "}");
        assertThrows(ExcecaoNaoAutenticado.class, () -> extrator.extrair(comHeader("Bearer " + expirado)));
    }

    @Test
    void modoNormalLeClaimsDoAuthorizer() {
        var jwt = APIGatewayV2HTTPEvent.RequestContext.Authorizer.JWT.builder()
                .withClaims(Map.of("sub", "u-2", "email", "u2@exemplo.gov.br",
                        "cognito:groups", "[Administrador Solicitante]"))
                .build();
        var evento = APIGatewayV2HTTPEvent.builder()
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withAuthorizer(APIGatewayV2HTTPEvent.RequestContext.Authorizer.builder()
                                .withJwt(jwt).build())
                        .build())
                .build();

        Principal p = new ExtratorPrincipal(false, RELOGIO).extrair(evento);

        assertEquals("u-2", p.sub());
        assertEquals("u2@exemplo.gov.br", p.nome());
        assertEquals(List.of("Administrador", "Solicitante"), p.grupos());
    }

    @Test
    void modoNormalSemAuthorizerLancaNaoAutenticado() {
        assertThrows(ExcecaoNaoAutenticado.class,
                () -> new ExtratorPrincipal(false, RELOGIO).extrair(new APIGatewayV2HTTPEvent()));
    }

    @Test
    void mapeadorRespondeHttp401() {
        var resposta = new MapeadorErros(new LogEstruturado(RELOGIO, "teste"))
                .mapear(new ExcecaoNaoAutenticado("Token ausente."), "req-1");
        assertEquals(401, resposta.getStatusCode());
    }
}
