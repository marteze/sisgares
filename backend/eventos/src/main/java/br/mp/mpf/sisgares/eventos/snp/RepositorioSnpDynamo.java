package br.mp.mpf.sisgares.eventos.snp;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import br.mp.mpf.sisgares.eventos.notificador.LeitorDadosDynamo;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * {@link RepositorioSnp} sobre a tabela única. Pedido_SNP: {@code PK=RESE#<id>}, {@code SK=SNP#<vinculo>}
 * com {@code numero}, {@code link}, {@code status} e {@code motivoFalha}. O Notificador lê {@code numero}.
 *
 * <p>Gravações condicionadas a "não há registro concluído": um pedido REGISTRADO nunca é sobrescrito,
 * nem por uma falha de entrega repetida.
 */
public final class RepositorioSnpDynamo implements RepositorioSnp {

    static final String NUMERO = "numero";
    static final String LINK = "link";
    static final String STATUS = "status";
    static final String MOTIVO_FALHA = "motivoFalha";
    static final String CODIGO_SERVICO = "codigoServico";
    static final String ATUALIZADO_EM = "atualizadoEm";
    public static final String REGISTRADO = "REGISTRADO";
    public static final String FALHA = "FALHA";

    private final DynamoDbClient cliente;
    private final String tabela;
    private final Clock clock;
    private final LeitorDadosDynamo leitor;
    private final RepositorioCatalogo catalogo;

    public RepositorioSnpDynamo(DynamoDbClient cliente, String tabela, Clock clock) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leitor = new LeitorDadosDynamo(cliente, tabela);
        this.catalogo = new RepositorioCatalogo(cliente, tabela);
    }

    @Override
    public Optional<Reserva> versao(long reseId, long numero) {
        return leitor.versao(reseId, numero).map(VersaoReserva::reserva);
    }

    @Override
    public Optional<Reserva> reservaAtual(long reseId) {
        return leitor.reservaAtual(reseId);
    }

    @Override
    public List<VinculoSnp> vinculosDoAmbiente(long ambienteId) {
        return catalogo.listarSetoresDoAmbiente(ambienteId).stream().map(VinculoSnp::doAmbiente).toList();
    }

    @Override
    public List<VinculoSnp> vinculosDoRecurso(long recursoId) {
        return catalogo.listarSetoresDoRecurso(recursoId).stream().map(VinculoSnp::doRecurso).toList();
    }

    @Override
    public Set<String> vinculosRegistrados(long reseId) {
        Set<String> chaves = new LinkedHashSet<>();
        Map<String, AttributeValue> valores = new HashMap<>();
        valores.put(":pk", AttributeValue.fromS(Chaves.pkReserva(reseId)));
        valores.put(":prefixo", AttributeValue.fromS(Chaves.SNP));
        Map<String, AttributeValue> inicio = null;
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
                AttributeValue st = item.get(STATUS);
                AttributeValue sk = item.get(NomesTabela.SK);
                if (st != null && REGISTRADO.equals(st.s()) && sk != null && sk.s() != null) {
                    chaves.add(sk.s().substring(Chaves.SNP.length()));
                }
            }
            inicio = resposta.hasLastEvaluatedKey() && !resposta.lastEvaluatedKey().isEmpty()
                    ? resposta.lastEvaluatedKey()
                    : null;
        } while (inicio != null);
        return Set.copyOf(chaves);
    }

    @Override
    public void gravarRegistro(PedidoSnp pedido, RespostaSnp resposta) {
        Map<String, AttributeValue> item = base(pedido, REGISTRADO);
        item.put(NUMERO, AttributeValue.fromS(resposta.numero()));
        item.put(LINK, AttributeValue.fromS(resposta.link()));
        gravar(item);
    }

    @Override
    public void gravarFalha(PedidoSnp pedido, String motivo) {
        Map<String, AttributeValue> item = base(pedido, FALHA);
        item.put(MOTIVO_FALHA, AttributeValue.fromS(motivo == null || motivo.isBlank() ? "DESCONHECIDO" : motivo));
        gravar(item);
    }

    private Map<String, AttributeValue> base(PedidoSnp pedido, String status) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(NomesTabela.PK, AttributeValue.fromS(Chaves.pkReserva(pedido.reseId())));
        item.put(NomesTabela.SK, AttributeValue.fromS(Chaves.skPedidoSnp(pedido.vinculo())));
        item.put(STATUS, AttributeValue.fromS(status));
        item.put(CODIGO_SERVICO, AttributeValue.fromS(pedido.codigoServico()));
        item.put(ATUALIZADO_EM, AttributeValue.fromS(clock.instant().toString()));
        return item;
    }

    /** Put condicionado: não sobrescreve pedido já REGISTRADO (entrega duplicada é ignorada). */
    private void gravar(Map<String, AttributeValue> item) {
        try {
            cliente.putItem(PutItemRequest.builder()
                    .tableName(tabela)
                    .item(item)
                    .conditionExpression("attribute_not_exists(#pk) OR #st <> :reg")
                    .expressionAttributeNames(Map.of("#pk", NomesTabela.PK, "#st", STATUS))
                    .expressionAttributeValues(Map.of(":reg", AttributeValue.fromS(REGISTRADO)))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // Já registrado por outra entrega: nada a fazer
        }
    }
}
