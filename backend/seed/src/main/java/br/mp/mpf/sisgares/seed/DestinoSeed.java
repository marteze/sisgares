package br.mp.mpf.sisgares.seed;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.UnidadeMacro;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.comumaws.repositorio.Usuario;
import br.mp.mpf.sisgares.dominio.Configuracao;

/**
 * Destino da carga do seed. Toda operação deve ser idempotente: gravar duas vezes o mesmo
 * registro (mesmos IDs) resulta no mesmo item (PutItem/Update por chave), sem duplicar
 * (Requisito 4.12).
 */
public interface DestinoSeed {

    void salvarUnidade(UnidadeMacro unidade);

    void salvarConfiguracao(Configuracao configuracao, String snpUrl);

    void salvarUsuario(Usuario usuario);

    void salvarGrupo(GrupoRecurso grupo);

    void salvarDisposicao(Disposicao disposicao);

    void salvarAmbiente(RegistroAmbiente ambiente);

    void salvarSetor(Setor setor);

    /** Recurso precisa existir antes dos vínculos VREC. */
    void salvarRecurso(RegistroRecurso recurso);

    void vincularSetorAmbiente(VinculoSetor eamb);

    void vincularSetorRecurso(VinculoSetor erec);

    void vincularRecursoAmbiente(VinculoRecursoAmbiente vrec);

    /**
     * Texto alternativo ({@code alt}) da Imagem_Ícone (Requisito 4.17).
     *
     * @param nomeImagem nome do arquivo (ex.: {@code equip_005_projetor-multimidia.png})
     * @param categoria  pasta de origem (ex.: {@code icones-recurso})
     * @param alt        texto alternativo
     */
    void salvarTextoAlternativo(String nomeImagem, String categoria, String alt);

    /** Reserva com Períodos e Solicitações, preservando RESE_ID, PRES_ID e SOLI_ID. */
    void salvarReserva(ReservaSeed reserva);
}
