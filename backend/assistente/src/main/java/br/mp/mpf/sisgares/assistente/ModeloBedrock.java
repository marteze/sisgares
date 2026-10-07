package br.mp.mpf.sisgares.assistente;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * {@link ModeloLinguagem} sobre a API Converse do Amazon Bedrock. O modelo vem de
 * {@code MODELO_BEDROCK} (Req. 18.7); com {@code GUARDRAIL_ID} definido, o Guardrail é aplicado na
 * entrada e na saída (Req. 18.3).
 */
public final class ModeloBedrock implements ModeloLinguagem {

    public static final String VAR_MODELO = "MODELO_BEDROCK";
    public static final String VAR_GUARDRAIL_ID = "GUARDRAIL_ID";
    public static final String VAR_GUARDRAIL_VERSAO = "GUARDRAIL_VERSAO";
    public static final String MODELO_PADRAO = "amazon.nova-lite-v1:0";
    /** Versão usada quando há Guardrail sem versão informada. */
    public static final String VERSAO_PADRAO_GUARDRAIL = "DRAFT";

    private final BedrockRuntimeClient cliente;
    private final String modelo;
    private final GuardrailConfiguration guardrail;

    public ModeloBedrock(BedrockRuntimeClient cliente, String modelo, String guardrailId, String guardrailVersao) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.modelo = vazio(modelo) ? MODELO_PADRAO : modelo.trim();
        this.guardrail = vazio(guardrailId) ? null : GuardrailConfiguration.builder()
                .guardrailIdentifier(guardrailId.trim())
                .guardrailVersion(vazio(guardrailVersao) ? VERSAO_PADRAO_GUARDRAIL : guardrailVersao.trim())
                .build();
    }

    /** Cria a partir das variáveis de ambiente, com timeout de chamada igual ao do Handler. */
    public static ModeloBedrock doAmbiente(Map<String, String> variaveis, Duration timeout) {
        BedrockRuntimeClient cliente = BedrockRuntimeClient.builder()
                .overrideConfiguration(c -> c.apiCallTimeout(timeout).apiCallAttemptTimeout(timeout))
                .build();
        return new ModeloBedrock(cliente, variaveis.get(VAR_MODELO), variaveis.get(VAR_GUARDRAIL_ID),
                variaveis.get(VAR_GUARDRAIL_VERSAO));
    }

    /** Identificador do modelo em uso. */
    public String modelo() {
        return modelo;
    }

    /** Indica se o Guardrail está configurado. */
    public boolean comGuardrail() {
        return guardrail != null;
    }

    @Override
    public String gerar(String instrucoes, String mensagem) {
        ConverseRequest.Builder requisicao = ConverseRequest.builder()
                .modelId(modelo)
                .system(SystemContentBlock.fromText(instrucoes))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(mensagem))
                        .build())
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(512).temperature(0f).build());
        if (guardrail != null) {
            requisicao.guardrailConfig(guardrail);
        }
        ConverseResponse resposta = cliente.converse(requisicao.build());
        // Se o Guardrail intervier, o texto devolvido é a mensagem de bloqueio: o saneador descarta tudo
        if (resposta.output() == null || resposta.output().message() == null) {
            return "";
        }
        return resposta.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
