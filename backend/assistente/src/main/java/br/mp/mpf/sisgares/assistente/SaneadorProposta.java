package br.mp.mpf.sisgares.assistente;

import br.mp.mpf.sisgares.assistente.RespostaProposta.Proposta;
import br.mp.mpf.sisgares.assistente.RespostaProposta.RecursoProposto;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Saneia a saída do modelo (lógica pura, Req. 18.4): extrai o JSON, mantém só os campos do schema,
 * aceita apenas IDs presentes no {@link Catalogo} enviado e formatos válidos. Todo campo descartado
 * ou ausente vai para {@code camposNaoPreenchidos}. Nunca lança exceção para entrada malformada.
 */
public final class SaneadorProposta {

    public static final String AMBIENTE_ID = "ambienteId";
    public static final String DATA = "data";
    public static final String INICIO = "inicio";
    public static final String TERMINO = "termino";
    public static final String PARTICIPANTES = "participantes";
    public static final String FINALIDADE = "finalidade";
    public static final String RECURSOS = "recursos";

    /** Campos do schema da proposta, na ordem do formulário. */
    public static final List<String> CAMPOS =
            List.of(AMBIENTE_ID, DATA, INICIO, TERMINO, PARTICIPANTES, FINALIDADE, RECURSOS);

    public static final int PARTICIPANTES_MIN = 1;
    public static final int PARTICIPANTES_MAX = 10_000;
    public static final int FINALIDADE_MAX = 2000;
    public static final int QUANTIDADE_MAX = 10_000;
    /** Limite de tentativas de extração (evita custo quadrático em textos com muitos '{'). */
    private static final int MAX_TENTATIVAS = 20;
    /** Limite de tamanho da saída analisada. */
    private static final int MAX_CARACTERES = 100_000;

    private static final Pattern HORA = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern DATA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern INTEIRO = Pattern.compile("\\d{1,18}");
    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SaneadorProposta() {
    }

    /**
     * Saneia o texto do modelo contra o catálogo enviado.
     *
     * @param saidaModelo texto bruto (pode ser nulo, vazio ou malformado)
     * @param catalogo    catálogos enviados no prompt
     */
    public static RespostaProposta sanear(String saidaModelo, Catalogo catalogo) {
        Catalogo cat = catalogo == null ? new Catalogo(List.of(), List.of()) : catalogo;
        JsonNode raiz;
        try {
            raiz = extrairObjeto(saidaModelo);
        } catch (RuntimeException e) {
            raiz = null;
        }
        if (raiz == null) {
            return new RespostaProposta(vazia(), CAMPOS);
        }
        // Aceita tanto {"proposta":{...}} quanto o objeto da proposta diretamente
        JsonNode proposta = raiz.path("proposta").isObject() ? raiz.get("proposta") : raiz;

        List<String> faltantes = new ArrayList<>();
        Set<Long> ambientes = cat.idsAmbientes();
        Long ambienteId = id(proposta.get(AMBIENTE_ID));
        if (ambienteId == null || !ambientes.contains(ambienteId)) {
            ambienteId = null;
            faltantes.add(AMBIENTE_ID);
        }
        String data = data(proposta.get(DATA));
        if (data == null) {
            faltantes.add(DATA);
        }
        String inicio = hora(proposta.get(INICIO));
        if (inicio == null) {
            faltantes.add(INICIO);
        }
        String termino = hora(proposta.get(TERMINO));
        // Término deve ser posterior ao início quando ambos existem
        if (termino != null && inicio != null && !LocalTime.parse(termino).isAfter(LocalTime.parse(inicio))) {
            termino = null;
        }
        if (termino == null) {
            faltantes.add(TERMINO);
        }
        Integer participantes = inteiro(proposta.get(PARTICIPANTES), PARTICIPANTES_MIN, PARTICIPANTES_MAX);
        if (participantes == null) {
            faltantes.add(PARTICIPANTES);
        }
        String finalidade = finalidade(proposta.get(FINALIDADE));
        if (finalidade == null) {
            faltantes.add(FINALIDADE);
        }
        List<RecursoProposto> recursos = recursos(proposta.get(RECURSOS), cat.idsRecursos(), faltantes);

        Proposta saneada = new Proposta(ambienteId, data, inicio, termino, participantes, finalidade, recursos);
        return new RespostaProposta(saneada, faltantes);
    }

    private static Proposta vazia() {
        return new Proposta(null, null, null, null, null, null, null);
    }

    // ---------- Extração do JSON ----------

    /** Primeiro objeto JSON válido do texto (o modelo pode cercá-lo de texto ou blocos ```json). */
    static JsonNode extrairObjeto(String texto) {
        if (texto == null || texto.isEmpty() || texto.length() > MAX_CARACTERES) {
            return null;
        }
        int inicio = texto.indexOf('{');
        int tentativas = 0;
        while (inicio >= 0 && tentativas++ < MAX_TENTATIVAS) {
            try (JsonParser parser = MAPPER.getFactory().createParser(texto.substring(inicio))) {
                JsonNode no = MAPPER.readTree(parser);
                if (no != null && no.isObject()) {
                    return no;
                }
            } catch (Exception e) {
                // Trecho inválido: tenta a próxima chave de abertura
            }
            inicio = texto.indexOf('{', inicio + 1);
        }
        return null;
    }

    // ---------- Validação de campos ----------

    /** ID inteiro positivo (número integral ou string só com dígitos). */
    private static Long id(JsonNode no) {
        if (no == null) {
            return null;
        }
        if (no.isIntegralNumber() && no.canConvertToLong() && no.longValue() > 0) {
            return no.longValue();
        }
        if (no.isTextual() && INTEIRO.matcher(no.textValue().trim()).matches()) {
            long valor = Long.parseLong(no.textValue().trim());
            return valor > 0 ? valor : null;
        }
        return null;
    }

    private static Integer inteiro(JsonNode no, int min, int max) {
        if (no == null || !no.isIntegralNumber() || !no.canConvertToInt()) {
            return null;
        }
        int valor = no.intValue();
        return valor >= min && valor <= max ? valor : null;
    }

    /** Data ISO-8601 (yyyy-MM-dd) existente no calendário. */
    private static String data(JsonNode no) {
        if (no == null || !no.isTextual()) {
            return null;
        }
        String texto = no.textValue().trim();
        if (!DATA_ISO.matcher(texto).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(texto, FORMATO_DATA).toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Hora no formato HH:mm (00:00–23:59). */
    private static String hora(JsonNode no) {
        if (no == null || !no.isTextual()) {
            return null;
        }
        String texto = no.textValue().trim();
        return HORA.matcher(texto).matches() ? texto : null;
    }

    /** Texto não vazio com até 2000 caracteres. */
    private static String finalidade(JsonNode no) {
        if (no == null || !no.isTextual()) {
            return null;
        }
        String texto = no.textValue().strip();
        return texto.isEmpty() || texto.length() > FINALIDADE_MAX ? null : texto;
    }

    /**
     * Recursos válidos (ID do catálogo, quantidade 1..10000, sem repetição). Cada item descartado
     * entra como {@code recursos[i]}; sem nenhum item válido, entra {@code recursos}.
     */
    private static List<RecursoProposto> recursos(JsonNode no, Set<Long> catalogo, List<String> faltantes) {
        List<RecursoProposto> validos = new ArrayList<>();
        if (no != null && no.isArray()) {
            Set<Long> vistos = new HashSet<>();
            for (int i = 0; i < no.size(); i++) {
                JsonNode item = no.get(i);
                Long recursoId = item != null && item.isObject() ? id(item.get("recursoId")) : null;
                Integer quantidade = item != null && item.isObject()
                        ? inteiro(item.get("quantidade"), 1, QUANTIDADE_MAX) : null;
                if (recursoId != null && quantidade != null && catalogo.contains(recursoId)
                        && vistos.add(recursoId)) {
                    validos.add(new RecursoProposto(recursoId, quantidade));
                } else {
                    faltantes.add(RECURSOS + "[" + i + "]");
                }
            }
        }
        if (validos.isEmpty()) {
            faltantes.add(RECURSOS);
            return null;
        }
        return validos;
    }
}
