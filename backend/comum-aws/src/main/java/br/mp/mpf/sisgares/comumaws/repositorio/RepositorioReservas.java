package br.mp.mpf.sisgares.comumaws.repositorio;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.SolicitacaoOcupada;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Leitura de Reservas somente com Query, nunca Scan (Req. 3.9).
 *
 * <p>Padrões de acesso (design.md, seção Data Models):
 * <ul>
 *   <li>Reserva completa: Query {@code PK = RESE#<id>}.</li>
 *   <li>Conflitos/grade: Query GSI1 por raiz com janela {@code [ini − 1 dia − 30 min, fim + 30 min]}.</li>
 *   <li>Disponibilidade de Recurso: Query GSI5 com a mesma janela.</li>
 *   <li>Painel Atendente / Admin: Query GSI2 / GSI3 por data de início.</li>
 *   <li>Minhas reservas: Query GSI4.</li>
 * </ul>
 * Todas as expressões são parametrizadas em {@link OperacoesDynamo}.
 */
public final class RepositorioReservas {

    /** Recuo da janela: períodos são limitados a 24 h pela faixa de horário, mais a Margem. */
    static final Duration RECUO_JANELA = Duration.ofDays(1).plus(ConstantesDominio.MARGEM);

    private final OperacoesDynamo dynamo;

    public RepositorioReservas(DynamoDbClient cliente, String nomeTabela) {
        this.dynamo = new OperacoesDynamo(cliente, nomeTabela);
    }

    /** Reserva completa com Períodos e Solicitações; vazio se não existir. */
    public Optional<Reserva> obter(long id) {
        List<Map<String, AttributeValue>> itens = dynamo.consultar(Chaves.pkReserva(id));
        Map<String, AttributeValue> meta = null;
        List<Periodo> periodos = new ArrayList<>();
        List<Solicitacao> solicitacoes = new ArrayList<>();
        for (Map<String, AttributeValue> item : itens) {
            if (MapeadorReserva.ehMeta(item)) {
                meta = item;
            } else if (MapeadorReserva.ehPeriodo(item)) {
                periodos.add(MapeadorReserva.dePeriodo(item));
            } else if (MapeadorReserva.ehSolicitacao(item)) {
                solicitacoes.add(MapeadorReserva.deSolicitacao(item));
            }
            // Demais itens (VERS, SNP, NOTI e cópias de índice) não compõem a Reserva
        }
        if (meta == null) {
            return Optional.empty();
        }
        periodos.sort(Comparator.comparing(Periodo::inicio));
        return Optional.of(MapeadorReserva.deItens(meta, periodos, solicitacoes));
    }

    /**
     * Períodos de reservas não canceladas na árvore {@code raizId} candidatos a conflito (RN5/RN6)
     * ou exibição na grade. A sobreposição efetiva com Margem é decidida no domínio.
     */
    public List<PeriodoOcupado> periodosOcupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim) {
        return consultarJanela(NomesTabela.GSI1, Chaves.gsi1Pk(raizId), ini, fim).stream()
                .filter(item -> !MapeadorReserva.cancelada(item))
                .map(MapeadorReserva::dePeriodoOcupado)
                .toList();
    }

    /** Quantidades do Recurso comprometidas por reservas não canceladas na janela (RN8). */
    public List<SolicitacaoOcupada> solicitacoesOcupadasPorRecurso(long recursoId, LocalDateTime ini,
                                                                   LocalDateTime fim) {
        return consultarJanela(NomesTabela.GSI5, Chaves.gsi5Pk(recursoId), ini, fim).stream()
                .filter(item -> !MapeadorReserva.cancelada(item))
                .map(MapeadorReserva::deSolicitacaoOcupada)
                .toList();
    }

    /** Painel do Atendente: reservas com Período iniciando em {@code [de, ate]} que envolvem o Setor. */
    public List<Reserva> porSetor(long envoId, LocalDateTime de, LocalDateTime ate) {
        return carregar(consultarPeriodo(NomesTabela.GSI2, Chaves.gsi2Pk(envoId), de, ate));
    }

    /** Painel do Administrador: reservas da Unidade_Macro com Período iniciando em {@code [de, ate]}. */
    public List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate) {
        return carregar(consultarPeriodo(NomesTabela.GSI3, Chaves.gsi3Pk(unidade), de, ate));
    }

    /** Minhas reservas: todas as reservas do Solicitante, mais recentes primeiro. */
    public List<Reserva> porSolicitante(String sub) {
        List<Map<String, AttributeValue>> itens =
                new ArrayList<>(dynamo.consultarIndice(NomesTabela.GSI4, Chaves.gsi4Pk(sub), null, null));
        // GSI4SK = <criadoEm>#<reseId>: ordem crescente do índice invertida
        Collections.reverse(itens);
        return carregar(itens);
    }

    // ---------- Auxiliares ----------

    /** Query com a janela de conflito: {@code ini − 1 dia − 30 min} até {@code fim + 30 min}. */
    private List<Map<String, AttributeValue>> consultarJanela(String indice, String pk,
                                                              LocalDateTime ini, LocalDateTime fim) {
        Objects.requireNonNull(ini, "ini");
        Objects.requireNonNull(fim, "fim");
        return consultarPeriodo(indice, pk, ini.minus(RECUO_JANELA), fim.plus(ConstantesDominio.MARGEM));
    }

    /** Query com {@code SK BETWEEN <de> AND <ate>#\uffff} (inclui todos os ids do último instante). */
    private List<Map<String, AttributeValue>> consultarPeriodo(String indice, String pk,
                                                               LocalDateTime de, LocalDateTime ate) {
        Objects.requireNonNull(de, "de");
        Objects.requireNonNull(ate, "ate");
        return dynamo.consultarIndice(indice, pk, Chaves.data(de), Chaves.limiteSuperior(Chaves.data(ate)));
    }

    /** Carrega cada Reserva distinta (na ordem do índice) com Query por {@code PK = RESE#id}. */
    private List<Reserva> carregar(List<Map<String, AttributeValue>> itensIndice) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Map<String, AttributeValue> item : itensIndice) {
            ids.add(MapeadorReserva.reservaId(item));
        }
        List<Reserva> reservas = new ArrayList<>(ids.size());
        for (long id : ids) {
            obter(id).ifPresent(reservas::add);
        }
        return reservas;
    }
}
