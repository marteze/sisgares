package br.mp.mpf.sisgares.comumaws.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * ObjectMapper compartilhado pelos handlers.
 *
 * <p>Whitelist: {@code FAIL_ON_UNKNOWN_PROPERTIES} ligado, de modo que campos não
 * previstos no DTO geram erro (mapeado para 422 {@code CAMPO_NAO_PERMITIDO}).
 * Datas em ISO-8601 (sem timestamps numéricos). Saída sem indentação (uma linha).
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.INDENT_OUTPUT);

    private Json() {
    }

    /** Retorna o ObjectMapper configurado (thread-safe após a configuração). */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Serializa um objeto em JSON numa linha. */
    public static String escrever(Object valor) {
        try {
            return MAPPER.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar JSON", e);
        }
    }

    /**
     * Desserializa JSON no tipo informado.
     *
     * @throws JsonProcessingException corpo malformado ou com campo não permitido
     */
    public static <T> T ler(String json, Class<T> tipo) throws JsonProcessingException {
        return MAPPER.readValue(json, tipo);
    }
}
