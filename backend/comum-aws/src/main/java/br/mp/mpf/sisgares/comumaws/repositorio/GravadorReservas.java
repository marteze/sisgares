package br.mp.mpf.sisgares.comumaws.repositorio;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoConflitoConcorrente;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Violacao;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Gravação atômica de Reservas em uma única {@code TransactWriteItems} (design.md, "Gravação atômica").
 *
 * <p>Itens da transação:
 * <ol>
 *   <li>{@code Put RESE#<id>/META} com {@code attribute_not_exists(PK)} na criação ou
 *       {@code versao = :lida} na alteração (RN7).</li>
 *   <li>{@code Put} dos itens {@code PRES#}/{@code SOLI#} e do snapshot {@code VERS#<000n>};
 *       na alteração, {@code Delete} dos filhos antigos que não existem mais.</li>
 *   <li>Incremento condicional de {@code CTRL#AMBI#<raiz>} (exceto Local_Proprio) e de
 *       {@code CTRL#RECU#<id>} para cada Recurso_Limitado.</li>
 *   <li>{@code Put OUTBOX} com o evento (publicado e removido após o commit).</li>
 * </ol>
 * Qualquer condição violada cancela tudo e vira {@link ExcecaoConflitoConcorrente}
 * (HTTP 409 {@code RN7_CONFLITO_AO_SALVAR}).
 */
public final class GravadorReservas {

    /** Código da violação quando a reserva exige mais itens do que a transação comporta. */
    public static final String LIMITE_ITENS_TRANSACAO = "LIMITE_ITENS_TRANSACAO";

    static final String VERS_NUMERO = "VERS_NUMERO";

    private final OperacoesDynamo dynamo;

    public GravadorReservas(DynamoDbClient cliente, String nomeTabela) {
        this.dynamo = new OperacoesDynamo(cliente, nomeTabela);
    }

    /**
     * Chave usada em {@code versoesCtrl} para a versão lida do {@code CTRL#AMBI#<raizId>}.
     * Ids são positivos, então o negativo da raiz não colide com ids de Recurso.
     */
    public static long chaveControleAmbiente(long raizId) {
        if (raizId <= 0) {
            throw new IllegalArgumentException("raizId deve ser positivo");
        }
        return -raizId;
    }

    /**
     * Versão atual de um Item_Controle ({@code SK = CTRL}); 0 quando ainda não existe.
     * Deve ser lida antes da validação de conflitos/disponibilidade e repassada a {@link #gravar}.
     */
    public long lerVersaoControle(String pk) {
        return dynamo.obter(pk, Chaves.CTRL)
                .map(item -> Atributos.lerNumero(item, NomesTabela.VERSAO))
                .map(v -> v == null ? 0L : v)
                .orElse(0L);
    }

    /**
     * Grava a Reserva atomicamente.
     *
     * @param r           Reserva com id, ids de Períodos e a nova versão em {@code r.versao()}
     * @param criadoEm    data de criação original (GSI4)
     * @param raizId      raiz da árvore do Ambiente; {@code null} em Local_Proprio
     * @param setores     ENVO_IDs envolvidos (cópias GSI2)
     * @param versoesCtrl versões lidas dos controles: chave {@link #chaveControleAmbiente(long)} para
     *                    a árvore e o RECU_ID para cada Recurso_Limitado solicitado
     * @param versaoLida  versão lida do META; {@code null} na criação
     * @param evento      evento do outbox (sem dados pessoais)
     * @throws ExcecaoConflitoConcorrente se alguma condição falhar (RN7)
     * @throws ExcecaoValidacao           se a transação ultrapassar 100 itens
     */
    public void gravar(Reserva r, LocalDateTime criadoEm, Long raizId, Set<Long> setores,
                       Map<Long, Long> versoesCtrl, Long versaoLida, EventoOutbox evento) {
        Objects.requireNonNull(r, "reserva");
        Objects.requireNonNull(versoesCtrl, "versoesCtrl");
        Objects.requireNonNull(evento, "evento");
        long reseId = Objects.requireNonNull(r.id(), "id da reserva é obrigatório");
        String pk = Chaves.pkReserva(reseId);

        List<TransactWriteItem> itens = new ArrayList<>();

        // 1. META com condição de versão (RN7)
        itens.add(putMeta(MapeadorReserva.paraItemMeta(r, criadoEm), versaoLida));

        // 2. Filhos PRES/SOLI; o Put sobrescreve chaves mantidas, o Delete remove as que sumiram
        List<Map<String, AttributeValue>> filhos = MapeadorReserva.itensFilhos(r, raizId, setores);
        Set<String> skNovas = new HashSet<>();
        for (Map<String, AttributeValue> filho : filhos) {
            skNovas.add(Atributos.texto(filho, NomesTabela.SK));
            itens.add(dynamo.put(filho));
        }
        if (versaoLida != null) {
            // Leitura fora da transação é segura: a condição do META impede gravação concorrente
            for (String prefixo : List.of(Chaves.PRES, Chaves.SOLI)) {
                for (Map<String, AttributeValue> antigo : dynamo.consultar(pk, prefixo)) {
                    String sk = Atributos.texto(antigo, NomesTabela.SK);
                    if (!skNovas.contains(sk)) {
                        itens.add(dynamo.delete(pk, sk));
                    }
                }
            }
        }
        // Snapshot imutável da versão (VERS# não é apagado na alteração)
        itens.add(dynamo.put(itemVersao(pk, r)));

        // 3. Controles: árvore de ambientes e Recursos_Limitados
        Set<Long> recursos = new HashSet<>(versoesCtrl.keySet());
        if (raizId != null) {
            long chave = chaveControleAmbiente(raizId);
            itens.add(dynamo.incrementarVersao(Chaves.pkControleAmbiente(raizId), Chaves.CTRL,
                    versaoObrigatoria(versoesCtrl, chave)));
            recursos.remove(chave);
        }
        for (Long recursoId : recursos) {
            if (recursoId <= 0) {
                throw new IllegalArgumentException("chave de controle inválida em versoesCtrl");
            }
            itens.add(dynamo.incrementarVersao(Chaves.pkControleRecurso(recursoId), Chaves.CTRL,
                    versaoObrigatoria(versoesCtrl, recursoId)));
        }

        // 4. Outbox
        itens.add(dynamo.put(itemOutbox(evento)));

        if (itens.size() > OperacoesDynamo.LIMITE_TRANSACAO) {
            throw new ExcecaoValidacao(List.of(Violacao.de(LIMITE_ITENS_TRANSACAO,
                    "A reserva tem períodos, setores ou recursos demais para ser salva de uma vez. "
                            + "Reduza a quantidade de períodos ou recursos e tente novamente.")));
        }
        executar(itens);
    }

    /** Remove o evento do outbox após a publicação bem-sucedida. */
    public void removerOutbox(EventoOutbox evento) {
        Objects.requireNonNull(evento, "evento");
        executar(List.of(dynamo.delete(Chaves.OUTBOX,
                Chaves.skOutbox(evento.ocorridoEm(), evento.eventoId()))));
    }

    /** Eventos pendentes de publicação, em ordem cronológica (Query {@code PK = OUTBOX}). */
    public List<EventoOutbox> listarOutboxPendentes() {
        List<EventoOutbox> eventos = new ArrayList<>();
        for (Map<String, AttributeValue> item : dynamo.consultar(Chaves.OUTBOX)) {
            String payload = Atributos.texto(item, NomesTabela.PAYLOAD);
            try {
                eventos.add(Json.ler(payload, EventoOutbox.class));
            } catch (JsonProcessingException e) {
                // Item corrompido: não expõe o payload na mensagem
                throw new IllegalStateException("Payload inválido no outbox", e);
            }
        }
        return eventos;
    }

    // ---------- Auxiliares ----------

    private TransactWriteItem putMeta(Map<String, AttributeValue> meta, Long versaoLida) {
        Put.Builder put = Put.builder().tableName(dynamo.tabela()).item(meta);
        if (versaoLida == null) {
            put.conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(Map.of("#pk", NomesTabela.PK));
        } else {
            put.conditionExpression("#versao = :lida")
                    .expressionAttributeNames(Map.of("#versao", NomesTabela.VERSAO))
                    .expressionAttributeValues(Map.of(":lida", Atributos.n(versaoLida)));
        }
        return TransactWriteItem.builder().put(put.build()).build();
    }

    private static Map<String, AttributeValue> itemVersao(String pk, Reserva r) {
        if (r.versao() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("versao fora do intervalo");
        }
        Map<String, AttributeValue> item = Atributos.chave(pk, Chaves.skVersao((int) r.versao()));
        item.put(VERS_NUMERO, Atributos.n(r.versao()));
        item.put(NomesTabela.PAYLOAD, Atributos.s(Json.escrever(r)));
        if (r.alteradaEm() != null) {
            item.put(NomesTabela.CRIADO_EM, Atributos.s(Chaves.data(r.alteradaEm())));
        }
        return item;
    }

    private static Map<String, AttributeValue> itemOutbox(EventoOutbox evento) {
        Map<String, AttributeValue> item = Atributos.chave(Chaves.OUTBOX,
                Chaves.skOutbox(evento.ocorridoEm(), evento.eventoId()));
        item.put(NomesTabela.PAYLOAD, Atributos.s(Json.escrever(evento)));
        item.put(NomesTabela.TENTATIVAS, Atributos.n(0));
        return item;
    }

    private static long versaoObrigatoria(Map<Long, Long> versoes, long chave) {
        Long versao = versoes.get(chave);
        if (versao == null || versao < 0) {
            throw new IllegalArgumentException("versão lida do controle ausente ou inválida");
        }
        return versao;
    }

    /** Executa a transação traduzindo cancelamento em conflito concorrente (RN7). */
    private void executar(List<TransactWriteItem> itens) {
        try {
            dynamo.transacao(itens);
        } catch (TransactionCanceledException e) {
            throw new ExcecaoConflitoConcorrente(
                    "RN7: a reserva foi alterada por outra pessoa. Recarregue e tente novamente.", e);
        }
    }
}
