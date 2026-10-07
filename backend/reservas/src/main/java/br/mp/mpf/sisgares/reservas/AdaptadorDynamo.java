package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.comumaws.repositorio.GravadorReservas;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioConfiguracao;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioReservas;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Recurso;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.SolicitacaoOcupada;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Implementação DynamoDB de {@link PortaReservas} e {@link PortaCatalogo}.
 *
 * <p>Delega aos repositórios de {@code comum-aws}. Os três acessos que eles ainda não oferecem
 * (contador de ids, {@code criadoEm} do META e snapshots {@code VERS#}) usam o cliente diretamente,
 * sempre com Query/GetItem/UpdateItem parametrizados (sem Scan).
 */
public final class AdaptadorDynamo implements PortaReservas, PortaCatalogo {

    /** Prefixo da partição do contador atômico ({@code PK=SEQ#RESE}, {@code PK=SEQ#PRES}). */
    static final String PREFIXO_SEQUENCIA = "SEQ#";
    static final String ATRIBUTO_VALOR = "valor";
    /**
     * Base do contador: ids gerados começam acima dela para não colidir com os RESE_ID/PRES_ID
     * carregados pelo seed a partir dos CSVs.
     */
    static final long BASE_SEQUENCIA = 1_000_000L;

    private final DynamoDbClient cliente;
    private final String tabela;
    private final RepositorioReservas repositorio;
    private final GravadorReservas gravador;
    private final RepositorioCatalogo repositorioCatalogo;
    private final RepositorioConfiguracao repositorioConfiguracao;
    private final ObjectReader leitorReserva = Json.mapper().readerFor(Reserva.class)
            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AdaptadorDynamo(DynamoDbClient cliente, String tabela, Clock relogio) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
        this.repositorio = new RepositorioReservas(cliente, tabela);
        this.gravador = new GravadorReservas(cliente, tabela);
        this.repositorioCatalogo = new RepositorioCatalogo(cliente, tabela);
        this.repositorioConfiguracao = new RepositorioConfiguracao(cliente, tabela, relogio);
    }

    // ---------- PortaReservas ----------

    /** {@code SET valor = if_not_exists(valor, :base) + :n}: atômico, devolve o primeiro id do bloco. */
    @Override
    public long reservarIds(String sequencia, int quantidade) {
        if (quantidade < 1) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        var resposta = cliente.updateItem(UpdateItemRequest.builder()
                .tableName(tabela)
                .key(chave(PREFIXO_SEQUENCIA + sequencia, Chaves.META))
                .updateExpression("SET #v = if_not_exists(#v, :base) + :n")
                .expressionAttributeNames(Map.of("#v", ATRIBUTO_VALOR))
                .expressionAttributeValues(Map.of(
                        ":base", AttributeValue.fromN(Long.toString(BASE_SEQUENCIA)),
                        ":n", AttributeValue.fromN(Integer.toString(quantidade))))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build());
        long ultimo = Long.parseLong(resposta.attributes().get(ATRIBUTO_VALOR).n());
        return ultimo - quantidade + 1;
    }

    @Override
    public Optional<Reserva> obter(long id) {
        return repositorio.obter(id);
    }

    @Override
    public Optional<LocalDateTime> criadoEm(long id) {
        GetItemResponse resposta = cliente.getItem(GetItemRequest.builder()
                .tableName(tabela)
                .key(chave(Chaves.pkReserva(id), Chaves.META))
                .projectionExpression("#c")
                .expressionAttributeNames(Map.of("#c", NomesTabela.CRIADO_EM))
                .build());
        if (!resposta.hasItem() || resposta.item().get(NomesTabela.CRIADO_EM) == null) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.parse(resposta.item().get(NomesTabela.CRIADO_EM).s()));
    }

    @Override
    public List<Reserva> porSolicitante(String sub) {
        return repositorio.porSolicitante(sub);
    }

    @Override
    public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
        return repositorio.porUnidade(unidade, de, ate);
    }

    @Override
    public List<PeriodoOcupado> periodosOcupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim) {
        return repositorio.periodosOcupadosPorRaiz(raizId, ini, fim);
    }

    @Override
    public List<SolicitacaoOcupada> solicitacoesOcupadasPorRecurso(long recursoId, LocalDateTime ini,
                                                                   LocalDateTime fim) {
        return repositorio.solicitacoesOcupadasPorRecurso(recursoId, ini, fim);
    }

    @Override
    public long lerVersaoControle(String pk) {
        return gravador.lerVersaoControle(pk);
    }

    @Override
    public void gravar(Reserva r, LocalDateTime criadoEm, Long raizId, Set<Long> setores,
                       Map<Long, Long> versoesCtrl, Long versaoLida, EventoOutbox evento) {
        gravador.gravar(r, criadoEm, raizId, setores, versoesCtrl, versaoLida, evento);
    }

    @Override
    public void removerOutbox(EventoOutbox evento) {
        gravador.removerOutbox(evento);
    }

    @Override
    public List<EventoOutbox> listarOutboxPendentes() {
        return gravador.listarOutboxPendentes();
    }

    /** Query {@code PK = RESE#id AND begins_with(SK, "VERS#")}, todas as páginas. */
    @Override
    public List<VersaoReserva> versoes(long id) {
        List<VersaoReserva> versoes = new ArrayList<>();
        Map<String, AttributeValue> inicio = null;
        do {
            QueryRequest.Builder req = QueryRequest.builder()
                    .tableName(tabela)
                    .keyConditionExpression("#pk = :pk AND begins_with(#sk, :prefixo)")
                    .expressionAttributeNames(Map.of("#pk", NomesTabela.PK, "#sk", NomesTabela.SK))
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.fromS(Chaves.pkReserva(id)),
                            ":prefixo", AttributeValue.fromS(Chaves.VERS)));
            if (inicio != null) {
                req.exclusiveStartKey(inicio);
            }
            QueryResponse resposta = cliente.query(req.build());
            for (Map<String, AttributeValue> item : resposta.items()) {
                AttributeValue payload = item.get(NomesTabela.PAYLOAD);
                if (payload != null && payload.s() != null) {
                    Reserva r = lerReserva(payload.s());
                    versoes.add(new VersaoReserva(r.versao(), r));
                }
            }
            inicio = resposta.hasLastEvaluatedKey() && !resposta.lastEvaluatedKey().isEmpty()
                    ? resposta.lastEvaluatedKey() : null;
        } while (inicio != null);
        versoes.sort(Comparator.comparingLong(VersaoReserva::numero));
        return versoes;
    }

    // ---------- PortaCatalogo ----------

    @Override
    public List<Ambiente> ambientes() {
        return repositorioCatalogo.listarAmbientes().stream().map(RegistroAmbiente::ambiente).toList();
    }

    @Override
    public Optional<Recurso> recurso(long id) {
        return repositorioCatalogo.obterRecurso(id).map(RegistroRecurso::recurso);
    }

    @Override
    public Set<Long> setoresDoAmbiente(long ambienteId) {
        return envos(repositorioCatalogo.listarSetoresDoAmbiente(ambienteId));
    }

    @Override
    public Set<Long> setoresDoRecurso(long recursoId) {
        return envos(repositorioCatalogo.listarSetoresDoRecurso(recursoId));
    }

    @Override
    public Configuracao configuracao() {
        return repositorioConfiguracao.obter();
    }

    // ---------- Auxiliares ----------

    private Reserva lerReserva(String payload) {
        try {
            return leitorReserva.readValue(payload);
        } catch (IOException e) {
            // Não expõe o payload (pode conter dados pessoais)
            throw new UncheckedIOException("Snapshot de versão inválido", e);
        }
    }

    private static Set<Long> envos(List<VinculoSetor> vinculos) {
        Set<Long> ids = new LinkedHashSet<>();
        vinculos.forEach(v -> ids.add(v.envoId()));
        return ids;
    }

    private static Map<String, AttributeValue> chave(String pk, String sk) {
        Map<String, AttributeValue> chave = new HashMap<>();
        chave.put(NomesTabela.PK, AttributeValue.fromS(pk));
        chave.put(NomesTabela.SK, AttributeValue.fromS(sk));
        return chave;
    }
}
