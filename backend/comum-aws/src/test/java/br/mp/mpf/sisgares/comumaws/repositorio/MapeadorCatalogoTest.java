package br.mp.mpf.sisgares.comumaws.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.Recurso;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Ida e volta dos mapeadores de catálogo, chaves e indicadores "S"/"N" (dados fictícios).
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.5</b>
 */
class MapeadorCatalogoTest {

    @Test
    void ambienteIdaEVoltaComChavesDoDesign() {
        RegistroAmbiente r = new RegistroAmbiente(new Ambiente(14207L, "Auditório", true, 10L, "PR/CE"), 10L);
        Map<String, AttributeValue> item = MapeadorCatalogo.paraItem(r);

        assertThat(item.get("PK").s()).isEqualTo("AMBI#14207");
        assertThat(item.get("SK").s()).isEqualTo("META");
        assertThat(item.get("AMBI_ST_ATIVO").s()).isEqualTo("S");
        assertThat(MapeadorCatalogo.deAmbiente(item)).isEqualTo(r);
    }

    @Test
    void indiceDeCatalogoUsaParticaoCatalogoEMesmoConteudo() {
        RegistroAmbiente r = new RegistroAmbiente(new Ambiente(1L, "Sala 1", false, null, "PR/CE"), 1L);
        Map<String, AttributeValue> indice = MapeadorCatalogo.paraIndice(MapeadorCatalogo.paraItem(r), "AMBI", "1");

        assertThat(indice.get("PK").s()).isEqualTo("CATALOGO#AMBI");
        assertThat(indice.get("SK").s()).isEqualTo("1");
        assertThat(MapeadorCatalogo.deAmbiente(indice)).isEqualTo(r);
    }

    @Test
    void recursoSemUnidadeIndicaTodasAsUnidadesELeConjuntoDeAmbientes() {
        RegistroRecurso r = new RegistroRecurso(new Recurso(5L, "Projetor", true, true, 3, null, Set.of()),
                2L, "projetor.svg", null);
        Map<String, AttributeValue> item = new java.util.HashMap<>(MapeadorCatalogo.paraItem(r));

        assertThat(item).doesNotContainKeys("unidade", MapeadorCatalogo.AMBIENTES_VINCULADOS);
        item.put(MapeadorCatalogo.AMBIENTES_VINCULADOS, AttributeValue.fromNs(List.of("10", "11")));

        RegistroRecurso lido = MapeadorCatalogo.deRecurso(item);
        assertThat(lido.recurso().unidade()).isNull();
        assertThat(lido.recurso().ambientesVinculados()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(lido.grupoId()).isEqualTo(2L);
    }

    @Test
    void setorComEmailsAlternativosEEambComCodigoSnp() {
        Setor setor = new Setor(7L, "Apoio", "apoio@exemplo.gov.br", true, "PR/CE",
                List.of("a@exemplo.gov.br", "b@exemplo.gov.br"));
        assertThat(MapeadorCatalogo.deSetor(MapeadorCatalogo.paraItem(setor))).isEqualTo(setor);

        VinculoSetor eamb = new VinculoSetor(3L, 7L, 14207L, "SRV-01");
        Map<String, AttributeValue> item = MapeadorCatalogo.paraItemEamb(eamb);
        assertThat(item.get("SK").s()).isEqualTo("EAMB#7");
        assertThat(MapeadorCatalogo.deEamb(item)).isEqualTo(eamb);
    }

    @Test
    void usuarioIdaEVolta() {
        Usuario u = new Usuario("sub-ficticio-1", Perfil.ATENDENTE, "PR/CE", 7L);
        Map<String, AttributeValue> item = RepositorioUsuario.paraItem(u);
        assertThat(item.get("PK").s()).isEqualTo("USER#sub-ficticio-1");
        assertThat(RepositorioUsuario.deItem(item)).isEqualTo(u);
    }
}
