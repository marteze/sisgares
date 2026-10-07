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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link DestinoSeed} em memória para os testes do seed. Cada operação grava o item sob a sua
 * chave (como um {@code PutItem} por PK/SK no Dynamo): regravar a mesma chave substitui o valor,
 * sem duplicar. Assim, duas execuções com os mesmos CSVs resultam no mesmo estado, o que permite
 * verificar a idempotência (Requisito 4.12).
 */
final class DestinoMemoria implements DestinoSeed {

    final Map<String, Object> itens = new LinkedHashMap<>();

    /** Total de gravações (PutItem) realizadas, inclusive sobrescritas. */
    int gravacoes;

    private void gravar(String chave, Object valor) {
        itens.put(chave, valor);
        gravacoes++;
    }

    @Override
    public void salvarUnidade(UnidadeMacro unidade) {
        gravar("UNID#" + unidade.codigo(), unidade);
    }

    @Override
    public void salvarConfiguracao(Configuracao configuracao, String snpUrl) {
        gravar("CONFIG", new ConfiguracaoGravada(configuracao, snpUrl));
    }

    /** Valor com igualdade estrutural para a Configuração padrão gravada (Req. 4.8). */
    record ConfiguracaoGravada(Configuracao configuracao, String snpUrl) {
    }

    @Override
    public void salvarUsuario(Usuario usuario) {
        gravar("USER#" + usuario.sub(), usuario);
    }

    @Override
    public void salvarGrupo(GrupoRecurso grupo) {
        gravar("GREC#" + grupo.id(), grupo);
    }

    @Override
    public void salvarDisposicao(Disposicao disposicao) {
        gravar("DISP#" + disposicao.id(), disposicao);
    }

    @Override
    public void salvarAmbiente(RegistroAmbiente ambiente) {
        gravar("AMBI#" + ambiente.ambiente().id(), ambiente);
    }

    @Override
    public void salvarSetor(Setor setor) {
        gravar("ENVO#" + setor.id(), setor);
    }

    @Override
    public void salvarRecurso(RegistroRecurso recurso) {
        gravar("RECU#" + recurso.recurso().id(), recurso);
    }

    @Override
    public void vincularSetorAmbiente(VinculoSetor eamb) {
        gravar("EAMB#" + eamb.id(), eamb);
    }

    @Override
    public void vincularSetorRecurso(VinculoSetor erec) {
        gravar("EREC#" + erec.id(), erec);
    }

    @Override
    public void vincularRecursoAmbiente(VinculoRecursoAmbiente vrec) {
        gravar("VREC#" + vrec.id(), vrec);
    }

    @Override
    public void salvarTextoAlternativo(String nomeImagem, String categoria, String alt) {
        gravar("ALT#" + categoria + "#" + nomeImagem, alt);
    }

    @Override
    public void salvarReserva(ReservaSeed reserva) {
        gravar("RESE#" + reserva.reserva().id(), reserva);
    }
}
