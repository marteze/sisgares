package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.CodigosRegra;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Status;
import br.mp.mpf.sisgares.dominio.Violacao;
import br.mp.mpf.sisgares.reservas.dto.CancelamentoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.PeriodoDto;
import br.mp.mpf.sisgares.reservas.dto.ReservaAlteracaoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.ReservaRequisicao;
import br.mp.mpf.sisgares.reservas.dto.ReservaResposta;
import br.mp.mpf.sisgares.reservas.dto.VerificarPeriodoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.VerificarPeriodoResposta;
import br.mp.mpf.sisgares.reservas.dto.VersaoResposta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos de uso de {@link ServicoReservas} com portas em memória.
 *
 * <p>**Validates: Requirements 9.14, 9.15, 10.5, 12.1, 12.4, 12.6, 12.8, 6.17**
 */
class ServicoReservasTest {

    static final String UNIDADE = "PRDF";
    static final LocalDateTime AGORA = LocalDateTime.of(2030, 1, 10, 8, 0);
    static final Clock RELOGIO = Clock.fixed(AGORA.atZone(ConstantesDominio.ZONA).toInstant(), ConstantesDominio.ZONA);

    static final Principal DONO = new Principal("sub-dono", "Solicitante Exemplo",
            List.of(ServicoReservas.GRUPO_SOLICITANTE), UNIDADE, null);
    static final Principal OUTRO = new Principal("sub-outro", "Outro Exemplo",
            List.of(ServicoReservas.GRUPO_SOLICITANTE), UNIDADE, null);
    static final Principal ADMIN = new Principal("sub-admin", "Admin Exemplo",
            List.of(ServicoReservas.GRUPO_ADMINISTRADOR), UNIDADE, null);
    static final Principal ATENDENTE = new Principal("sub-atend", "Atendente Exemplo",
            List.of(ServicoReservas.GRUPO_ATENDENTE), UNIDADE, "7");

    MemoriaReservas memoria;
    ServicoReservas servico;

    @BeforeEach
    void preparar() {
        memoria = new MemoriaReservas();
        memoria.ambientes.add(new Ambiente(1L, "Auditório (Completo)", true, null, UNIDADE));
        memoria.ambientes.add(new Ambiente(2L, "Auditório (Parte A)", true, 1L, UNIDADE));
        memoria.setoresAmbiente.put(1L, Set.of(7L));
        servico = new ServicoReservas(memoria, memoria, RELOGIO, ServicoReservas.ResolvedorNome.doPrincipal());
    }

    static ReservaRequisicao requisicao(String ambienteId, LocalDateTime ini, LocalDateTime fim) {
        return new ReservaRequisicao(ambienteId, null, null, "Reunião de planejamento", 10,
                List.of(new PeriodoDto(null, ini, fim)), List.of());
    }

    static LocalDateTime dia12(int hora, int minuto) {
        return LocalDateTime.of(2030, 1, 12, hora, minuto);
    }

    private ReservaResposta criarPadrao() {
        return servico.criar(requisicao("1", dia12(9, 0), dia12(11, 0)), DONO);
    }

    private static List<String> codigos(ExcecaoValidacao e) {
        return e.violacoes().stream().map(Violacao::codigo).toList();
    }

    @Test
    void criar_gravaPrimeiraVersaoEEventoNoOutbox() {
        ReservaResposta r = criarPadrao();

        long id = Long.parseLong(r.id());
        assertThat(r.status()).isEqualTo(Status.PREVISTA);
        assertThat(r.solicitanteNome()).isEqualTo("Solicitante Exemplo");
        assertThat(r.periodos()).singleElement().satisfies(p -> assertThat(p.id()).isNotNull());
        assertThat(memoria.historico.get(id)).singleElement().satisfies(v -> assertThat(v.numero()).isEqualTo(1));
        assertThat(memoria.eventos).singleElement()
                .satisfies(e -> assertThat(e.tipo()).isEqualTo(ServicoReservas.EVENTO_CRIADA));
    }

    @Test
    void criar_RN2_retornaTodasAsViolacoes() {
        ReservaRequisicao req = new ReservaRequisicao("1", null, null, "  ", null,
                List.of(new PeriodoDto(null, dia12(9, 0), dia12(11, 0))), List.of());

        assertThatThrownBy(() -> servico.criar(req, DONO))
                .isInstanceOfSatisfying(ExcecaoValidacao.class, e -> assertThat(codigos(e)).contains(
                        CodigosRegra.RN2_FINALIDADE_OBRIGATORIA, CodigosRegra.RN2_PARTICIPANTES_OBRIGATORIO));
        assertThat(memoria.eventos).isEmpty();
    }

    @Test
    void criar_RN6_conflitoComAmbienteRelacionado() {
        servico.criar(requisicao("2", dia12(14, 0), dia12(16, 0)), DONO);

        assertThatThrownBy(() -> servico.criar(requisicao("1", dia12(15, 0), dia12(17, 0)), OUTRO))
                .isInstanceOfSatisfying(ExcecaoValidacao.class,
                        e -> assertThat(codigos(e)).containsExactly(CodigosRegra.RN6_CONFLITO_PAI_FILHO));
    }

    @Test
    void alterar_gravaNovaVersaoEPreservaIdDoPeriodo() {
        ReservaResposta criada = criarPadrao();
        long id = Long.parseLong(criada.id());
        String presId = criada.periodos().get(0).id();
        // O frontend reenvia o período sem id e com a mesma data/hora
        ReservaAlteracaoRequisicao req = new ReservaAlteracaoRequisicao("1", null, null, "Nova finalidade", 20,
                List.of(new PeriodoDto(null, dia12(9, 0), dia12(11, 0))), List.of());

        ReservaResposta alterada = servico.alterar(id, req, DONO);

        assertThat(alterada.finalidade()).isEqualTo("Nova finalidade");
        assertThat(alterada.periodos().get(0).id()).isEqualTo(presId);
        assertThat(memoria.atuais.get(id).versao()).isEqualTo(2);
        assertThat(memoria.eventos).last()
                .satisfies(e -> assertThat(e.tipo()).isEqualTo(ServicoReservas.EVENTO_ALTERADA));
    }

    @Test
    void alterar_RN12_reservaCanceladaNaoEditavel() {
        long id = Long.parseLong(criarPadrao().id());
        servico.cancelar(id, new CancelamentoRequisicao(true), DONO);
        ReservaAlteracaoRequisicao req = new ReservaAlteracaoRequisicao("1", null, null, "X", 5,
                List.of(new PeriodoDto(null, dia12(9, 0), dia12(11, 0))), List.of());

        assertThatThrownBy(() -> servico.alterar(id, req, DONO))
                .isInstanceOfSatisfying(ExcecaoValidacao.class,
                        e -> assertThat(codigos(e)).contains(CodigosRegra.RN12_RESERVA_NAO_EDITAVEL));
    }

    @Test
    void alterarECancelar_porOutroSolicitante_negado() {
        long id = Long.parseLong(criarPadrao().id());

        assertThatThrownBy(() -> servico.cancelar(id, new CancelamentoRequisicao(true), OUTRO))
                .isInstanceOf(ExcecaoAcessoNegado.class);
        assertThatThrownBy(() -> servico.obter(id, OUTRO)).isInstanceOf(ExcecaoAcessoNegado.class);
    }

    @Test
    void cancelar_RN12_semConfirmacao() {
        long id = Long.parseLong(criarPadrao().id());

        assertThatThrownBy(() -> servico.cancelar(id, new CancelamentoRequisicao(null), DONO))
                .isInstanceOfSatisfying(ExcecaoValidacao.class,
                        e -> assertThat(codigos(e)).containsExactly(CodigosRegra.RN12_CONFIRMACAO_OBRIGATORIA));
    }

    @Test
    void cancelar_confirmadoPeloAdministrador_gravaVersaoCancelada() {
        long id = Long.parseLong(criarPadrao().id());

        ReservaResposta r = servico.cancelar(id, new CancelamentoRequisicao(true), ADMIN);

        assertThat(r.status()).isEqualTo(Status.CANCELADA);
        assertThat(memoria.atuais.get(id).solicitante()).isEqualTo(DONO.sub());
        assertThat(memoria.historico.get(id)).hasSize(2);
        // Req. 6.17: o resolvedor padrão não expõe o nome de outro usuário
        assertThat(r.solicitanteNome()).isNull();
    }

    @Test
    void obter_atendenteEnvolvidoVeDetalhe() {
        long id = Long.parseLong(criarPadrao().id());
        Principal atendenteOutroSetor = new Principal("sub-x", "X", List.of(ServicoReservas.GRUPO_ATENDENTE),
                UNIDADE, "99");

        assertThat(servico.obter(id, ATENDENTE).id()).isEqualTo(String.valueOf(id));
        assertThatThrownBy(() -> servico.obter(id, atendenteOutroSetor)).isInstanceOf(ExcecaoAcessoNegado.class);
    }

    @Test
    void verificarPeriodo_RN5_respeitaMargemEIgnoraPropriaReserva() {
        String id = criarPadrao().id();

        VerificarPeriodoResposta conflito = servico.verificarPeriodo(new VerificarPeriodoRequisicao("1",
                new PeriodoDto(null, dia12(11, 20), dia12(12, 0)), null), OUTRO);
        VerificarPeriodoResposta livre = servico.verificarPeriodo(new VerificarPeriodoRequisicao("1",
                new PeriodoDto(null, dia12(11, 30), dia12(12, 0)), null), OUTRO);
        VerificarPeriodoResposta propria = servico.verificarPeriodo(new VerificarPeriodoRequisicao("1",
                new PeriodoDto(null, dia12(10, 0), dia12(12, 0)), id), DONO);

        assertThat(conflito.conflitos()).singleElement().satisfies(c -> {
            assertThat(c.codigo()).isEqualTo(CodigosRegra.RN5_CONFLITO_HORARIO);
            assertThat(c.reservaId()).isEqualTo(id);
        });
        assertThat(livre.conflitos()).isEmpty();
        assertThat(propria.conflitos()).isEmpty();
    }

    @Test
    void versoes_listaDiferencasEntreVersoes() {
        long id = Long.parseLong(criarPadrao().id());
        servico.alterar(id, new ReservaAlteracaoRequisicao("1", null, null, "Outra finalidade", 10,
                List.of(new PeriodoDto(null, dia12(9, 0), dia12(11, 0))), List.of()), DONO);

        List<VersaoResposta> versoes = servico.versoes(id, DONO);

        assertThat(versoes).extracting(VersaoResposta::numero).containsExactly(1L, 2L);
        assertThat(versoes.get(0).diferencas()).isEmpty();
        assertThat(versoes.get(1).diferencas()).extracting(d -> d.campo()).containsExactly("Finalidade");
    }
}
