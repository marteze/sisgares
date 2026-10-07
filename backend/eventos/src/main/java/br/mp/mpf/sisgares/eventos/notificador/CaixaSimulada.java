package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * Caixa_Simulada: grava as Notificações em {@code RESE#<id>} / {@code NOTI#<eventoId>#<envoId>}
 * com TTL {@code expiraEm} (Req. 13.5, 13.6).
 *
 * <p>Como {@link EnviadorEmail}, não envia nada para fora: o e-mail fica somente registrado
 * (modo {@code simulado}, usado com SES em sandbox ou execução local).
 */
public final class CaixaSimulada implements EnviadorEmail, CaixaNotificacoes {

    static final String TIPO = "tipo";
    static final String DESTINATARIOS = "destinatarios";
    static final String ASSUNTO = "assunto";
    static final String HTML = "html";
    static final String STATUS = "status";
    static final String MOTIVO_FALHA = "motivoFalha";
    static final String EVENTO_ID = "eventoId";
    static final String ENVO_ID = "ENVO_ID";

    private final DynamoDbClient cliente;
    private final String tabela;

    public CaixaSimulada(DynamoDbClient cliente, String tabela) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
    }

    /** Modo simulado: nada é enviado; o registro é feito pelo Notificador via {@link #registrar}. */
    @Override
    public StatusNotificacao enviar(List<String> para, Email email) {
        return StatusNotificacao.SIMULADA;
    }

    @Override
    public void registrar(Notificacao n) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(NomesTabela.PK, s(Chaves.pkReserva(n.reseId())));
        item.put(NomesTabela.SK, s(Chaves.skNotificacao(n.eventoId(), n.envoId())));
        item.put(EVENTO_ID, s(n.eventoId()));
        item.put(ENVO_ID, AttributeValue.fromN(Long.toString(n.envoId())));
        item.put(TIPO, s(n.tipo().nomeEvento()));
        item.put(DESTINATARIOS, AttributeValue.fromL(n.destinatarios().stream().map(CaixaSimulada::s).toList()));
        item.put(ASSUNTO, s(n.assunto()));
        item.put(HTML, s(n.html()));
        item.put(STATUS, s(n.status().name()));
        if (n.motivoFalha() != null && !n.motivoFalha().isBlank()) {
            item.put(MOTIVO_FALHA, s(n.motivoFalha()));
        }
        item.put(NomesTabela.CRIADO_EM, s(n.registradaEm().toString()));
        item.put(NomesTabela.EXPIRA_EM, AttributeValue.fromN(Long.toString(n.expiraEm())));
        cliente.putItem(PutItemRequest.builder().tableName(tabela).item(item).build());
    }

    @Override
    public Optional<StatusNotificacao> status(long reseId, String eventoId, long envoId) {
        GetItemResponse resposta = cliente.getItem(GetItemRequest.builder()
                .tableName(tabela)
                .key(Map.of(
                        NomesTabela.PK, s(Chaves.pkReserva(reseId)),
                        NomesTabela.SK, s(Chaves.skNotificacao(eventoId, envoId))))
                .projectionExpression("#st")
                .expressionAttributeNames(Map.of("#st", STATUS))
                .consistentRead(true)
                .build());
        if (!resposta.hasItem() || resposta.item().get(STATUS) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(StatusNotificacao.valueOf(resposta.item().get(STATUS).s()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static AttributeValue s(String valor) {
        return AttributeValue.fromS(valor);
    }
}
