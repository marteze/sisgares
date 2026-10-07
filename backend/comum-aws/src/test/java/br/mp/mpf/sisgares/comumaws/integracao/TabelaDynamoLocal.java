package br.mp.mpf.sisgares.comumaws.integracao;

import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Apoio aos testes de integração: cria o cliente DynamoDB apontando para o DynamoDB Local e
 * provisiona a tabela única {@code sisgares} com GSI1–GSI5, espelhando o {@code DadosStack} do CDK.
 *
 * <p>Usado apenas por testes marcados com {@code @Testcontainers}; não acessa a AWS real.
 */
public final class TabelaDynamoLocal {

    /** Nome da tabela usado nos testes de integração. */
    public static final String TABELA = NomesTabela.TABELA_PADRAO;

    private TabelaDynamoLocal() {
    }

    /** Cliente apontando para o endpoint do DynamoDB Local, com credenciais fictícias. */
    public static DynamoDbClient cliente(String endpoint) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();
    }

    /** Cria a tabela {@code sisgares} (PK/SK) com os cinco GSIs STRING/STRING. */
    public static void criarTabela(DynamoDbClient cliente) {
        var definicoes = new java.util.ArrayList<AttributeDefinition>();
        definicoes.add(atributo(NomesTabela.PK));
        definicoes.add(atributo(NomesTabela.SK));
        var indices = new java.util.ArrayList<GlobalSecondaryIndex>();
        for (int i = 1; i <= 5; i++) {
            String pk = "GSI" + i + "PK";
            String sk = "GSI" + i + "SK";
            definicoes.add(atributo(pk));
            definicoes.add(atributo(sk));
            indices.add(GlobalSecondaryIndex.builder()
                    .indexName("GSI" + i)
                    .keySchema(
                            KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName(sk).keyType(KeyType.RANGE).build())
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                    .build());
        }
        cliente.createTable(CreateTableRequest.builder()
                .tableName(TABELA)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(definicoes)
                .keySchema(
                        KeySchemaElement.builder().attributeName(NomesTabela.PK).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(NomesTabela.SK).keyType(KeyType.RANGE).build())
                .globalSecondaryIndexes(indices)
                .build());
    }

    private static AttributeDefinition atributo(String nome) {
        return AttributeDefinition.builder().attributeName(nome).attributeType(ScalarAttributeType.S).build();
    }
}
