package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.comumaws.http.Json;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link GatewaySnp} via HTTP: POST JSON no endpoint da Configuração, com timeout de 5 s.
 * Envia o cabeçalho {@code Idempotency-Key} = {@code RESE#<id>/SNP#<vinculo>}.
 */
public final class GatewaySnpHttp implements GatewaySnp {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http;

    /** Cliente padrão, criado uma vez por contêiner. */
    public GatewaySnpHttp() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    public GatewaySnpHttp(HttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public RespostaSnp registrar(String endpoint, PedidoSnp pedido) {
        URI uri = validar(endpoint);
        HttpRequest requisicao = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("Idempotency-Key", pedido.chaveIdempotencia())
                .POST(HttpRequest.BodyPublishers.ofString(Json.escrever(pedido.comoMapa()), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resposta;
        try {
            resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new FalhaSnpException(FalhaSnpException.TIMEOUT, "SNP não respondeu em " + TIMEOUT.toSeconds() + " s.", e);
        } catch (IOException e) {
            throw new FalhaSnpException(FalhaSnpException.INDISPONIVEL, "SNP indisponível.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaSnpException(FalhaSnpException.INTERROMPIDO, "Envio ao SNP interrompido.", e);
        }
        int status = resposta.statusCode();
        if (status < 200 || status >= 300) {
            throw new FalhaSnpException("HTTP_" + status, "SNP respondeu HTTP " + status + ".");
        }
        return ler(resposta.body());
    }

    /** Aceita somente {@code http(s)://} com host; a regra de https em produção é da Configuração. */
    static URI validar(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new FalhaSnpException(FalhaSnpException.SEM_ENDPOINT, "Endpoint do SNP não configurado.");
        }
        try {
            URI uri = URI.create(endpoint.strip());
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!esquema.equals("https") && !esquema.equals("http")) || uri.getHost() == null) {
                throw new FalhaSnpException(FalhaSnpException.ENDPOINT_INVALIDO, "Endpoint do SNP inválido.");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new FalhaSnpException(FalhaSnpException.ENDPOINT_INVALIDO, "Endpoint do SNP inválido.", e);
        }
    }

    /** Lê {@code {numero, link}} do corpo da resposta. */
    static RespostaSnp ler(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            throw new FalhaSnpException(FalhaSnpException.RESPOSTA_INVALIDA, "Resposta do SNP vazia.");
        }
        try {
            JsonNode no = Json.mapper().readTree(corpo);
            return new RespostaSnp(no.path("numero").asText(null), no.path("link").asText(null));
        } catch (JsonProcessingException e) {
            throw new FalhaSnpException(FalhaSnpException.RESPOSTA_INVALIDA, "Resposta do SNP não é JSON válido.", e);
        }
    }
}
