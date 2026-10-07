package br.mp.mpf.sisgares.comumaws.dynamo;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * Fábrica do cliente DynamoDB (SDK v2) e do cliente enhanced.
 *
 * <p>Variáveis de ambiente:
 * <ul>
 *   <li>{@code TABELA_SISGARES}: nome da tabela única (obrigatória).</li>
 *   <li>{@code DYNAMODB_ENDPOINT}: endpoint alternativo, ex.: DynamoDB Local (opcional).</li>
 * </ul>
 * Região e credenciais seguem a cadeia padrão do SDK (na Lambda, {@code AWS_REGION} e o papel IAM).
 * As instâncias padrão são criadas uma vez por contêiner e reutilizadas entre invocações (SnapStart).
 */
public final class ClienteDynamo {

    public static final String VAR_TABELA = "TABELA_SISGARES";
    public static final String VAR_ENDPOINT = "DYNAMODB_ENDPOINT";

    private final String nomeTabela;
    private final DynamoDbClient cliente;
    private final DynamoDbEnhancedClient enhanced;

    private ClienteDynamo(Function<String, String> ambiente) {
        this.nomeTabela = lerNomeTabela(ambiente);
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        String endpoint = ambiente.apply(VAR_ENDPOINT);
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        this.cliente = builder.build();
        this.enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(cliente).build();
    }

    /** Instância única lida das variáveis de ambiente do processo. */
    public static ClienteDynamo padrao() {
        return Padrao.INSTANCIA;
    }

    /** Cria uma instância a partir de um mapa de variáveis (testes e DynamoDB Local). */
    public static ClienteDynamo de(Map<String, String> variaveis) {
        return new ClienteDynamo(variaveis::get);
    }

    /** Lê e valida o nome da tabela; falha cedo se a variável estiver ausente. */
    static String lerNomeTabela(Function<String, String> ambiente) {
        String valor = ambiente.apply(VAR_TABELA);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + VAR_TABELA + " não definida.");
        }
        return valor.trim();
    }

    public String nomeTabela() {
        return nomeTabela;
    }

    public DynamoDbClient cliente() {
        return cliente;
    }

    public DynamoDbEnhancedClient enhanced() {
        return enhanced;
    }

    /** Holder para inicialização preguiçosa e thread-safe. */
    private static final class Padrao {
        private static final ClienteDynamo INSTANCIA = new ClienteDynamo(System::getenv);
    }
}
