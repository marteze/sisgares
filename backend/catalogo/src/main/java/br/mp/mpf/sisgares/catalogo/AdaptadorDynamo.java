package br.mp.mpf.sisgares.catalogo;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/** {@link PortaCatalogo} sobre o {@link RepositorioCatalogo} (single-table, sem Scan). */
public final class AdaptadorDynamo implements PortaCatalogo {

    /** Partição do contador atômico ({@code PK=SEQ#<entidade>}, {@code SK=META}). */
    static final String PREFIXO_SEQUENCIA = "SEQ#";
    static final String ATRIBUTO_VALOR = "valor";
    /** Ids gerados começam acima da base para não colidir com os ids carregados pelo seed (CSVs). */
    static final long BASE_SEQUENCIA = 1_000_000L;

    private final DynamoDbClient cliente;
    private final String tabela;
    private final RepositorioCatalogo repositorio;

    public AdaptadorDynamo(DynamoDbClient cliente, String tabela) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
        this.repositorio = new RepositorioCatalogo(cliente, tabela);
    }

    /** {@code SET valor = if_not_exists(valor, :base) + 1}: atômico. */
    @Override
    public long proximoId(String sequencia) {
        var resposta = cliente.updateItem(UpdateItemRequest.builder()
                .tableName(tabela)
                .key(Map.of("PK", AttributeValue.fromS(PREFIXO_SEQUENCIA + sequencia),
                        "SK", AttributeValue.fromS(Chaves.META)))
                .updateExpression("SET #v = if_not_exists(#v, :base) + :um")
                .expressionAttributeNames(Map.of("#v", ATRIBUTO_VALOR))
                .expressionAttributeValues(Map.of(
                        ":base", AttributeValue.fromN(Long.toString(BASE_SEQUENCIA)),
                        ":um", AttributeValue.fromN("1")))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build());
        return Long.parseLong(resposta.attributes().get(ATRIBUTO_VALOR).n());
    }

    @Override public void salvarSetor(Setor setor) { repositorio.salvarSetor(setor); }
    @Override public Optional<Setor> obterSetor(long id) { return repositorio.obterSetor(id); }
    @Override public List<Setor> listarSetores() { return repositorio.listarSetores(); }

    @Override public void salvarAmbiente(RegistroAmbiente a) { repositorio.salvarAmbiente(a); }
    @Override public Optional<RegistroAmbiente> obterAmbiente(long id) { return repositorio.obterAmbiente(id); }
    @Override public List<RegistroAmbiente> listarAmbientes() { return repositorio.listarAmbientes(); }

    @Override public void salvarDisposicao(Disposicao d) { repositorio.salvarDisposicao(d); }
    @Override public Optional<Disposicao> obterDisposicao(long id) { return repositorio.obterDisposicao(id); }
    @Override public List<Disposicao> listarDisposicoes() { return repositorio.listarDisposicoes(); }

    @Override public void salvarGrupo(GrupoRecurso g) { repositorio.salvarGrupo(g); }
    @Override public Optional<GrupoRecurso> obterGrupo(long id) { return repositorio.obterGrupo(id); }
    @Override public List<GrupoRecurso> listarGrupos() { return repositorio.listarGrupos(); }

    @Override public void salvarRecurso(RegistroRecurso r) { repositorio.salvarRecurso(r); }
    @Override public Optional<RegistroRecurso> obterRecurso(long id) { return repositorio.obterRecurso(id); }
    @Override public List<RegistroRecurso> listarRecursos() { return repositorio.listarRecursos(); }

    @Override public void vincularSetorAmbiente(VinculoSetor v) { repositorio.vincularSetorAmbiente(v); }

    @Override
    public void desvincularSetorAmbiente(long ambienteId, long setorId) {
        repositorio.desvincularSetorAmbiente(ambienteId, setorId);
    }

    @Override
    public List<VinculoSetor> listarSetoresDoAmbiente(long ambienteId) {
        return repositorio.listarSetoresDoAmbiente(ambienteId);
    }

    @Override public void vincularSetorRecurso(VinculoSetor v) { repositorio.vincularSetorRecurso(v); }

    @Override
    public void desvincularSetorRecurso(long recursoId, long setorId) {
        repositorio.desvincularSetorRecurso(recursoId, setorId);
    }

    @Override
    public List<VinculoSetor> listarSetoresDoRecurso(long recursoId) {
        return repositorio.listarSetoresDoRecurso(recursoId);
    }

    @Override public void vincularRecursoAmbiente(VinculoRecursoAmbiente v) { repositorio.vincularRecursoAmbiente(v); }

    @Override
    public void desvincularRecursoAmbiente(long recursoId, long ambienteId) {
        repositorio.desvincularRecursoAmbiente(recursoId, ambienteId);
    }
}
