package br.mp.mpf.sisgares.comumaws.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.dominio.Configuracao;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Cache de 60 s da Configuração medido pelo Clock e uso de Query parametrizada.
 *
 * <p><b>Validates: Requirements 3.5, 8.6</b>
 */
class RepositorioConfiguracaoTest {

    /** Relógio ajustável para simular a passagem do tempo. */
    private static final class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2025-01-10T12:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Fortaleza");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    /** Cliente em memória que responde apenas Query na partição CONFIG e registra as requisições. */
    private static final class ClienteConfig implements DynamoDbClient {
        final List<QueryRequest> consultas = new ArrayList<>();
        int antecedencia = 60;

        @Override
        public QueryResponse query(QueryRequest requisicao) {
            consultas.add(requisicao);
            Map<String, AttributeValue> global = new HashMap<>(Atributos.chave("CONFIG", "GLOBAL"));
            global.put("antecedenciaMinutos", Atributos.n(antecedencia));
            global.put("faixaMinima", Atributos.s("07:00"));
            global.put("faixaMaxima", Atributos.s("22:00"));
            Map<String, AttributeValue> unidade = new HashMap<>(Atributos.chave("CONFIG", "UNID#PR/CE"));
            unidade.put("unidade", Atributos.s("PR/CE"));
            unidade.put("faixaMinima", Atributos.s("08:00"));
            unidade.put("faixaMaxima", Atributos.s("18:00"));
            return QueryResponse.builder().items(global, unidade).build();
        }

        @Override
        public String serviceName() {
            return "dynamodb";
        }

        @Override
        public void close() {
        }
    }

    @Test
    void reutilizaCacheAte60sERecarregaDepois() {
        ClienteConfig cliente = new ClienteConfig();
        RelogioAjustavel relogio = new RelogioAjustavel();
        RepositorioConfiguracao repo = new RepositorioConfiguracao(cliente, "sisgares", relogio);

        Configuracao primeira = repo.obter();
        assertThat(primeira.antecedenciaMinutos()).isEqualTo(60);
        assertThat(primeira.faixaAplicavel("PR/CE").minimo()).isEqualTo(LocalTime.of(8, 0));

        cliente.antecedencia = 120;
        relogio.avancar(Duration.ofSeconds(59));
        assertThat(repo.obter().antecedenciaMinutos()).isEqualTo(60);
        assertThat(cliente.consultas).hasSize(1);

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(repo.obter().antecedenciaMinutos()).isEqualTo(120);
        assertThat(cliente.consultas).hasSize(2);
    }

    @Test
    void consultaUsaValoresParametrizados() {
        ClienteConfig cliente = new ClienteConfig();
        new RepositorioConfiguracao(cliente, "sisgares", new RelogioAjustavel()).obter();

        QueryRequest q = cliente.consultas.get(0);
        assertThat(q.keyConditionExpression()).isEqualTo("#pk = :pk").doesNotContain("CONFIG");
        assertThat(q.expressionAttributeValues().get(":pk").s()).isEqualTo("CONFIG");
    }
}
