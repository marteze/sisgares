package br.mp.mpf.sisgares.catalogo;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.dominio.Recurso;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** {@link PortaCatalogo} e {@link ArmazenamentoImagens} em memória, com a mesma semântica do Dynamo. */
final class MemoriaCatalogo implements PortaCatalogo, ArmazenamentoImagens {

    final Map<String, Long> sequencias = new HashMap<>();
    final Map<Long, Setor> setores = new LinkedHashMap<>();
    final Map<Long, RegistroAmbiente> ambientes = new LinkedHashMap<>();
    final Map<Long, Disposicao> disposicoes = new LinkedHashMap<>();
    final Map<Long, GrupoRecurso> grupos = new LinkedHashMap<>();
    final Map<Long, RegistroRecurso> recursos = new LinkedHashMap<>();
    final List<VinculoSetor> eamb = new ArrayList<>();
    final List<VinculoSetor> erec = new ArrayList<>();
    final Map<String, byte[]> objetos = new HashMap<>();

    @Override
    public long proximoId(String sequencia) {
        return sequencias.merge(sequencia, 1L, Long::sum) + 1000;
    }

    @Override public void salvarSetor(Setor s) { setores.put(s.id(), s); }
    @Override public Optional<Setor> obterSetor(long id) { return Optional.ofNullable(setores.get(id)); }
    @Override public List<Setor> listarSetores() { return List.copyOf(setores.values()); }

    @Override public void salvarAmbiente(RegistroAmbiente a) { ambientes.put(a.ambiente().id(), a); }
    @Override public Optional<RegistroAmbiente> obterAmbiente(long id) { return Optional.ofNullable(ambientes.get(id)); }
    @Override public List<RegistroAmbiente> listarAmbientes() { return List.copyOf(ambientes.values()); }

    @Override public void salvarDisposicao(Disposicao d) { disposicoes.put(d.id(), d); }
    @Override public Optional<Disposicao> obterDisposicao(long id) { return Optional.ofNullable(disposicoes.get(id)); }
    @Override public List<Disposicao> listarDisposicoes() { return List.copyOf(disposicoes.values()); }

    @Override public void salvarGrupo(GrupoRecurso g) { grupos.put(g.id(), g); }
    @Override public Optional<GrupoRecurso> obterGrupo(long id) { return Optional.ofNullable(grupos.get(id)); }
    @Override public List<GrupoRecurso> listarGrupos() { return List.copyOf(grupos.values()); }

    /** Como no Dynamo, o conjunto de ambientes vinculados existente é preservado. */
    @Override
    public void salvarRecurso(RegistroRecurso r) {
        RegistroRecurso atual = recursos.get(r.recurso().id());
        Set<Long> vinculados = atual == null ? Set.of() : atual.recurso().ambientesVinculados();
        recursos.put(r.recurso().id(), comAmbientes(r, vinculados));
    }

    @Override public Optional<RegistroRecurso> obterRecurso(long id) { return Optional.ofNullable(recursos.get(id)); }
    @Override public List<RegistroRecurso> listarRecursos() { return List.copyOf(recursos.values()); }

    @Override public void vincularSetorAmbiente(VinculoSetor v) { eamb.add(v); }

    @Override
    public void desvincularSetorAmbiente(long ambienteId, long setorId) {
        eamb.removeIf(v -> v.alvoId() == ambienteId && v.envoId() == setorId);
    }

    @Override
    public List<VinculoSetor> listarSetoresDoAmbiente(long ambienteId) {
        return eamb.stream().filter(v -> v.alvoId() == ambienteId).toList();
    }

    @Override public void vincularSetorRecurso(VinculoSetor v) { erec.add(v); }

    @Override
    public void desvincularSetorRecurso(long recursoId, long setorId) {
        erec.removeIf(v -> v.alvoId() == recursoId && v.envoId() == setorId);
    }

    @Override
    public List<VinculoSetor> listarSetoresDoRecurso(long recursoId) {
        return erec.stream().filter(v -> v.alvoId() == recursoId).toList();
    }

    @Override
    public void vincularRecursoAmbiente(VinculoRecursoAmbiente v) {
        alterarVrec(v.recursoId(), v.ambienteId(), true);
    }

    @Override
    public void desvincularRecursoAmbiente(long recursoId, long ambienteId) {
        alterarVrec(recursoId, ambienteId, false);
    }

    private void alterarVrec(long recursoId, long ambienteId, boolean adicionar) {
        RegistroRecurso r = recursos.get(recursoId);
        Set<Long> conjunto = new HashSet<>(r.recurso().ambientesVinculados());
        if (adicionar) {
            conjunto.add(ambienteId);
        } else {
            conjunto.remove(ambienteId);
        }
        recursos.put(recursoId, comAmbientes(r, conjunto));
    }

    private static RegistroRecurso comAmbientes(RegistroRecurso r, Set<Long> ambientes) {
        Recurso x = r.recurso();
        return new RegistroRecurso(new Recurso(x.id(), x.descricao(), x.ativo(), x.limitado(), x.disponibilidade(),
                x.unidade(), ambientes), r.grupoId(), r.iconeArquivo(), r.iconeRefOriginal());
    }

    @Override
    public void gravar(String chave, byte[] conteudo, String contentType) {
        objetos.put(chave, conteudo);
    }
}
