package br.mp.mpf.sisgares.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.comumaws.auth.AutorizadorLocal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Consulta;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Grupo;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Visibilidade e parâmetros da exportação (mesmas regras do Painel do Atendente).
 *
 * <p><b>Validates: Requirements 17.1, 17.3</b>
 */
class ServicoExportacaoTest {

    private static final LocalDate SEG = LocalDate.of(2025, 3, 10);

    private final Reserva r1 = new Reserva(1L, "PR/CE", "sub-1", 10L, null, null, "Reunião", 10,
            List.of(new Periodo(1L, SEG.atTime(14, 0), SEG.atTime(15, 0)),
                    new Periodo(2L, SEG.plusDays(1).atTime(9, 0), SEG.plusDays(1).atTime(10, 0))),
            List.of(new Solicitacao(5L, 2)), false, null, null, 1);
    private final Reserva r2 = new Reserva(2L, "PR/CE", "sub-2", 10L, null, null, "Aula", 5,
            List.of(new Periodo(3L, SEG.atTime(8, 0), SEG.atTime(9, 0))), List.of(), true, null, null, 1);

    private final ServicoExportacao.FonteDados fonte = new ServicoExportacao.FonteDados() {
        @Override
        public List<Reserva> porSetor(long envoId, LocalDateTime de, LocalDateTime ate) {
            return envoId == 7 ? List.of(r1) : List.of();
        }

        @Override
        public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
            return "PR/CE".equals(unidade) ? List.of(r1, r2) : List.of();
        }

        @Override
        public Optional<String> descricaoAmbiente(long ambienteId) {
            return Optional.of("Sala " + ambienteId);
        }

        @Override
        public Optional<String> descricaoRecurso(long recursoId) {
            return Optional.of("Projetor");
        }
    };

    private final ServicoExportacao servico = new ServicoExportacao(fonte, new AutorizadorLocal());
    private final Consulta consulta = new Consulta(ServicoExportacao.Formato.CSV, SEG, 2, false);

    @Test
    void administradorVeAUnidadeOrdenadaPorInicio() {
        Principal admin = new Principal("adm", "Admin", List.of("Administrador"), "PR/CE", null);
        List<Grupo> grupos = servico.consultar(consulta, admin);
        assertThat(grupos).extracting(Grupo::data).containsExactly(SEG, SEG.plusDays(1));
        assertThat(grupos.get(0).linhas()).extracting(ServicoExportacao.Linha::reservaId).containsExactly(2L, 1L);
        assertThat(grupos.get(0).linhas().get(1).recursos()).isEqualTo("Projetor (2)");
    }

    @Test
    void atendenteVeSoOSeuSetor() {
        Principal atendente = new Principal("at", "Atendente", List.of("Setor_Atendente"), "PR/CE", "7");
        List<Grupo> grupos = servico.consultar(consulta, atendente);
        assertThat(grupos.stream().flatMap(g -> g.linhas().stream()))
                .extracting(ServicoExportacao.Linha::reservaId).containsOnly(1L);
    }

    @Test
    void demaisPerfisRecebem403() {
        Principal solicitante = new Principal("s", "Sol", List.of("Solicitante"), "PR/CE", null);
        assertThatThrownBy(() -> servico.consultar(consulta, solicitante)).isInstanceOf(ExcecaoAcessoNegado.class);
    }

    @Test
    void validaCorpoAcumulandoViolacoes() {
        assertThatThrownBy(() -> ServicoExportacao.lerParametros(Map.of("formato", "xls", "colunas", 99)))
                .isInstanceOf(ExcecaoValidacao.class)
                .satisfies(e -> assertThat(((ExcecaoValidacao) e).violacoes()).hasSize(3));
        Consulta c = ServicoExportacao.lerParametros(Map.of("formato", "pdf", "data", "2025-03-10", "colunas", 5,
                "fds", true));
        assertThat(c).isEqualTo(new Consulta(ServicoExportacao.Formato.PDF, SEG, 5, true));
    }

    @Test
    void chaveNaoContemOSub() {
        String chave = ArmazenamentoS3.chave("sub-secreto", Instant.parse("2025-03-10T12:00:00Z"), "csv");
        assertThat(chave).matches("exportacoes/[0-9a-f]{32}/20250310T120000000Z\\.csv").doesNotContain("sub-secreto");
    }
}
