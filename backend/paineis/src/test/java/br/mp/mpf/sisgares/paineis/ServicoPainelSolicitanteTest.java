package br.mp.mpf.sisgares.paineis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.CalculadoraGrade;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Exemplos do Painel do Solicitante com fonte de dados em memória (sem AWS).
 *
 * <p>**Validates: Requirements 15.1, 15.4, 15.5, 6.17**
 */
class ServicoPainelSolicitanteTest {

    private static final LocalDate DIA = LocalDate.of(2030, 3, 4); // segunda-feira
    private static final String DONO = "sub-dono";

    /** Raiz 1 com filho 2; ocupado no filho (Ambiente_Relacionado) às 10:00–11:00. */
    private final ServicoPainelSolicitante.FonteDados fonte = new ServicoPainelSolicitante.FonteDados() {
        private final List<Ambiente> ambientes = List.of(
                new Ambiente(1, "Auditório", true, null, "PR/CE"),
                new Ambiente(2, "Sala A", true, 1L, "PR/CE"));

        @Override
        public Optional<Ambiente> ambiente(long id) {
            return ambientes.stream().filter(a -> a.id() == id).findFirst();
        }

        @Override
        public List<Ambiente> ambientesDaUnidade(String unidade) {
            return ambientes;
        }

        @Override
        public List<PeriodoOcupado> ocupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim) {
            return List.of(new PeriodoOcupado(99, 2,
                    Periodo.de(DIA.atTime(10, 0), DIA.atTime(11, 0))));
        }

        @Override
        public Optional<String> solicitante(long reservaId) {
            return reservaId == 99 ? Optional.of(DONO) : Optional.empty();
        }

        @Override
        public Configuracao configuracao() {
            return new Configuracao(0, new Configuracao.FaixaHoraria(LocalTime.of(8, 0), LocalTime.of(18, 0)),
                    Map.of());
        }
    };

    private final ServicoPainelSolicitante servico = new ServicoPainelSolicitante(fonte,
            new CalculadoraGrade(Clock.fixed(DIA.minusDays(7).atStartOfDay(ConstantesDominio.ZONA).toInstant(),
                    ConstantesDominio.ZONA)));

    private ServicoPainelSolicitante.Celula celula10h(Principal usuario) {
        var consulta = ServicoPainelSolicitante.lerParametros(Map.of("ambiente", "1", "data", DIA.toString(),
                "colunas", "1"));
        return servico.consultar(consulta, usuario).colunas().get(0).celulas().stream()
                .filter(c -> c.inicio().equals("10:00")).findFirst().orElseThrow();
    }

    @Test
    void donoVeLinkDaReservaDeAmbienteRelacionado() {
        var c = celula10h(new Principal(DONO, null, List.of("Solicitante"), "PR/CE", null));
        assertThat(c.estado()).isEqualTo("OCUPADO");
        assertThat(c.reservaId()).isEqualTo("99");
    }

    @Test
    void administradorVeLink() {
        var c = celula10h(new Principal("sub-admin", null, List.of("Administrador"), "PR/CE", null));
        assertThat(c.reservaId()).isEqualTo("99");
    }

    @Test
    void outroSolicitanteVeSoOcupadoSemLink() {
        var c = celula10h(new Principal("sub-outro", null, List.of("Solicitante"), "PR/CE", null));
        assertThat(c.estado()).isEqualTo("OCUPADO");
        assertThat(c.reservaId()).isNull();
    }

    @Test
    void parametrosInvalidosAcumulamViolacoes() {
        assertThatThrownBy(() -> ServicoPainelSolicitante.lerParametros(
                Map.of("ambiente", "x", "data", "04/03/2030", "colunas", "15", "fds", "talvez")))
                .isInstanceOfSatisfying(ExcecaoValidacao.class,
                        e -> assertThat(e.violacoes()).hasSize(4));
    }

    @Test
    void ultimaDataPulaFimDeSemana() {
        var p = new br.mp.mpf.sisgares.dominio.ParametrosGrade(1, "PR/CE", DIA, 7, false);
        assertThat(ServicoPainelSolicitante.ultimaData(p)).isEqualTo(DIA.plusDays(8));
    }
}
