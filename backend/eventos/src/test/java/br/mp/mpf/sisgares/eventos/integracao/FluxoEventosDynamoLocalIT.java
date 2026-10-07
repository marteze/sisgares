package br.mp.mpf.sisgares.eventos.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.comumaws.repositorio.GravadorReservas;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.eventos.notificador.CaixaSimulada;
import br.mp.mpf.sisgares.eventos.notificador.EventoReserva;
import br.mp.mpf.sisgares.eventos.notificador.LeitorDadosDynamo;
import br.mp.mpf.sisgares.eventos.notificador.Notificador;
import br.mp.mpf.sisgares.eventos.notificador.RegistroIdempotenciaDynamo;
import br.mp.mpf.sisgares.eventos.notificador.StatusNotificacao;
import br.mp.mpf.sisgares.eventos.notificador.TipoEvento;
import br.mp.mpf.sisgares.eventos.snp.ClienteSnp;
import br.mp.mpf.sisgares.eventos.snp.PedidoSnp;
import br.mp.mpf.sisgares.eventos.snp.RepositorioSnpDynamo;
import br.mp.mpf.sisgares.eventos.snp.RespostaSnp;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Testes de integração do fluxo de eventos (Notificador e Cliente_SNP) com DynamoDB Local via
 * Testcontainers. Cobrem:
 *
 * <ul>
 *   <li>RN10: todos os setores ativos vinculados ao Ambiente e aos Recursos são notificados.</li>
 *   <li>RN11: Pedido_SNP só é gerado para vínculos com código de serviço.</li>
 *   <li>Entrega duplicada do mesmo evento não gera novas notificações nem novos pedidos.</li>
 * </ul>
 * O SES entra como dublê: usa-se a {@link CaixaSimulada} (modo simulado, sem envio externo) e um
 * {@code GatewaySnp} em memória; nenhuma chamada à AWS real é feita.
 *
 * <p><b>Validates: Requirements 22.5, 22.6</b>
 *
 * <p>Desabilitado: o ambiente de execução não tem Docker disponível, exigido pelo Testcontainers
 * para subir o contêiner {@code amazon/dynamodb-local}. Habilite rodando com Docker ativo.
 */
@Testcontainers
@Disabled("Requer Docker para o Testcontainers subir o DynamoDB Local; indisponivel no ambiente.")
class FluxoEventosDynamoLocalIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2030-01-10T12:00:00Z"), ZoneId.of("America/Fortaleza"));
    private static final LogEstruturado LOG =
            new LogEstruturado(CLOCK, "teste-it", new PrintStream(OutputStream.nullOutputStream()));

    private static final long RESE_ID = 2001;
    private static final long AMBIENTE_ID = 500;
    private static final long RECURSO_ID = 20;

    @Container
    static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
            .withExposedPorts(8000);

    private static DynamoDbClient cliente;
    private static Notificador notificador;
    private static ClienteSnp clienteSnp;
    private static CaixaSimulada caixa;
    private static GatewaySnpMemoria gateway;

    @BeforeAll
    static void prepararAmbiente() {
        String endpoint = "http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000);
        cliente = TabelaDynamoLocal.cliente(endpoint);
        TabelaDynamoLocal.criarTabela(cliente);

        // Catálogo: setores 1 (ativo) e 2 (ativo); setor 3 (inativo) não deve ser notificado
        RepositorioCatalogo catalogo = new RepositorioCatalogo(cliente, TabelaDynamoLocal.TABELA);
        catalogo.salvarSetor(setor(1, "s1@exemplo.gov.br", true));
        catalogo.salvarSetor(setor(2, "s2@exemplo.gov.br", true));
        catalogo.salvarSetor(setor(3, "s3@exemplo.gov.br", false));
        // Ambiente 500 vinculado aos setores 1 e 3; EAMB do setor 1 com código SNP, setor 3 sem código
        catalogo.vincularSetorAmbiente(new VinculoSetor(1, 1, AMBIENTE_ID, "SRV-SALA"));
        catalogo.vincularSetorAmbiente(new VinculoSetor(2, 3, AMBIENTE_ID, null));
        // Recurso 20 vinculado ao setor 2, sem código SNP (não gera Pedido_SNP)
        catalogo.vincularSetorRecurso(new VinculoSetor(3, 2, RECURSO_ID, null));

        // Reserva versão 1 persistida com o snapshot VERS#0001 (lido pelo Notificador e Cliente_SNP)
        GravadorReservas gravador = new GravadorReservas(cliente, TabelaDynamoLocal.TABELA);
        Reserva reserva = reserva(1);
        gravador.gravar(reserva, LocalDateTime.of(2030, 2, 1, 8, 0), AMBIENTE_ID, Set.of(1L, 2L),
                Map.of(GravadorReservas.chaveControleAmbiente(AMBIENTE_ID), 0L), null,
                new EventoOutbox("outbox-1", "RESERVA_CRIADA", RESE_ID, 1, CLOCK.instant()));

        caixa = new CaixaSimulada(cliente, TabelaDynamoLocal.TABELA);
        notificador = new Notificador(
                new LeitorDadosDynamo(cliente, TabelaDynamoLocal.TABELA), caixa, caixa,
                new RegistroIdempotenciaDynamo(cliente, TabelaDynamoLocal.TABELA,
                        RegistroIdempotenciaDynamo.CONSUMIDOR, CLOCK),
                CLOCK, LOG);
        gateway = new GatewaySnpMemoria();
        clienteSnp = new ClienteSnp(
                new RepositorioSnpDynamo(cliente, TabelaDynamoLocal.TABELA, CLOCK), gateway,
                () -> Optional.of("https://snp.exemplo.gov.br"),
                new RegistroIdempotenciaDynamo(cliente, TabelaDynamoLocal.TABELA, "CLIENTE_SNP", CLOCK),
                CLOCK, LOG);
    }

    @AfterAll
    static void fechar() {
        if (cliente != null) {
            cliente.close();
        }
    }

    @Test
    void rn10SetoresAtivosSaoNotificadosEDuplicataNaoReenviaRn11PedidoSoComCodigo() {
        EventoReserva evento = new EventoReserva("evt-cria-2001", RESE_ID, 1, TipoEvento.CRIADA);

        // Notificação: setores ativos 1 e 2 recebem; setor 3 (inativo) não
        Notificador.Resultado notif = notificador.processar(evento);
        assertThat(notif.enviadas()).isEqualTo(2);
        assertThat(caixa.status(RESE_ID, evento.eventoId(), 1).orElseThrow()).isEqualTo(StatusNotificacao.SIMULADA);
        assertThat(caixa.status(RESE_ID, evento.eventoId(), 2).orElseThrow()).isEqualTo(StatusNotificacao.SIMULADA);
        assertThat(caixa.status(RESE_ID, evento.eventoId(), 3)).isEmpty();

        // Entrega duplicada do mesmo evento: nada é reenviado
        Notificador.Resultado duplicado = notificador.processar(evento);
        assertThat(duplicado.jaProcessado()).isTrue();
        assertThat(duplicado.enviadas()).isZero();

        // Pedido_SNP: só o vínculo EAMB do setor 1 tem código; recurso e setor 3 não geram pedido
        ClienteSnp.Resultado snp = clienteSnp.processar(evento);
        assertThat(snp.registrados()).isEqualTo(1);
        assertThat(gateway.chamadas).hasSize(1);
        assertThat(gateway.chamadas).extracting(PedidoSnp::vinculo).containsExactly("EAMB#1");

        // Entrega duplicada do evento SNP: nenhum pedido novo
        ClienteSnp.Resultado snpDup = clienteSnp.processar(evento);
        assertThat(snpDup.jaProcessado()).isTrue();
        assertThat(gateway.chamadas).hasSize(1);
    }

    // ---------- Dados fictícios ----------

    private static Setor setor(long id, String email, boolean ativo) {
        return new Setor(id, "Setor " + id, email, ativo, "PR/CE", List.of());
    }

    private static Reserva reserva(long versao) {
        LocalDateTime ini = LocalDateTime.of(2030, 3, 1, 14, 0);
        return new Reserva(RESE_ID, "PR/CE", "sub-ficticio", AMBIENTE_ID, null, null, "Reunião", 10,
                List.of(new Periodo(RESE_ID * 10, ini, ini.plusHours(1))),
                List.of(new Solicitacao(RECURSO_ID, 1)), false, null, null, versao);
    }

    /** Dublê do GatewaySnp: resposta determinística, registrando os pedidos recebidos. */
    private static final class GatewaySnpMemoria implements br.mp.mpf.sisgares.eventos.snp.GatewaySnp {
        final List<PedidoSnp> chamadas = new ArrayList<>();

        @Override
        public RespostaSnp registrar(String endpoint, PedidoSnp pedido) {
            chamadas.add(pedido);
            String numero = "SNP-" + pedido.ano() + "-" + String.format("%06d", chamadas.size());
            return new RespostaSnp(numero, "https://snp.exemplo.gov.br/" + numero);
        }
    }
}
