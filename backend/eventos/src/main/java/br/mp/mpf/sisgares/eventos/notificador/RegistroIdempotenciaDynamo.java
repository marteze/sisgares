package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Idempotência em {@code EVT#<eventoId>} / {@code <consumidor>} com TTL de 90 dias (Req. 2.6).
 *
 * <p>{@link #iniciar} faz Put condicionado a {@code attribute_not_exists(PK)}. Para não perder o
 * evento quando uma tentativa anterior falhou ou expirou (timeout da Lambda), a condição também
 * aceita item em {@code FALHA} ou em {@code PROCESSANDO} há mais que {@link #LIMITE_PROCESSAMENTO}.
 */
public final class RegistroIdempotenciaDynamo implements RegistroIdempotencia {

    /** Nome do consumidor usado como SK. */
    public static final String CONSUMIDOR = "NOTIFICADOR";
    /** Acima da duração máxima da Lambda (15 min): após isso a trava é considerada abandonada. */
    static final Duration LIMITE_PROCESSAMENTO = Duration.ofMinutes(16);
    static final String STATUS = "status";
    static final String PROCESSANDO = "PROCESSANDO";
    static final String CONCLUIDO = "CONCLUIDO";
    static final String FALHA = "FALHA";

    private final DynamoDbClient cliente;
    private final String tabela;
    private final String consumidor;
    private final Clock clock;

    public RegistroIdempotenciaDynamo(DynamoDbClient cliente, String tabela, String consumidor, Clock clock) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
        this.consumidor = Objects.requireNonNull(consumidor, "consumidor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean iniciar(String eventoId) {
        Instant agora = clock.instant();
        Map<String, AttributeValue> item = Map.of(
                NomesTabela.PK, AttributeValue.fromS(Chaves.pkEventoProcessado(eventoId)),
                NomesTabela.SK, AttributeValue.fromS(consumidor),
                STATUS, AttributeValue.fromS(PROCESSANDO),
                NomesTabela.CRIADO_EM, AttributeValue.fromS(agora.toString()),
                NomesTabela.EXPIRA_EM, numero(agora.plus(Notificador.TTL).getEpochSecond()));
        try {
            cliente.putItem(PutItemRequest.builder()
                    .tableName(tabela)
                    .item(item)
                    .conditionExpression("attribute_not_exists(#pk) OR #st = :falha"
                            + " OR (#st = :proc AND #criado < :limite)")
                    .expressionAttributeNames(Map.of(
                            "#pk", NomesTabela.PK, "#st", STATUS, "#criado", NomesTabela.CRIADO_EM))
                    .expressionAttributeValues(Map.of(
                            ":falha", AttributeValue.fromS(FALHA),
                            ":proc", AttributeValue.fromS(PROCESSANDO),
                            // Instant.toString é ISO-8601 em UTC: ordem lexicográfica = cronológica
                            ":limite", AttributeValue.fromS(agora.minus(LIMITE_PROCESSAMENTO).toString())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public void concluir(String eventoId) {
        atualizarStatus(eventoId, CONCLUIDO);
    }

    @Override
    public void falhar(String eventoId) {
        atualizarStatus(eventoId, FALHA);
    }

    private void atualizarStatus(String eventoId, String status) {
        cliente.updateItem(UpdateItemRequest.builder()
                .tableName(tabela)
                .key(Map.of(
                        NomesTabela.PK, AttributeValue.fromS(Chaves.pkEventoProcessado(eventoId)),
                        NomesTabela.SK, AttributeValue.fromS(consumidor)))
                .updateExpression("SET #st = :st")
                .conditionExpression("attribute_exists(#pk)")
                .expressionAttributeNames(Map.of("#st", STATUS, "#pk", NomesTabela.PK))
                .expressionAttributeValues(Map.of(":st", AttributeValue.fromS(status)))
                .build());
    }

    private static AttributeValue numero(long valor) {
        return AttributeValue.fromN(Long.toString(valor));
    }
}
