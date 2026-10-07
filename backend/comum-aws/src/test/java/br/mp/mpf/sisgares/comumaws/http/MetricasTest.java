package br.mp.mpf.sisgares.comumaws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

/** Testes do formato EMF das métricas de negócio. */
class MetricasTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2025-03-10T12:00:00Z"), ZoneId.of("America/Fortaleza"));

    /** Conflito RN5 gera linha EMF com namespace, dimensão Regra e valor 1. **Validates: Requirements 21.2** */
    @Test
    void conflitoRn5GeraLinhaEmfComDimensaoRegra() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Metricas metricas = new Metricas(RELOGIO, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        metricas.contar(Metricas.CONFLITO, Metricas.DIMENSAO_REGRA, "RN5");

        JsonNode linha = Json.mapper().readTree(buffer.toString(StandardCharsets.UTF_8).strip());
        JsonNode cw = linha.at("/_aws/CloudWatchMetrics/0");
        assertEquals(RELOGIO.millis(), linha.at("/_aws/Timestamp").asLong());
        assertEquals("SISGARES", cw.get("Namespace").asText());
        assertEquals("Regra", cw.at("/Dimensions/0/0").asText());
        assertEquals("Conflito", cw.at("/Metrics/0/Name").asText());
        assertEquals("Count", cw.at("/Metrics/0/Unit").asText());
        assertEquals("RN5", linha.get("Regra").asText());
        assertEquals(1, linha.get("Conflito").asInt());
    }

    /** Métrica sem dimensão publica conjunto de dimensões vazio. **Validates: Requirements 21.2** */
    @Test
    void reservaCriadaSemDimensoes() throws Exception {
        JsonNode linha = Json.mapper().readTree(new Metricas(RELOGIO).formatar(Metricas.RESERVA_CRIADA,
                java.util.Map.of()));
        assertTrue(linha.at("/_aws/CloudWatchMetrics/0/Dimensions/0").isEmpty());
        assertEquals(1, linha.get("ReservaCriada").asInt());
    }

    /** Toda linha é JSON válido com o valor 1 sob o nome da métrica. **Validates: Requirements 21.2** */
    @Property(tries = 50)
    void todaLinhaEhJsonValido(@ForAll @AlphaChars @StringLength(min = 1, max = 20) String nome) throws Exception {
        JsonNode linha = Json.mapper().readTree(new Metricas(RELOGIO).formatar(nome, java.util.Map.of()));
        assertEquals(nome, linha.at("/_aws/CloudWatchMetrics/0/Metrics/0/Name").asText());
        assertEquals(1, linha.get(nome).asInt());
    }
}
