package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioReservas;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Implementação de {@link LeitorDados} sobre a tabela única (somente GetItem e Query).
 *
 * <p>Versão_Reserva: item {@code RESE#<id>} / {@code VERS#<000n>} com o snapshot da Reserva em
 * JSON no atributo {@code payload}. Pedido_SNP: itens {@code SNP#} com o atributo {@code numero}.
 */
public final class LeitorDadosDynamo implements LeitorDados {

    /** Atributo com o número do Pedido_SNP no item {@code SNP#}. */
    static final String NUMERO_SNP = "numero";

    private final DynamoDbClient cliente;
    private final String tabela;
    private final RepositorioCatalogo catalogo;
    private final RepositorioReservas reservas;

    public LeitorDadosDynamo(DynamoDbClient cliente, String tabela) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
        this.catalogo = new RepositorioCatalogo(cliente, tabela);
        this.reservas = new RepositorioReservas(cliente, tabela);
    }

    @Override
    public Optional<VersaoReserva> versao(long reseId, long numero) {
        if (numero < 0) {
            return Optional.empty();
        }
        GetItemResponse resposta = cliente.getItem(GetItemRequest.builder()
                .tableName(tabela)
                .key(Map.of(
                        NomesTabela.PK, AttributeValue.fromS(Chaves.pkReserva(reseId)),
                        NomesTabela.SK, AttributeValue.fromS(Chaves.skVersao(Math.toIntExact(numero)))))
                .build());
        if (!resposta.hasItem() || resposta.item().isEmpty()) {
            return Optional.empty();
        }
        AttributeValue payload = resposta.item().get(NomesTabela.PAYLOAD);
        if (payload == null || payload.s() == null) {
            throw new IllegalStateException("Versão " + numero + " da reserva " + reseId + " sem payload.");
        }
        try {
            Reserva reserva = Json.mapper().readValue(payload.s(), Reserva.class);
            return Optional.of(new VersaoReserva(numero, reserva));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Versão " + numero + " da reserva " + reseId + " ilegível.", e);
        }
    }

    @Override
    public Optional<Reserva> reservaAtual(long reseId) {
        return reservas.obter(reseId);
    }

    @Override
    public List<String> pedidosSnp(long reseId) {
        List<String> numeros = new ArrayList<>();
        Map<String, AttributeValue> inicio = null;
        Map<String, AttributeValue> valores = new HashMap<>();
        valores.put(":pk", AttributeValue.fromS(Chaves.pkReserva(reseId)));
        valores.put(":prefixo", AttributeValue.fromS(Chaves.SNP));
        do {
            QueryRequest.Builder req = QueryRequest.builder()
                    .tableName(tabela)
                    .keyConditionExpression("#pk = :pk AND begins_with(#sk, :prefixo)")
                    .expressionAttributeNames(Map.of("#pk", NomesTabela.PK, "#sk", NomesTabela.SK))
                    .expressionAttributeValues(valores);
            if (inicio != null) {
                req.exclusiveStartKey(inicio);
            }
            QueryResponse resposta = cliente.query(req.build());
            for (Map<String, AttributeValue> item : resposta.items()) {
                AttributeValue n = item.get(NUMERO_SNP);
                if (n != null && n.s() != null && !n.s().isBlank()) {
                    numeros.add(n.s());
                } else if (n != null && n.n() != null) {
                    numeros.add(n.n());
                }
            }
            inicio = resposta.hasLastEvaluatedKey() && !resposta.lastEvaluatedKey().isEmpty()
                    ? resposta.lastEvaluatedKey()
                    : null;
        } while (inicio != null);
        return List.copyOf(numeros);
    }

    @Override
    public Optional<String> descricaoAmbiente(long ambienteId) {
        return catalogo.obterAmbiente(ambienteId).map(a -> a.ambiente().descricao());
    }

    @Override
    public Optional<String> descricaoDisposicao(long disposicaoId) {
        return catalogo.obterDisposicao(disposicaoId).map(d -> d.descricao());
    }

    @Override
    public Optional<String> descricaoRecurso(long recursoId) {
        return catalogo.obterRecurso(recursoId).map(r -> r.recurso().descricao());
    }

    @Override
    public List<Long> setoresDoAmbiente(long ambienteId) {
        return catalogo.listarSetoresDoAmbiente(ambienteId).stream().map(VinculoSetor::envoId).toList();
    }

    @Override
    public List<Long> setoresDoRecurso(long recursoId) {
        return catalogo.listarSetoresDoRecurso(recursoId).stream().map(VinculoSetor::envoId).toList();
    }

    @Override
    public Optional<Setor> setor(long envoId) {
        return catalogo.obterSetor(envoId);
    }
}
