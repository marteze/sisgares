package br.mp.mpf.sisgares.comumaws.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.verifiedpermissions.model.IsAuthorizedRequest;

/**
 * Matriz perfil × dono/setor/unidade do {@link AutorizadorLocal}, que reproduz as políticas Cedar
 * de {@code infra/cedar} (criar-reserva, dono-reserva, admin-reservas-unidade, atendente-setor).
 *
 * <p><b>Validates: Requirements 5.5, 5.6</b>
 */
class AutorizadorLocalTest {

    private static final String UNIDADE = "PR-EX";
    private static final String OUTRA_UNIDADE = "PR-OUTRA";
    private static final String SETOR = "7";

    // Usuários fictícios
    private static final Principal SOLICITANTE = new Principal("sub-dono", null, List.of("Solicitante"), UNIDADE, null);
    private static final Principal OUTRO_SOLICITANTE = new Principal("sub-outro", null, List.of("Solicitante"), UNIDADE, null);
    private static final Principal ADMIN = new Principal("sub-admin", null, List.of("Administrador"), UNIDADE, null);
    private static final Principal ADMIN_OUTRA = new Principal("sub-admin2", null, List.of("Administrador"), OUTRA_UNIDADE, null);
    private static final Principal ATENDENTE = new Principal("sub-atend", null, List.of("Setor_Atendente"), UNIDADE, SETOR);
    private static final Principal ATENDENTE_OUTRO_SETOR = new Principal("sub-atend2", null, List.of("Setor_Atendente"), UNIDADE, "9");
    private static final Principal ATENDENTE_SEM_SETOR = new Principal("sub-atend3", null, List.of("Setor_Atendente"), UNIDADE, null);
    private static final Principal ATENDENTE_OUTRA_UNIDADE = new Principal("sub-atend4", null, List.of("Setor_Atendente"), OUTRA_UNIDADE, SETOR);

    /** Reserva do SOLICITANTE, na UNIDADE, envolvendo o SETOR. */
    private static final RecursoAutorizacao RESERVA = new RecursoAutorizacao("10", "sub-dono", UNIDADE, Set.of(SETOR, "3"));

    private final AutorizadorLocal autorizador = new AutorizadorLocal();

    /** Linhas: principal, consultar, alterar, cancelar, ver painel (sobre a RESERVA). */
    static Stream<Arguments> matriz() {
        return Stream.of(
                Arguments.of("dono", SOLICITANTE, true, true, true, false),
                Arguments.of("outro solicitante", OUTRO_SOLICITANTE, false, false, false, false),
                Arguments.of("admin da unidade", ADMIN, true, true, true, true),
                Arguments.of("admin de outra unidade", ADMIN_OUTRA, false, false, false, false),
                Arguments.of("atendente do setor", ATENDENTE, true, false, false, true),
                Arguments.of("atendente de outro setor", ATENDENTE_OUTRO_SETOR, false, false, false, false),
                Arguments.of("atendente sem setor", ATENDENTE_SEM_SETOR, false, false, false, false),
                Arguments.of("atendente de outra unidade", ATENDENTE_OUTRA_UNIDADE, false, false, false, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void matrizPerfilDonoSetorUnidade(String caso, Principal p, boolean consultar, boolean alterar,
                                      boolean cancelar, boolean painel) {
        assertThat(autorizador.permitido(p, Acao.CONSULTAR_RESERVA, RESERVA)).as("consultar").isEqualTo(consultar);
        assertThat(autorizador.permitido(p, Acao.ALTERAR_RESERVA, RESERVA)).as("alterar").isEqualTo(alterar);
        assertThat(autorizador.permitido(p, Acao.CANCELAR_RESERVA, RESERVA)).as("cancelar").isEqualTo(cancelar);
        assertThat(autorizador.permitido(p, Acao.VER_PAINEL_ATENDENTE, RESERVA)).as("painel").isEqualTo(painel);
    }

    @Test
    void criarReservaSomenteEmNomeProprioENaPropriaUnidade() {
        assertThat(autorizador.permitido(SOLICITANTE, Acao.CRIAR_RESERVA,
                new RecursoAutorizacao("nova", "sub-dono", UNIDADE, Set.of()))).isTrue();
        // Em nome de terceiro
        assertThat(autorizador.permitido(SOLICITANTE, Acao.CRIAR_RESERVA,
                new RecursoAutorizacao("nova", "sub-outro", UNIDADE, Set.of()))).isFalse();
        // Em outra unidade
        assertThat(autorizador.permitido(SOLICITANTE, Acao.CRIAR_RESERVA,
                new RecursoAutorizacao("nova", "sub-dono", OUTRA_UNIDADE, Set.of()))).isFalse();
    }

    @Test
    void painelDoProprioUsuario() {
        assertThat(autorizador.permitido(ADMIN, Acao.VER_PAINEL_ATENDENTE, RecursoAutorizacao.painelDe(ADMIN))).isTrue();
        assertThat(autorizador.permitido(ATENDENTE, Acao.VER_PAINEL_ATENDENTE, RecursoAutorizacao.painelDe(ATENDENTE))).isTrue();
        assertThat(autorizador.permitido(ATENDENTE_SEM_SETOR, Acao.VER_PAINEL_ATENDENTE,
                RecursoAutorizacao.painelDe(ATENDENTE_SEM_SETOR))).isFalse();
        assertThat(autorizador.permitido(SOLICITANTE, Acao.VER_PAINEL_ATENDENTE,
                RecursoAutorizacao.painelDe(SOLICITANTE))).isFalse();
    }

    @Test
    void negacaoLancaAcessoNegado() {
        assertThatThrownBy(() -> autorizador.exigir(OUTRO_SOLICITANTE, Acao.ALTERAR_RESERVA, RESERVA))
                .isInstanceOf(ExcecaoAcessoNegado.class);
    }

    @Test
    void requisicaoAvpMontaEntidadesUsuarioEReserva() {
        // Só monta a requisição; nenhuma chamada de rede (cliente nunca é usado)
        var avp = new AutorizadorVerifiedPermissions(
                software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient.builder()
                        .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                        .credentialsProvider(software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider.create())
                        .build(),
                "ps-exemplo");
        IsAuthorizedRequest req = avp.requisicao(ATENDENTE, Acao.CONSULTAR_RESERVA, RESERVA);
        assertThat(req.policyStoreId()).isEqualTo("ps-exemplo");
        assertThat(req.action().actionType()).isEqualTo("Sisgares::Action");
        assertThat(req.action().actionId()).isEqualTo("ConsultarReserva");
        assertThat(req.principal().entityType()).isEqualTo("Sisgares::Usuario");
        assertThat(req.resource().entityId()).isEqualTo("10");
        var entidades = req.entities().entityList();
        assertThat(entidades).hasSize(2);
        var usuario = entidades.get(0).attributes();
        assertThat(usuario.get("unidade").string()).isEqualTo(UNIDADE);
        assertThat(usuario.get("setor").string()).isEqualTo(SETOR);
        assertThat(usuario.get("grupos").set()).extracting(v -> v.string()).containsExactly("Setor_Atendente");
        var reserva = entidades.get(1).attributes();
        assertThat(reserva.get("dono").entityIdentifier().entityId()).isEqualTo("sub-dono");
        assertThat(reserva.get("setores").set()).extracting(v -> v.string()).containsExactly("3", "7");
    }
}
