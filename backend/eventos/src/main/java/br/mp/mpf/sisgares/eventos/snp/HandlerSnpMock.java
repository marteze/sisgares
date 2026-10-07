package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.comumaws.http.Json;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lambda mock do SNP (Req. 14.3). Recebe evento HTTP (API Gateway / Function URL) com o
 * {@link PedidoSnp} em JSON e responde {@code {numero: "SNP-<ano>-<seq>", link: ".../pedidos/<numero>"}}.
 *
 * <p>Determinístico: {@code seq} vem do SHA-256 de {@code reseId|vinculo|codigoServico}, portanto o
 * mesmo pedido sempre gera o mesmo número (idempotente). Não persiste nada.
 */
public final class HandlerSnpMock implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    public static final String BASE_LINK = "https://snp-simulado.exemplo.gov.br/pedidos/";
    static final int MODULO_SEQ = 1_000_000;

    private final Clock clock;

    public HandlerSnpMock() {
        this(Clock.system(ZoneId.of("America/Fortaleza")));
    }

    public HandlerSnpMock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> evento, Context context) {
        String corpo = corpo(evento);
        if (corpo == null || corpo.isBlank()) {
            return resposta(400, Map.of("mensagem", "Corpo do pedido ausente: envie o JSON do pedido."));
        }
        JsonNode no;
        try {
            no = Json.mapper().readTree(corpo);
        } catch (JsonProcessingException e) {
            return resposta(400, Map.of("mensagem", "Corpo do pedido não é JSON válido."));
        }
        String reseId = no.path("reseId").asText("");
        String vinculo = no.path("vinculo").asText("");
        String codigo = no.path("codigoServico").asText("");
        if (reseId.isBlank() || vinculo.isBlank() || codigo.isBlank()) {
            return resposta(400, Map.of("mensagem", "Informe reseId, vinculo e codigoServico."));
        }
        int ano = no.path("ano").asInt(0);
        if (ano < 2000 || ano > 9999) {
            ano = LocalDate.now(clock).getYear();
        }
        String numero = numero(ano, reseId, vinculo, codigo);
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("numero", numero);
        saida.put("link", BASE_LINK + numero);
        return resposta(201, saida);
    }

    /** Número determinístico {@code SNP-<ano>-<seq de 6 dígitos>}. */
    static String numero(int ano, String reseId, String vinculo, String codigo) {
        byte[] hash = sha256(reseId + "|" + vinculo + "|" + codigo);
        long valor = ((hash[0] & 0xFFL) << 24) | ((hash[1] & 0xFFL) << 16) | ((hash[2] & 0xFFL) << 8) | (hash[3] & 0xFFL);
        return String.format("SNP-%04d-%06d", ano, valor % MODULO_SEQ);
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Corpo do evento HTTP, decodificando Base64 quando indicado. */
    static String corpo(Map<String, Object> evento) {
        if (evento == null || !(evento.get("body") instanceof String body)) {
            return null;
        }
        if (Boolean.TRUE.equals(evento.get("isBase64Encoded"))) {
            try {
                return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return body;
    }

    private static Map<String, Object> resposta(int status, Map<String, Object> corpo) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("statusCode", status);
        r.put("headers", Map.of("Content-Type", "application/json; charset=utf-8"));
        r.put("body", Json.escrever(corpo));
        r.put("isBase64Encoded", false);
        return r;
    }
}
