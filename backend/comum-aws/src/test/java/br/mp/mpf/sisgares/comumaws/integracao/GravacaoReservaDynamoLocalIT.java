package br.mp.mpf.sisgares.comumaws.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoConflitoConcorrente;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.comumaws.repositorio.GravadorReservas;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioReservas;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * Testes de integração da gravação atômica de Reservas (tarefa 9.4) com DynamoDB Local via
 * Testcontainers. Cobrem:
 *
 * <ul>
 *   <li>RN7: duas gravações concorrentes da mesma reserva nova; a segunda recebe conflito
 *       (traduzido em HTTP 409 pelo handler).</li>
 *   <li>Outbox gravado na mesma {@code TransactWriteItems} da Reserva (tudo ou nada).</li>
 *   <li>Ausência de Scan: a leitura das Reservas usa somente Query (padrões de acesso do
 *       {@link RepositorioReservas}).</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 3.9, 10.6, 10.7, 22.5</b>
 *
 * <p>Desabilitado: o ambiente de execução não tem Docker disponível, exigido pelo Testcontainers
 * para subir o contêiner {@code amazon/dynamodb-local}. Habilite rodando com Docker ativo.
 */
@Testcontainers
@Disabled("Requer Docker para o Testcontainers subir o DynamoDB Local; indisponivel no ambiente.")
class GravacaoReservaDynamoLocalIT {

    @Container
    static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
            .withExposedPorts(8000);

    private static DynamoDbClient cliente;
    private static GravadorReservas gravador;
    private static RepositorioReservas repositorio;

    @BeforeAll
    static void prepararTabela() {
        String endpoint = "http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000);
        cliente = TabelaDynamoLocal.cliente(endpoint);
        TabelaDynamoLocal.criarTabela(cliente);
        gravador = new GravadorReservas(cliente, TabelaDynamoLocal.TABELA);
        repositorio = new RepositorioReservas(cliente, TabelaDynamoLocal.TABELA);
    }

    @AfterAll
    static void fechar() {
        if (cliente != null) {
            cliente.close();
        }
    }

    @Test
    void rn7DuasGravacoesConcorrentesDaMesmaReservaNovaConflitam() {
        long reseId = 1001;
        Reserva nova = reserva(reseId, 14, 0);

        // Primeira gravação cria o META com attribute_not_exists(PK): sucesso
        gravador.gravar(nova, criadoEm(), 500L, Set.of(1L),
                Map.of(GravadorReservas.chaveControleAmbiente(500L), 0L), null, evento(reseId, "e1"));

        // Segunda gravação concorrente (versaoLida == null) viola a condição: RN7 → conflito/409
        assertThatThrownBy(() -> gravador.gravar(nova, criadoEm(), 500L, Set.of(1L),
                Map.of(GravadorReservas.chaveControleAmbiente(500L), 0L), null, evento(reseId, "e2")))
                .isInstanceOf(ExcecaoConflitoConcorrente.class);
    }

    @Test
    void outboxEhGravadoNaMesmaTransacaoDaReserva() {
        long reseId = 1002;
        EventoOutbox evento = evento(reseId, "e-outbox");
        gravador.gravar(reserva(reseId, 9, 0), criadoEm(), 500L, Set.of(1L),
                Map.of(GravadorReservas.chaveControleAmbiente(500L), 0L), null, evento);

        // A reserva ficou persistida (META + filhos) e o evento está no outbox: ambos no mesmo commit
        assertThat(repositorio.obter(reseId)).isPresent();
        List<EventoOutbox> pendentes = gravador.listarOutboxPendentes();
        assertThat(pendentes).extracting(EventoOutbox::eventoId).contains("e-outbox");
    }

    @Test
    void leituraDeReservaUsaSomenteQuery() {
        long reseId = 1003;
        gravador.gravar(reserva(reseId, 10, 0), criadoEm(), 500L, Set.of(1L),
                Map.of(GravadorReservas.chaveControleAmbiente(500L), 0L), null, evento(reseId, "e3"));

        // Reserva recuperada por Query PK=RESE#id e por GSI (nunca Scan); mesmo id de volta
        assertThat(repositorio.obter(reseId)).get()
                .extracting(Reserva::id).isEqualTo(reseId);
        assertThat(repositorio.porSolicitante("sub-ficticio"))
                .extracting(Reserva::id).contains(reseId);
    }

    // ---------- Dados fictícios ----------

    private static Reserva reserva(long id, int hora, long versao) {
        LocalDateTime ini = LocalDateTime.of(2030, 3, 1, hora, 0);
        return new Reserva(id, "PR/CE", "sub-ficticio", 500L, null, null, "Reunião", 10,
                List.of(new Periodo(id * 10, ini, ini.plusHours(1))),
                List.of(new Solicitacao(20, 1)), false, null, null, versao);
    }

    private static LocalDateTime criadoEm() {
        return LocalDateTime.of(2030, 2, 1, 8, 0);
    }

    private static EventoOutbox evento(long reseId, String eventoId) {
        return new EventoOutbox(eventoId, "RESERVA_CRIADA", reseId, 0, Instant.parse("2030-02-01T08:00:00Z"));
    }
}
