package br.mp.mpf.sisgares.comumaws.repositorio;

import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.chave;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.s;

import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

/**
 * Operações básicas sobre a tabela única: GetItem, Query paginada, Put e transações.
 *
 * <p>Toda expressão usa placeholders ({@code #nome} / {@code :valor}); nenhum valor é concatenado.
 */
final class OperacoesDynamo {

    /** Limite de itens por TransactWriteItems no DynamoDB. */
    static final int LIMITE_TRANSACAO = 100;

    private final DynamoDbClient cliente;
    private final String tabela;

    OperacoesDynamo(DynamoDbClient cliente, String tabela) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
    }

    String tabela() {
        return tabela;
    }

    /** GetItem por PK/SK. */
    Optional<Map<String, AttributeValue>> obter(String pk, String sk) {
        GetItemResponse resposta = cliente.getItem(GetItemRequest.builder()
                .tableName(tabela)
                .key(chave(pk, sk))
                .build());
        return resposta.hasItem() && !resposta.item().isEmpty()
                ? Optional.of(resposta.item())
                : Optional.empty();
    }

    /** Query de toda a partição. */
    List<Map<String, AttributeValue>> consultar(String pk) {
        return consultar(pk, null);
    }

    /** Query na partição com {@code begins_with(SK, :prefixo)} opcional; percorre todas as páginas. */
    List<Map<String, AttributeValue>> consultar(String pk, String prefixoSk) {
        Map<String, String> nomes = new HashMap<>();
        Map<String, AttributeValue> valores = new HashMap<>();
        nomes.put("#pk", NomesTabela.PK);
        valores.put(":pk", s(pk));
        String expressao = "#pk = :pk";
        if (prefixoSk != null) {
            nomes.put("#sk", NomesTabela.SK);
            valores.put(":prefixo", s(prefixoSk));
            expressao += " AND begins_with(#sk, :prefixo)";
        }
        List<Map<String, AttributeValue>> itens = new ArrayList<>();
        Map<String, AttributeValue> inicio = null;
        do {
            QueryRequest.Builder requisicao = QueryRequest.builder()
                    .tableName(tabela)
                    .keyConditionExpression(expressao)
                    .expressionAttributeNames(nomes)
                    .expressionAttributeValues(valores);
            if (inicio != null) {
                requisicao.exclusiveStartKey(inicio);
            }
            QueryResponse resposta = cliente.query(requisicao.build());
            itens.addAll(resposta.items());
            inicio = resposta.hasLastEvaluatedKey() && !resposta.lastEvaluatedKey().isEmpty()
                    ? resposta.lastEvaluatedKey()
                    : null;
        } while (inicio != null);
        return itens;
    }

    /**
     * Query em um GSI ({@code <indice>PK = :pk}) com {@code <indice>SK BETWEEN :de AND :ate} opcional;
     * percorre todas as páginas. Os limites são usados somente quando ambos forem informados.
     *
     * @param indice nome do índice (ex.: {@link NomesTabela#GSI1}); os atributos de chave são
     *               {@code <indice>PK} e {@code <indice>SK}
     */
    List<Map<String, AttributeValue>> consultarIndice(String indice, String pk, String skDe, String skAte) {
        Objects.requireNonNull(indice, "indice");
        Map<String, String> nomes = new HashMap<>();
        Map<String, AttributeValue> valores = new HashMap<>();
        nomes.put("#pk", indice + "PK");
        valores.put(":pk", s(pk));
        String expressao = "#pk = :pk";
        if (skDe != null && skAte != null) {
            nomes.put("#sk", indice + "SK");
            valores.put(":de", s(skDe));
            valores.put(":ate", s(skAte));
            expressao += " AND #sk BETWEEN :de AND :ate";
        }
        List<Map<String, AttributeValue>> itens = new ArrayList<>();
        Map<String, AttributeValue> inicio = null;
        do {
            QueryRequest.Builder requisicao = QueryRequest.builder()
                    .tableName(tabela)
                    .indexName(indice)
                    .keyConditionExpression(expressao)
                    .expressionAttributeNames(nomes)
                    .expressionAttributeValues(valores);
            if (inicio != null) {
                requisicao.exclusiveStartKey(inicio);
            }
            QueryResponse resposta = cliente.query(requisicao.build());
            itens.addAll(resposta.items());
            inicio = resposta.hasLastEvaluatedKey() && !resposta.lastEvaluatedKey().isEmpty()
                    ? resposta.lastEvaluatedKey()
                    : null;
        } while (inicio != null);
        return itens;
    }

    /** PutItem simples (sobrescreve o item). */
    void gravar(Map<String, AttributeValue> item) {
        cliente.putItem(PutItemRequest.builder().tableName(tabela).item(item).build());
    }

    /** Executa itens em uma única TransactWriteItems (tudo ou nada). */
    void transacao(List<TransactWriteItem> itens) {
        if (itens.isEmpty()) {
            return;
        }
        if (itens.size() > LIMITE_TRANSACAO) {
            throw new IllegalArgumentException("Transação excede " + LIMITE_TRANSACAO + " itens.");
        }
        cliente.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(itens).build());
    }

    TransactWriteItem put(Map<String, AttributeValue> item) {
        return TransactWriteItem.builder()
                .put(Put.builder().tableName(tabela).item(item).build())
                .build();
    }

    TransactWriteItem delete(String pk, String sk) {
        return TransactWriteItem.builder()
                .delete(Delete.builder().tableName(tabela).key(chave(pk, sk)).build())
                .build();
    }

    /**
     * Update com {@code SET} dos campos informados e {@code REMOVE} dos opcionais ausentes,
     * condicionado a {@code attribute_exists(PK)} quando {@code exigirExistente}.
     * Preserva atributos não citados (ex.: o conjunto de ambientes vinculados do Recurso).
     */
    TransactWriteItem atualizar(String pk, String sk, Map<String, AttributeValue> campos,
                                List<String> remover, boolean exigirExistente) {
        Map<String, String> nomes = new HashMap<>();
        Map<String, AttributeValue> valores = new HashMap<>();
        List<String> sets = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, AttributeValue> campo : campos.entrySet()) {
            if (campo.getKey().equals(NomesTabela.PK) || campo.getKey().equals(NomesTabela.SK)) {
                continue;
            }
            nomes.put("#c" + i, campo.getKey());
            valores.put(":c" + i, campo.getValue());
            sets.add("#c" + i + " = :c" + i);
            i++;
        }
        List<String> removes = new ArrayList<>();
        int j = 0;
        for (String nome : remover) {
            if (!campos.containsKey(nome)) {
                nomes.put("#r" + j, nome);
                removes.add("#r" + j);
                j++;
            }
        }
        StringBuilder expressao = new StringBuilder();
        if (!sets.isEmpty()) {
            expressao.append("SET ").append(String.join(", ", sets));
        }
        if (!removes.isEmpty()) {
            expressao.append(expressao.isEmpty() ? "" : " ").append("REMOVE ").append(String.join(", ", removes));
        }
        // Nomes completos antes do builder, que copia o mapa no momento da chamada
        if (exigirExistente) {
            nomes.put("#pkExiste", NomesTabela.PK);
        }
        Update.Builder update = Update.builder()
                .tableName(tabela)
                .key(chave(pk, sk))
                .updateExpression(expressao.toString())
                .expressionAttributeNames(nomes);
        if (!valores.isEmpty()) {
            update.expressionAttributeValues(valores);
        }
        if (exigirExistente) {
            update.conditionExpression("attribute_exists(#pkExiste)");
        }
        return TransactWriteItem.builder().update(update.build()).build();
    }

    /**
     * Update {@code SET versao = versao + 1} condicionado à versão lida (RN7).
     *
     * <p>Com {@code versaoLida == 0} (item ainda inexistente na leitura), a condição é
     * {@code attribute_not_exists(PK) OR versao = :lida}, permitindo a criação do controle.
     */
    TransactWriteItem incrementarVersao(String pk, String sk, long versaoLida) {
        String condicao = versaoLida == 0
                ? "attribute_not_exists(#pk) OR #versao = :lida"
                : "#versao = :lida";
        Update update = Update.builder()
                .tableName(tabela)
                .key(chave(pk, sk))
                .updateExpression("SET #versao = if_not_exists(#versao, :zero) + :um")
                .conditionExpression(condicao)
                .expressionAttributeNames(Map.of("#pk", NomesTabela.PK, "#versao", NomesTabela.VERSAO))
                .expressionAttributeValues(Map.of(
                        ":lida", Atributos.n(versaoLida),
                        ":zero", Atributos.n(0),
                        ":um", Atributos.n(1)))
                .build();
        return TransactWriteItem.builder().update(update).build();
    }

    /** Update {@code ADD}/{@code DELETE} de um número em um conjunto (NS), exigindo item existente. */
    TransactWriteItem alterarConjunto(String pk, String sk, String atributo, long valor, boolean adicionar) {
        Update update = Update.builder()
                .tableName(tabela)
                .key(chave(pk, sk))
                .updateExpression((adicionar ? "ADD" : "DELETE") + " #conj :valor")
                .conditionExpression("attribute_exists(#pk)")
                .expressionAttributeNames(Map.of("#conj", atributo, "#pk", NomesTabela.PK))
                .expressionAttributeValues(Map.of(":valor", AttributeValue.fromNs(List.of(Long.toString(valor)))))
                .build();
        return TransactWriteItem.builder().update(update).build();
    }
}
