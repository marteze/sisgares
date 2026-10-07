package br.mp.mpf.sisgares.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.AmbienteRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.DisposicaoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.GrupoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.RecursoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.SetorRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.VinculoAmbienteRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.VinculoSetorRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.AmbienteResposta;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.RecursoResposta;
import br.mp.mpf.sisgares.comumaws.auth.ExtratorPrincipal;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.http.MapeadorErros;
import br.mp.mpf.sisgares.dominio.ArvoreAmbientes;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Violacao;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Casos de uso do {@link ServicoCatalogo} com porta em memória.
 *
 * <p>**Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11, 7.12, 7.13, 7.14**
 */
class ServicoCatalogoTest {

    static final String UNIDADE = "PRDF";
    static final Principal ADMIN = new Principal("sub-admin", "Admin Exemplo",
            List.of(ServicoCatalogo.GRUPO_ADMINISTRADOR), UNIDADE, null);
    static final Principal SOLICITANTE = new Principal("sub-sol", "Solicitante Exemplo",
            List.of("Solicitante"), UNIDADE, null);

    MemoriaCatalogo memoria;
    ServicoCatalogo servico;

    @BeforeEach
    void preparar() {
        memoria = new MemoriaCatalogo();
        servico = new ServicoCatalogo(memoria, memoria);
    }

    private static List<String> codigos(ExcecaoValidacao e) {
        return e.violacoes().stream().map(Violacao::codigo).toList();
    }

    private ExcecaoValidacao validacao(Runnable acao) {
        try {
            acao.run();
        } catch (ExcecaoValidacao e) {
            return e;
        }
        throw new AssertionError("Esperava ExcecaoValidacao");
    }

    @Test
    void escritaPorNaoAdministradorEhNegada() {
        assertThatThrownBy(() -> servico.criarGrupo(new GrupoRequisicao("Serviço", 1), SOLICITANTE))
                .isInstanceOf(ExcecaoAcessoNegado.class);
        assertThatThrownBy(() -> servico.listarSetores(SOLICITANTE, false)).isInstanceOf(ExcecaoAcessoNegado.class);
        assertThatThrownBy(() -> servico.listarAmbientes(SOLICITANTE, true)).isInstanceOf(ExcecaoAcessoNegado.class);
    }

    @Test
    void inativosSomemDasOpcoesMasAparecemComTodosParaAdmin() {
        AmbienteResposta a = servico.criarAmbiente(new AmbienteRequisicao("Auditório", UNIDADE, null), ADMIN);
        servico.criarAmbiente(new AmbienteRequisicao("Sala 1", UNIDADE, null), ADMIN);
        servico.inativarAmbiente(Long.parseLong(a.id()), ADMIN);

        assertThat(servico.listarAmbientes(SOLICITANTE, false)).extracting(AmbienteResposta::nome)
                .containsExactly("Sala 1");
        assertThat(servico.listarAmbientes(ADMIN, true)).hasSize(2);
        // A inativação preserva o registro (ST_ATIVO = "N")
        assertThat(servico.obterAmbiente(SOLICITANTE, Long.parseLong(a.id())).ativo()).isFalse();
    }

    @Test
    void ambientePaiValidadoPelaArvoreERaizRecalculada() {
        AmbienteResposta raiz = servico.criarAmbiente(new AmbienteRequisicao("Auditório", UNIDADE, null), ADMIN);
        long raizId = Long.parseLong(raiz.id());
        AmbienteResposta filho = servico.criarAmbiente(new AmbienteRequisicao("Parte A", UNIDADE, raizId), ADMIN);
        long filhoId = Long.parseLong(filho.id());
        assertThat(filho.raizId()).isEqualTo(raiz.id());

        // Pai descendente e próprio ambiente são rejeitados
        assertThat(codigos(validacao(() -> servico.alterarAmbiente(raizId,
                new AmbienteRequisicao("Auditório", UNIDADE, filhoId), ADMIN))))
                .containsExactly(ArvoreAmbientes.PAI_DESCENDENTE);
        assertThat(codigos(validacao(() -> servico.alterarAmbiente(raizId,
                new AmbienteRequisicao("Auditório", UNIDADE, raizId), ADMIN))))
                .containsExactly(ArvoreAmbientes.PAI_PROPRIO_AMBIENTE);
        // Pai de outra Unidade_Macro
        AmbienteResposta outra = servico.criarAmbiente(new AmbienteRequisicao("Sala X", "PRSP", null), ADMIN);
        assertThat(codigos(validacao(() -> servico.criarAmbiente(
                new AmbienteRequisicao("Sala Y", UNIDADE, Long.parseLong(outra.id())), ADMIN))))
                .containsExactly(ArvoreAmbientes.PAI_OUTRA_UNIDADE);

        // Mover a raiz para baixo de outro ambiente atualiza a raiz do descendente
        AmbienteResposta predio = servico.criarAmbiente(new AmbienteRequisicao("Prédio", UNIDADE, null), ADMIN);
        servico.alterarAmbiente(raizId, new AmbienteRequisicao("Auditório", UNIDADE, Long.parseLong(predio.id())),
                ADMIN);
        assertThat(servico.obterAmbiente(ADMIN, filhoId).raizId()).isEqualTo(predio.id());
    }

    @Test
    void setorExigeCamposEEmailsValidos() {
        ExcecaoValidacao e = validacao(() -> servico.criarSetor(
                new SetorRequisicao(" ", null, "invalido", List.of("ok@exemplo.gov.br", "sem-arroba")), ADMIN));
        assertThat(codigos(e)).containsExactlyInAnyOrder(ServicoCatalogo.CAMPO_OBRIGATORIO,
                ServicoCatalogo.CAMPO_OBRIGATORIO, ServicoCatalogo.EMAIL_INVALIDO, ServicoCatalogo.EMAIL_INVALIDO);

        var setor = servico.criarSetor(new SetorRequisicao("Informática", UNIDADE, "ti@exemplo.gov.br",
                List.of("suporte@exemplo.gov.br")), ADMIN);
        assertThat(setor.emailsAlternativos()).containsExactly("suporte@exemplo.gov.br");
        assertThat(servico.listarSetores(ADMIN, false)).hasSize(1);
    }

    @Test
    void recursoLimitadoExigeQuantidadeEGrupo() {
        ExcecaoValidacao e = validacao(() -> servico.criarRecurso(
                new RecursoRequisicao("Projetor", 99L, "projetor.svg", null, true, 0), ADMIN));
        assertThat(codigos(e)).containsExactlyInAnyOrder(ServicoCatalogo.GRUPO_INEXISTENTE,
                ServicoCatalogo.DISPONIBILIDADE_INVALIDA);
        assertThat(codigos(validacao(() -> servico.criarRecurso(
                new RecursoRequisicao("Projetor", null, null, null, false, null), ADMIN))))
                .containsExactly(ServicoCatalogo.CAMPO_OBRIGATORIO, ServicoCatalogo.CAMPO_OBRIGATORIO);
    }

    @Test
    void recursosListadosComGrupoOrdemEAmbientesPermitidos() {
        long equipamento = Long.parseLong(servico.criarGrupo(new GrupoRequisicao("Equipamento", 3), ADMIN).id());
        long servicoG = Long.parseLong(servico.criarGrupo(new GrupoRequisicao("Serviço", 1), ADMIN).id());
        long projetor = Long.parseLong(servico.criarRecurso(
                new RecursoRequisicao("Projetor", equipamento, "projetor.svg", null, true, 2), ADMIN).id());
        servico.criarRecurso(new RecursoRequisicao("Café", servicoG, "cafe.svg", null, false, null), ADMIN);
        long sala = Long.parseLong(servico.criarAmbiente(new AmbienteRequisicao("Sala", UNIDADE, null), ADMIN).id());

        servico.vincularRecursoAmbiente(projetor, new VinculoAmbienteRequisicao(sala), ADMIN);
        // Alterar o recurso preserva o VREC
        servico.alterarRecurso(projetor,
                new RecursoRequisicao("Projetor HD", equipamento, "projetor.svg", null, true, 3), ADMIN);

        List<RecursoResposta> lista = servico.listarRecursos(SOLICITANTE, false);
        assertThat(lista).extracting(RecursoResposta::descricao).containsExactly("Café", "Projetor HD");
        RecursoResposta p = lista.get(1);
        assertThat(p.grupoNome()).isEqualTo("Equipamento");
        assertThat(p.grupoOrdem()).isEqualTo(3);
        assertThat(p.disponibilidade()).isEqualTo(3);
        assertThat(p.ambientesPermitidos()).containsExactly(Long.toString(sala));

        servico.inativarRecurso(projetor, ADMIN);
        assertThat(servico.listarRecursos(SOLICITANTE, false)).hasSize(1);
    }

    @Test
    void vinculoSetorAmbienteExigeMesmaUnidade() {
        long sala = Long.parseLong(servico.criarAmbiente(new AmbienteRequisicao("Sala", UNIDADE, null), ADMIN).id());
        long ti = Long.parseLong(servico.criarSetor(
                new SetorRequisicao("TI", UNIDADE, "ti@exemplo.gov.br", null), ADMIN).id());
        long outro = Long.parseLong(servico.criarSetor(
                new SetorRequisicao("TI SP", "PRSP", "tisp@exemplo.gov.br", null), ADMIN).id());

        assertThat(codigos(validacao(() -> servico.vincularSetorAmbiente(sala,
                new VinculoSetorRequisicao(outro, null), ADMIN)))).containsExactly(ServicoCatalogo.SETOR_OUTRA_UNIDADE);
        servico.vincularSetorAmbiente(sala, new VinculoSetorRequisicao(ti, "SNP-1"), ADMIN);
        assertThat(servico.listarSetoresDoAmbiente(ADMIN, sala)).singleElement()
                .satisfies(v -> assertThat(v.codServicoSnp()).isEqualTo("SNP-1"));
        servico.desvincularSetorAmbiente(sala, ti, ADMIN);
        assertThat(servico.listarSetoresDoAmbiente(ADMIN, sala)).isEmpty();
    }

    @Test
    void imagemDaDisposicaoGravadaNoArmazenamento() {
        long id = Long.parseLong(servico.criarDisposicao(new DisposicaoRequisicao("Auditório", "Cadeiras em fileiras"),
                ADMIN).id());
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\"/></svg>"
                .getBytes(StandardCharsets.UTF_8);
        var resposta = servico.enviarImagem(id, svg, ADMIN);
        assertThat(resposta.imagem()).isEqualTo("disposicao-" + id + ".svg");
        assertThat(memoria.objetos).containsKey(ServicoCatalogo.PREFIXO_IMAGENS + "disposicao-" + id + ".svg");

        byte[] inseguro = "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertThat(codigos(validacao(() -> servico.enviarImagem(id, inseguro, ADMIN))))
                .containsExactly(ValidadorImagem.SVG_INSEGURO);
    }

    /** Token fictício (alg "none") aceito pelo {@link ExtratorPrincipal} em modo mock. */
    private static String token(String grupo) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"sub\":\"sub-1\",\"name\":\"Fulano Exemplo\","
                + "\"cognito:groups\":[\"" + grupo + "\"],\"custom:unidade\":\"PRDF\"}")
                .getBytes(StandardCharsets.UTF_8));
        return cabecalho + "." + payload + ".";
    }

    private APIGatewayV2HTTPResponse chamar(String metodo, String caminho, String corpo, String grupo) {
        Handler handler = new Handler(servico,
                new ExtratorPrincipal(true, ConstantesDominio.relogioPadrao()),
                new MapeadorErros(new LogEstruturado(ConstantesDominio.relogioPadrao(), "catalogo")));
        APIGatewayV2HTTPEvent evento = APIGatewayV2HTTPEvent.builder()
                .withRawPath(caminho)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withRequestId("req-1")
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(metodo).build())
                        .build())
                .withHeaders(Map.of("Authorization", "Bearer " + token(grupo)))
                .withBody(corpo)
                .build();
        return handler.handleRequest(evento, null);
    }

    @Test
    void handlerGetAmbientesDevolveIdNomeAtivoEEscritaExigeAdmin() {
        servico.criarAmbiente(new AmbienteRequisicao("Sala 1", UNIDADE, null), ADMIN);

        APIGatewayV2HTTPResponse get = chamar("GET", "/api/catalogo/ambientes", null, "Solicitante");
        assertThat(get.getStatusCode()).isEqualTo(200);
        assertThat(get.getBody()).contains("\"id\":\"1001\"", "\"nome\":\"Sala 1\"", "\"ativo\":true");

        String corpo = "{\"descricao\":\"Sala 2\",\"unidade\":\"PRDF\"}";
        assertThat(chamar("POST", "/api/catalogo/ambientes", corpo, "Solicitante").getStatusCode()).isEqualTo(403);
        assertThat(chamar("POST", "/api/catalogo/ambientes", corpo, ServicoCatalogo.GRUPO_ADMINISTRADOR)
                .getStatusCode()).isEqualTo(201);
        assertThat(chamar("PATCH", "/api/catalogo/ambientes/1001/inativar", null,
                ServicoCatalogo.GRUPO_ADMINISTRADOR).getStatusCode()).isEqualTo(200);
        assertThat(chamar("GET", "/api/catalogo/setores", null, "Solicitante").getStatusCode()).isEqualTo(403);
        assertThat(chamar("GET", "/api/catalogo/inexistente", null, "Solicitante").getStatusCode()).isEqualTo(404);
    }
}
