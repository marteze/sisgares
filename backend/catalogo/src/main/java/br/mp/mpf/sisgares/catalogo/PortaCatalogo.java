package br.mp.mpf.sisgares.catalogo;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência do catálogo usada pelo {@link ServicoCatalogo}.
 * Implementada pelo {@link AdaptadorDynamo} (produção) e por um repositório em memória (testes).
 */
public interface PortaCatalogo {

    /** Sequências de ids por entidade (contador atômico). */
    String SEQ_SETOR = "ENVO";
    String SEQ_AMBIENTE = "AMBI";
    String SEQ_DISPOSICAO = "DISP";
    String SEQ_GRUPO = "GREC";
    String SEQ_RECURSO = "RECU";
    String SEQ_EAMB = "EAMB";
    String SEQ_EREC = "EREC";
    String SEQ_VREC = "VREC";

    /** Próximo id da sequência informada. */
    long proximoId(String sequencia);

    void salvarSetor(Setor setor);

    Optional<Setor> obterSetor(long id);

    List<Setor> listarSetores();

    void salvarAmbiente(RegistroAmbiente ambiente);

    Optional<RegistroAmbiente> obterAmbiente(long id);

    List<RegistroAmbiente> listarAmbientes();

    void salvarDisposicao(Disposicao disposicao);

    Optional<Disposicao> obterDisposicao(long id);

    List<Disposicao> listarDisposicoes();

    void salvarGrupo(GrupoRecurso grupo);

    Optional<GrupoRecurso> obterGrupo(long id);

    List<GrupoRecurso> listarGrupos();

    /** Grava o Recurso preservando o conjunto de ambientes vinculados (VREC). */
    void salvarRecurso(RegistroRecurso recurso);

    Optional<RegistroRecurso> obterRecurso(long id);

    List<RegistroRecurso> listarRecursos();

    // ---------- Vínculos ----------

    void vincularSetorAmbiente(VinculoSetor eamb);

    void desvincularSetorAmbiente(long ambienteId, long setorId);

    List<VinculoSetor> listarSetoresDoAmbiente(long ambienteId);

    void vincularSetorRecurso(VinculoSetor erec);

    void desvincularSetorRecurso(long recursoId, long setorId);

    List<VinculoSetor> listarSetoresDoRecurso(long recursoId);

    void vincularRecursoAmbiente(VinculoRecursoAmbiente vrec);

    void desvincularRecursoAmbiente(long recursoId, long ambienteId);
}
