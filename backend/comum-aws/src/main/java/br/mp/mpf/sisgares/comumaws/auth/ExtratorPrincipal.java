package br.mp.mpf.sisgares.comumaws.auth;

import br.mp.mpf.sisgares.comumaws.http.Json;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Extrai o {@link Principal} de uma requisição HTTP API (Req. 5.1, 5.3, 5.4).
 *
 * <ul>
 *   <li>Modo normal: lê {@code requestContext.authorizer.jwt.claims}, já validadas
 *       (assinatura, emissor, audiência e expiração) pelo authorizer JWT do API Gateway.</li>
 *   <li>Modo local ({@code MODO_AUTH=mock}): decodifica o JWT NÃO assinado do header
 *       {@code Authorization}, no formato gerado por {@code frontend/src/app/core/auth/jwt.ts}
 *       ({@code base64url(cabeçalho).base64url(payload).}) e verifica {@code exp}.
 *       Nunca ative esse modo em ambientes AWS.</li>
 * </ul>
 *
 * <p>Sem token ou com token inválido lança {@link ExcecaoNaoAutenticado} (HTTP 401).
 */
public final class ExtratorPrincipal {

    public static final String VARIAVEL_MODO = "MODO_AUTH";
    public static final String MODO_MOCK = "mock";

    static final String CLAIM_SUB = "sub";
    static final String CLAIM_NOME = "name";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_GRUPOS = "cognito:groups";
    static final String CLAIM_UNIDADE = "custom:unidade";
    static final String CLAIM_SETOR = "custom:setor";
    static final String CLAIM_EXP = "exp";

    private static final String PREFIXO_BEARER = "bearer ";

    private final boolean modoMock;
    private final Clock relogio;

    /**
     * @param modoMock true para decodificar o JWT fictício do header (somente execução local)
     * @param relogio  relógio usado para checar {@code exp} no modo mock
     */
    public ExtratorPrincipal(boolean modoMock, Clock relogio) {
        this.modoMock = modoMock;
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    /** Cria o extrator conforme a variável de ambiente {@code MODO_AUTH}. */
    public static ExtratorPrincipal doAmbiente(Clock relogio) {
        return new ExtratorPrincipal(MODO_MOCK.equalsIgnoreCase(System.getenv(VARIAVEL_MODO)), relogio);
    }

    /** Extrai o usuário autenticado ou lança {@link ExcecaoNaoAutenticado}. */
    public Principal extrair(APIGatewayV2HTTPEvent evento) {
        if (evento == null) {
            throw new ExcecaoNaoAutenticado("Requisição sem contexto de autenticação.");
        }
        Map<String, Object> claims = modoMock ? claimsDoTokenFicticio(evento) : claimsDoAuthorizer(evento);
        return montar(claims);
    }

    /** Claims validadas pelo authorizer JWT do API Gateway. */
    private static Map<String, Object> claimsDoAuthorizer(APIGatewayV2HTTPEvent evento) {
        var contexto = evento.getRequestContext();
        if (contexto == null || contexto.getAuthorizer() == null || contexto.getAuthorizer().getJwt() == null
                || contexto.getAuthorizer().getJwt().getClaims() == null) {
            throw new ExcecaoNaoAutenticado("Token ausente.");
        }
        return new HashMap<>(contexto.getAuthorizer().getJwt().getClaims());
    }

    /** Decodifica o payload do JWT fictício (alg "none") do header Authorization. */
    private Map<String, Object> claimsDoTokenFicticio(APIGatewayV2HTTPEvent evento) {
        String token = tokenBearer(evento.getHeaders());
        String[] partes = token.split("\\.", -1);
        if (partes.length < 2 || partes[1].isEmpty()) {
            throw new ExcecaoNaoAutenticado("Token malformado.");
        }
        JsonNode payload;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(partes[1]);
            payload = Json.mapper().readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Não propaga o conteúdo do token (pode conter dados pessoais)
            throw new ExcecaoNaoAutenticado("Token malformado.");
        }
        if (payload == null || !payload.isObject()) {
            throw new ExcecaoNaoAutenticado("Token malformado.");
        }
        JsonNode exp = payload.get(CLAIM_EXP);
        if (exp != null && exp.isNumber() && exp.asLong() * 1000 <= relogio.millis()) {
            throw new ExcecaoNaoAutenticado("Token expirado.");
        }
        Map<String, Object> claims = new HashMap<>();
        payload.fields().forEachRemaining(campo -> {
            JsonNode valor = campo.getValue();
            if (valor.isArray()) {
                List<String> itens = new ArrayList<>();
                valor.forEach(item -> itens.add(item.asText()));
                claims.put(campo.getKey(), itens);
            } else if (!valor.isNull()) {
                claims.put(campo.getKey(), valor.asText());
            }
        });
        return claims;
    }

    /** Obtém o token do header Authorization (nome sem distinção de caixa, prefixo Bearer). */
    private static String tokenBearer(Map<String, String> cabecalhos) {
        String valor = null;
        if (cabecalhos != null) {
            for (Map.Entry<String, String> c : cabecalhos.entrySet()) {
                if ("authorization".equalsIgnoreCase(c.getKey())) {
                    valor = c.getValue();
                    break;
                }
            }
        }
        if (valor == null || valor.isBlank()) {
            throw new ExcecaoNaoAutenticado("Token ausente.");
        }
        String texto = valor.trim();
        if (texto.regionMatches(true, 0, PREFIXO_BEARER, 0, PREFIXO_BEARER.length())) {
            texto = texto.substring(PREFIXO_BEARER.length()).trim();
        }
        if (texto.isEmpty()) {
            throw new ExcecaoNaoAutenticado("Token ausente.");
        }
        return texto;
    }

    private static Principal montar(Map<String, Object> claims) {
        String sub = texto(claims.get(CLAIM_SUB));
        if (sub == null) {
            throw new ExcecaoNaoAutenticado("Token sem identificação do usuário.");
        }
        String nome = texto(claims.get(CLAIM_NOME));
        if (nome == null) {
            nome = texto(claims.get(CLAIM_EMAIL));
        }
        return new Principal(sub, nome, grupos(claims.get(CLAIM_GRUPOS)),
                texto(claims.get(CLAIM_UNIDADE)), texto(claims.get(CLAIM_SETOR)));
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = valor.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Normaliza {@code cognito:groups}: lista (modo mock) ou texto. No authorizer da HTTP API
     * a claim chega como {@code "[Administrador Solicitante]"}; também aceita vírgulas.
     */
    static List<String> grupos(Object valor) {
        if (valor == null) {
            return List.of();
        }
        if (valor instanceof List<?> lista) {
            return lista.stream().filter(Objects::nonNull).map(Object::toString)
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String s = valor.toString().trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        return Arrays.stream(s.split("[,\\s]+"))
                .map(g -> g.replace("\"", "").trim())
                .filter(g -> !g.isEmpty())
                .toList();
    }
}
