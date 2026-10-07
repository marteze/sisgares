package br.mp.mpf.sisgares.paineis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Exemplos do Painel do Atendente com fonte de dados em memória (sem AWS).
 *
 * <p>**Validates: Requirements 16.2, 16.3, 16.4, 16.5, 16.6**
 */
class ServicoPainelAtendenteTest {

    private static final LocalDate DIA = LocalDate.of(2030, 3, 4); // segunda-feira
    private static final long SETOR = 7;

    /** Reserva 1 (setor 7): dois Períodos no DIA, fora de ordem; Reserva 2 (outro setor) cancelada. */
    private final Reserva r1 = new Reserva(1L, "PR/CE", "sub-1", 10L, null, null, "Reunião", 10,
            List.of(new Periodo(1L, DIA.atTime(15, 0), DIA.atTime(16, 0)),
                    new Periodo(2L, DIA.atTime(9, 0), DIA.atTime(10, 0))),
            List.of(new Solicitacao(100, 2)), false, null, null, 1);
    private final Reserva r2 = new Reserva(2L, "PR/CE", "sub-2", 10L, null, null, "Treinamento", 5,
            List.of(new Periodo(3L, DIA.atTime(12, 0), DIA.atTime(13, 0))),
            List.of(), true, DIA.atTime(8, 0), null, 2);
    /** Período no sábado: só aparece com fds=true. */
    private final Reserva r3 = new Reserva(3L, "PR/CE", "sub-3", null, "Gabinete 2", null, "Visita", 3,
            List.of(new Periodo(4L, DIA.plusDays(5).atTime(9, 0), DIA.plusDays(5).atTime(10, 0))),
            List.of(), false, null, null, 1);

    /** Chamadas registradas para conferir qual GSI foi usado. */
    private final List<String> chamadas = new ArrayList<>();

    private final ServicoPainelAtendente.FonteDados fonte = new ServicoPainelAtendente.FonteDados() {
        @Override
        public List<Reserva> porSetor(long envoId, LocalDateTime de, LocalDateTime ate) {
            chamadas.add("GSI2:" + envoId);
            return envoId == SETOR ? List.of(r1, r3) : List.of();
        }

        @Override
        public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
            chamadas.add("GSI3:" + unidade);
            return "PR/CE".equals(unidade) ? List.of(r1, r2, r3) : List.of();
        }

        @Override
        public Optional<String> descricaoAmbiente(long ambienteId) {
            return ambienteId == 10 ? Optional.of("Auditório") : Optional.empty();
        }

        @Override
        public Optional<String> descricaoRecurso(long recursoId) {
            return recursoId == 100 ? Optional.of("Projetor") : Optional.empty();
        }

        @Override
        public List<ServicoPainelAtendente.PedidoSnpCard> pedidosSnp(long reservaId) {
            return reservaId == 1
                    ? List.of(new ServicoPainelAtendente.PedidoSnpCard("SNP-123", "https://snp.exemplo.gov.br/123"))
                    : List.of();
        }
    };

    private final ServicoPainelAtendente servico = new ServicoPainelAtendente(fonte);

    private static Principal usuario(String grupo, String unidade, String setor) {
        return new Principal("sub-x", null, List.of(grupo), unidade, setor);
    }

    private ServicoPainelAtendente.RespostaPainel consultar(Principal p, boolean fds) {
        return servico.consultar(ServicoPainelAtendente.lerParametros(
                Map.of("data", DIA.toString(), "colunas", "7", "fds", Boolean.toString(fds))), p);
    }

    @Test
    void atendenteVeSoReservasDoSetorOrdenadasPorInicio() {
        var resposta = consultar(usuario("Setor_Atendente", "PR/CE", "7"), false);

        assertThat(chamadas).containsExactly("GSI2:7");
        assertThat(resposta.colunas()).hasSize(7);
        var cards = resposta.colunas().get(0).cards();
        assertThat(cards).extracting(ServicoPainelAtendente.Card::inicio)
                .containsExactly("2030-03-04T09:00", "2030-03-04T15:00");
        var card = cards.get(0);
        assertThat(card.reservaId()).isEqualTo("1");
        assertThat(card.ambiente()).isEqualTo("Auditório");
        assertThat(card.solicitanteNome()).isEqualTo("sub-1");
        assertThat(card.recursos()).containsExactly(new ServicoPainelAtendente.RecursoCard("Projetor", 2));
        assertThat(card.pedidosSnp()).extracting(ServicoPainelAtendente.PedidoSnpCard::numero)
                .containsExactly("SNP-123");
        assertThat(card.cancelada()).isFalse();
    }

    @Test
    void administradorVeTodaUnidadeComIndicadorDeCancelada() {
        var resposta = consultar(usuario("Administrador", "PR/CE", null), false);

        assertThat(chamadas).containsExactly("GSI3:PR/CE");
        var cards = resposta.colunas().get(0).cards();
        assertThat(cards).extracting(ServicoPainelAtendente.Card::reservaId).containsExactly("1", "2", "1");
        assertThat(cards.get(1).cancelada()).isTrue();
    }

    @Test
    void fimDeSemanaSoApareceComFds() {
        // Sem fds: o sábado não é coluna e o card de Local_Proprio some
        var semFds = consultar(usuario("Setor_Atendente", "PR/CE", "7"), false);
        assertThat(semFds.colunas()).extracting(ServicoPainelAtendente.Coluna::data)
                .doesNotContain(DIA.plusDays(5).toString());

        var comFds = consultar(usuario("Setor_Atendente", "PR/CE", "7"), true);
        var sabado = comFds.colunas().stream()
                .filter(c -> c.data().equals(DIA.plusDays(5).toString())).findFirst().orElseThrow();
        assertThat(sabado.cards()).singleElement()
                .satisfies(c -> assertThat(c.ambiente()).isEqualTo("Local próprio: Gabinete 2"));
    }

    @Test
    void atendenteSemSetorNumericoRecebe403() {
        assertThatThrownBy(() -> consultar(usuario("Setor_Atendente", "PR/CE", null), false))
                .isInstanceOf(ExcecaoAcessoNegado.class);
        assertThatThrownBy(() -> consultar(usuario("Setor_Atendente", "PR/CE", "STI"), false))
                .isInstanceOf(ExcecaoAcessoNegado.class);
    }

    @Test
    void solicitanteRecebe403() {
        assertThatThrownBy(() -> consultar(usuario("Solicitante", "PR/CE", "7"), false))
                .isInstanceOf(ExcecaoAcessoNegado.class);
        assertThat(chamadas).isEmpty();
    }

    @Test
    void parametrosInvalidosAcumulamViolacoes() {
        assertThatThrownBy(() -> ServicoPainelAtendente.lerParametros(
                Map.of("data", "04/03/2030", "colunas", "0", "fds", "talvez")))
                .isInstanceOfSatisfying(ExcecaoValidacao.class,
                        e -> assertThat(e.violacoes()).hasSize(3));
    }
}
