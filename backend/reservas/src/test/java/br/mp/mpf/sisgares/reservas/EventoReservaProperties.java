package br.mp.mpf.sisgares.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Propriedades do Evento_Reserva publicado no barramento (tarefa 10.4).
 *
 * <p>O Evento_Reserva carrega somente IDs técnicos, versão e tipo; os consumidores releem a
 * Reserva pelo {@code reseId}. Portanto o {@code detail} serializado nunca pode conter
 * Dados_Pessoais (nome do Solicitante, e-mail, CPF, finalidade ou complemento), mesmo quando a
 * Reserva de origem os possui.
 *
 * <p><b>Validates: Requirements 2.3, 6.7</b>
 */
class EventoReservaProperties {

    private static final List<String> TIPOS = List.of(ServicoReservas.EVENTO_CRIADA,
            ServicoReservas.EVENTO_ALTERADA, ServicoReservas.EVENTO_CANCELADA);

    /** Reproduz a montagem do evento feita por {@code ServicoReservas.evento(tipo, reserva)}. */
    private static EventoOutbox eventoDe(String tipo, Reserva r) {
        return new EventoOutbox(UUID.randomUUID().toString(), tipo, r.id(), r.versao(), Instant.EPOCH);
    }

    /**
     * Feature: sisgares-reservas, Property 15: Evento_Reserva serializado sem dados pessoais.
     *
     * <p>Para qualquer Reserva com Dados_Pessoais arbitrários, o {@code detail} publicado contém
     * apenas {@code eventoId}, {@code reseId}, {@code versao} e {@code tipo}, e o JSON serializado
     * não revela o nome, o e-mail, o CPF, a finalidade nem o complemento da Reserva.
     */
    @Property(tries = 100)
    void eventoSerializadoNaoContemDadosPessoais(@ForAll("reservas") Reserva reserva,
                                                 @ForAll("tipos") String tipo) throws Exception {
        EventoOutbox evento = eventoDe(tipo, reserva);
        String detalhe = Json.escrever(PublicadorEventos.detalhe(evento));

        JsonNode json = Json.mapper().readTree(detalhe);
        // Somente os campos técnicos: nada de Dados_Pessoais
        List<String> campos = new ArrayList<>();
        json.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("eventoId", "reseId", "versao", "tipo");
        assertThat(json.get("reseId").asLong()).isEqualTo(reserva.id());
        assertThat(json.get("versao").asLong()).isEqualTo(reserva.versao());
        assertThat(json.get("tipo").asText()).isEqualTo(tipo);

        // Nenhum Dado_Pessoal da Reserva aparece no texto publicado
        for (String pessoal : dadosPessoais(reserva)) {
            if (pessoal != null && !pessoal.isBlank()) {
                assertThat(detalhe).doesNotContain(pessoal);
            }
        }
    }

    /** Dados_Pessoais e textos livres que jamais podem vazar no evento. */
    private static List<String> dadosPessoais(Reserva r) {
        return List.of(r.solicitante(), r.finalidade() == null ? "" : r.finalidade(),
                r.complemento() == null ? "" : r.complemento());
    }

    @Provide
    Arbitrary<String> tipos() {
        return Arbitraries.of(TIPOS);
    }

    @Provide
    Arbitrary<Reserva> reservas() {
        Arbitrary<Long> id = Arbitraries.longs().between(1, 1_000_000);
        Arbitrary<Long> versao = Arbitraries.longs().between(1, 50);
        // Finalidade e complemento com Dados_Pessoais plausíveis (e-mail, CPF, nome)
        Arbitrary<String> textoComPessoais = Arbitraries.of(
                "Reunião com joao.silva@exemplo.gov.br",
                "Visita de Maria Oliveira e Carlos Souza",
                "Atendimento CPF 123.456.789-09",
                "Entrevista: ana@exemplo.gov.br, 987.654.321-00",
                "Planejamento do trimestre",
                "");
        Arbitrary<String> solicitante = Arbitraries.of(
                "sub-" + UUID.randomUUID(), "joao.silva@exemplo.gov.br", "Maria Oliveira");
        return Combinators.combine(id, versao, textoComPessoais, textoComPessoais, solicitante,
                        periodos(), solicitacoes(), Arbitraries.of(true, false))
                .as((reseId, v, finalidade, complemento, sub, periodos, solicitacoes, cancelada) ->
                        new Reserva(reseId, "PR/CE", sub, 10L, complemento, null, finalidade, 10,
                                periodos, solicitacoes, cancelada, null, LocalDateTime.of(2030, 1, 1, 8, 0), v));
    }

    @Provide
    Arbitrary<List<Periodo>> periodos() {
        Arbitrary<Periodo> periodo = Combinators.combine(
                        Arbitraries.longs().between(1, 1000),
                        Arbitraries.integers().between(7, 20))
                .as((pid, hora) -> new Periodo(pid,
                        LocalDateTime.of(2030, 3, 4, hora, 0),
                        LocalDateTime.of(2030, 3, 4, hora, 30)));
        return periodo.list().ofMinSize(1).ofMaxSize(3);
    }

    @Provide
    Arbitrary<List<Solicitacao>> solicitacoes() {
        Arbitrary<Solicitacao> solicitacao = Combinators.combine(
                        Arbitraries.longs().between(1, 500),
                        Arbitraries.integers().between(1, 5))
                .as((recursoId, quantidade) -> new Solicitacao(recursoId, quantidade));
        return solicitacao.list().ofMaxSize(3);
    }
}
