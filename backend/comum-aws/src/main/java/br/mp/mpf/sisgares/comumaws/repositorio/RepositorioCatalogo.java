package br.mp.mpf.sisgares.comumaws.repositorio;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.UnidadeMacro;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

/**
 * Repositório das tabelas básicas: Unidade_Macro, Ambientes, EAMB/VREC, Disposições, Grupos,
 * Recursos, EREC e Setores (Req. 3.1, 3.2, 3.3).
 *
 * <p><b>Decisão de modelagem — item índice de catálogo.</b> Como Scan é proibido (Req. 3.9) e as
 * entidades de catálogo ficam cada uma em sua partição ({@code AMBI#<id>/META} etc.), cada gravação
 * escreve, na mesma {@code TransactWriteItems}, uma cópia desnormalizada do item em
 * {@code PK=CATALOGO#<tipo>}, {@code SK=<id>} (tipos: {@code UNID}, {@code AMBI}, {@code DISP},
 * {@code GREC}, {@code RECU}, {@code ENVO}). Assim a listagem de um tipo é uma única Query na
 * partição índice, e a leitura por id continua sendo GetItem no item principal. O volume de catálogo
 * é pequeno (centenas de itens), o que mantém a partição índice bem abaixo dos limites de vazão.
 *
 * <p><b>Vínculos.</b> EAMB e VREC ficam em {@code AMBI#<id>} ({@code EAMB#<envoId>} /
 * {@code VREC#<recuId>}) e EREC em {@code RECU#<id>} ({@code EREC#<envoId>}), lidos por Query com
 * {@code begins_with}. O conjunto {@code ambientesVinculados} (NS) do Recurso é mantido por
 * {@link #vincularRecursoAmbiente} / {@link #desvincularRecursoAmbiente}; por isso a gravação do
 * Recurso usa Update (preserva esse conjunto) e o Recurso precisa existir antes do vínculo.
 */
public final class RepositorioCatalogo {

    /** Prefixo da partição índice de catálogo. */
    public static final String PREFIXO_CATALOGO = "CATALOGO#";

    static final String TIPO_UNIDADE = "UNID";
    static final String TIPO_AMBIENTE = "AMBI";
    static final String TIPO_DISPOSICAO = "DISP";
    static final String TIPO_GRUPO = "GREC";
    static final String TIPO_RECURSO = "RECU";
    static final String TIPO_SETOR = "ENVO";

    private final OperacoesDynamo dynamo;

    public RepositorioCatalogo(DynamoDbClient cliente, String nomeTabela) {
        this.dynamo = new OperacoesDynamo(cliente, nomeTabela);
    }

    // ---------- Unidade_Macro ----------

    public void salvarUnidade(UnidadeMacro unidade) {
        gravarComIndice(MapeadorCatalogo.paraItem(unidade), TIPO_UNIDADE, unidade.codigo());
    }

    public Optional<UnidadeMacro> obterUnidade(String codigo) {
        return obterMeta(Chaves.pkUnidade(codigo), MapeadorCatalogo::deUnidade);
    }

    public List<UnidadeMacro> listarUnidades() {
        return listar(TIPO_UNIDADE, MapeadorCatalogo::deUnidade);
    }

    // ---------- Ambiente ----------

    public void salvarAmbiente(RegistroAmbiente ambiente) {
        gravarComIndice(MapeadorCatalogo.paraItem(ambiente), TIPO_AMBIENTE,
                Long.toString(ambiente.ambiente().id()));
    }

    public Optional<RegistroAmbiente> obterAmbiente(long id) {
        return obterMeta(Chaves.pkAmbiente(id), MapeadorCatalogo::deAmbiente);
    }

    public List<RegistroAmbiente> listarAmbientes() {
        return listar(TIPO_AMBIENTE, MapeadorCatalogo::deAmbiente);
    }

    // ---------- EAMB ----------

    public void vincularSetorAmbiente(VinculoSetor eamb) {
        dynamo.gravar(MapeadorCatalogo.paraItemEamb(eamb));
    }

    public void desvincularSetorAmbiente(long ambienteId, long envoId) {
        dynamo.transacao(List.of(dynamo.delete(Chaves.pkAmbiente(ambienteId), Chaves.skEamb(envoId))));
    }

    /** Setores do ambiente: Query {@code PK=AMBI#id}, {@code begins_with(SK, EAMB#)}. */
    public List<VinculoSetor> listarSetoresDoAmbiente(long ambienteId) {
        return dynamo.consultar(Chaves.pkAmbiente(ambienteId), Chaves.EAMB).stream()
                .map(MapeadorCatalogo::deEamb).toList();
    }

    // ---------- VREC ----------

    /** Grava o VREC e acrescenta o ambiente ao conjunto do Recurso (principal e índice) atomicamente. */
    public void vincularRecursoAmbiente(VinculoRecursoAmbiente vrec) {
        alterarVrec(vrec.recursoId(), vrec.ambienteId(), true,
                dynamo.put(MapeadorCatalogo.paraItem(vrec)));
    }

    public void desvincularRecursoAmbiente(long recursoId, long ambienteId) {
        alterarVrec(recursoId, ambienteId, false,
                dynamo.delete(Chaves.pkAmbiente(ambienteId), Chaves.skVrec(recursoId)));
    }

    /** Recursos vinculados ao ambiente: Query {@code PK=AMBI#id}, {@code begins_with(SK, VREC#)}. */
    public List<VinculoRecursoAmbiente> listarRecursosDoAmbiente(long ambienteId) {
        return dynamo.consultar(Chaves.pkAmbiente(ambienteId), Chaves.VREC).stream()
                .map(MapeadorCatalogo::deVrec).toList();
    }

    private void alterarVrec(long recursoId, long ambienteId, boolean adicionar,
                             TransactWriteItem itemVrec) {
        String recuId = Long.toString(recursoId);
        dynamo.transacao(List.of(
                itemVrec,
                dynamo.alterarConjunto(Chaves.pkRecurso(recursoId), Chaves.META,
                        MapeadorCatalogo.AMBIENTES_VINCULADOS, ambienteId, adicionar),
                dynamo.alterarConjunto(PREFIXO_CATALOGO + TIPO_RECURSO, recuId,
                        MapeadorCatalogo.AMBIENTES_VINCULADOS, ambienteId, adicionar)));
    }

    // ---------- Disposição ----------

    public void salvarDisposicao(Disposicao disposicao) {
        gravarComIndice(MapeadorCatalogo.paraItem(disposicao), TIPO_DISPOSICAO, Long.toString(disposicao.id()));
    }

    public Optional<Disposicao> obterDisposicao(long id) {
        return obterMeta(Chaves.pkDisposicao(id), MapeadorCatalogo::deDisposicao);
    }

    public List<Disposicao> listarDisposicoes() {
        return listar(TIPO_DISPOSICAO, MapeadorCatalogo::deDisposicao);
    }

    // ---------- Grupo ----------

    public void salvarGrupo(GrupoRecurso grupo) {
        gravarComIndice(MapeadorCatalogo.paraItem(grupo), TIPO_GRUPO, Long.toString(grupo.id()));
    }

    public Optional<GrupoRecurso> obterGrupo(long id) {
        return obterMeta(Chaves.pkGrupo(id), MapeadorCatalogo::deGrupo);
    }

    public List<GrupoRecurso> listarGrupos() {
        return listar(TIPO_GRUPO, MapeadorCatalogo::deGrupo);
    }

    // ---------- Recurso ----------

    /**
     * Grava o Recurso com Update (SET dos atributos, REMOVE dos opcionais ausentes) no item principal e
     * no índice, preservando {@code ambientesVinculados}. O conjunto do record é ignorado aqui.
     */
    public void salvarRecurso(RegistroRecurso recurso) {
        Map<String, AttributeValue> campos = MapeadorCatalogo.paraItem(recurso);
        String id = Long.toString(recurso.recurso().id());
        dynamo.transacao(List.of(
                dynamo.atualizar(Chaves.pkRecurso(recurso.recurso().id()), Chaves.META, campos,
                        MapeadorCatalogo.OPCIONAIS_RECURSO, false),
                dynamo.atualizar(PREFIXO_CATALOGO + TIPO_RECURSO, id, campos,
                        MapeadorCatalogo.OPCIONAIS_RECURSO, false)));
    }

    public Optional<RegistroRecurso> obterRecurso(long id) {
        return obterMeta(Chaves.pkRecurso(id), MapeadorCatalogo::deRecurso);
    }

    public List<RegistroRecurso> listarRecursos() {
        return listar(TIPO_RECURSO, MapeadorCatalogo::deRecurso);
    }

    // ---------- EREC ----------

    public void vincularSetorRecurso(VinculoSetor erec) {
        dynamo.gravar(MapeadorCatalogo.paraItemErec(erec));
    }

    public void desvincularSetorRecurso(long recursoId, long envoId) {
        dynamo.transacao(List.of(dynamo.delete(Chaves.pkRecurso(recursoId), Chaves.skErec(envoId))));
    }

    /** Setores do recurso: Query {@code PK=RECU#id}, {@code begins_with(SK, EREC#)}. */
    public List<VinculoSetor> listarSetoresDoRecurso(long recursoId) {
        return dynamo.consultar(Chaves.pkRecurso(recursoId), Chaves.EREC).stream()
                .map(MapeadorCatalogo::deErec).toList();
    }

    // ---------- Setor ----------

    public void salvarSetor(Setor setor) {
        gravarComIndice(MapeadorCatalogo.paraItem(setor), TIPO_SETOR, Long.toString(setor.id()));
    }

    public Optional<Setor> obterSetor(long id) {
        return obterMeta(Chaves.pkSetor(id), MapeadorCatalogo::deSetor);
    }

    public List<Setor> listarSetores() {
        return listar(TIPO_SETOR, MapeadorCatalogo::deSetor);
    }

    // ---------- Auxiliares ----------

    /** Put do item principal e do item índice na mesma transação. */
    private void gravarComIndice(Map<String, AttributeValue> principal, String tipo, String id) {
        dynamo.transacao(List.of(
                dynamo.put(principal),
                dynamo.put(MapeadorCatalogo.paraIndice(principal, tipo, id))));
    }

    private <T> Optional<T> obterMeta(String pk, Function<Map<String, AttributeValue>, T> mapeador) {
        return dynamo.obter(pk, Chaves.META).map(mapeador);
    }

    /** Query na partição índice {@code CATALOGO#<tipo>}. */
    private <T> List<T> listar(String tipo, Function<Map<String, AttributeValue>, T> mapeador) {
        return dynamo.consultar(PREFIXO_CATALOGO + tipo).stream().map(mapeador).toList();
    }
}
