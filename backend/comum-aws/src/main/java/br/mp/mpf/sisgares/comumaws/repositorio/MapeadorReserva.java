package br.mp.mpf.sisgares.comumaws.repositorio;

import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.indicador;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.lerNumero;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.lerTexto;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.n;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.numero;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.numeroOpcional;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.s;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.st;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.texto;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.textoOpcional;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.SolicitacaoOcupada;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Mapeamento manual entre a {@link Reserva} do domínio e os itens da partição {@code RESE#<id>}.
 *
 * <p>Layout dos itens (design.md, seção Data Models):
 * <ul>
 *   <li>{@code META}: campos da Reserva (Req. 3.4), {@code versao}, {@code criadoEm} e GSI4
 *       ({@code SOLIC#<sub>} / {@code <criadoEm>#<reseId>}).</li>
 *   <li>{@code PRES#<presId>}: Período com GSI1 ({@code RAIZ#<raizId>}, somente fora do
 *       Local_Proprio) e GSI3 ({@code UNID#<u>}).</li>
 *   <li>{@code PRES#<presId>#ENVO#<envoId>}: cópia do Período por Setor envolvido, com GSI2
 *       ("1 item por setor envolvido").</li>
 *   <li>{@code SOLI#<soliId>}: Solicitação de Recurso (base, sem GSI).</li>
 *   <li>{@code SOLI#<soliId>#PRES#<presId>}: cópia da Solicitação por Período, com GSI5
 *       ({@code RECU#<id>}).</li>
 * </ul>
 *
 * <p>Toda cópia carrega {@code RESE_ST_CANCELADA}; em reserva cancelada os atributos de GSI1 e GSI5
 * não são gravados (índice esparso), para que conflitos e disponibilidade ignorem a reserva.
 * GSI2/GSI3/GSI4 continuam populados para os painéis exibirem o status CANCELADA.
 */
public final class MapeadorReserva {

    // Atributos do META (nomes no padrão dos CSVs; Req. 3.1 e 3.4)
    static final String RESE_ID = "RESE_ID";
    static final String UNIDADE = NomesTabela.UNIDADE;
    static final String SOLICITANTE = "RESE_SOLICITANTE";
    static final String AMBI_ID = "RESE_AMBI_ID";
    static final String COMPLEMENTO = "RESE_COMPLEMENTO";
    static final String DISP_ID = "RESE_DISP_ID";
    static final String FINALIDADE = "RESE_FINALIDADE";
    static final String PARTICIPANTES = "RESE_PARTICIPANTES";
    static final String CANCELADA = "RESE_ST_CANCELADA";
    static final String CANCELADA_EM = "RESE_DTHR_CANCELAMENTO";
    static final String ALTERADA_EM = "RESE_DTHR_ALTERACAO";
    static final String VERSAO = NomesTabela.VERSAO;
    static final String CRIADO_EM = NomesTabela.CRIADO_EM;

    // Atributos do PRES
    static final String PRES_ID = "PRES_ID";
    static final String PRES_RESE_ID = "PRES_RESE_ID";
    static final String PRES_INICIO = "PRES_DTHR_INICIO";
    static final String PRES_TERMINO = "PRES_DTHR_TERMINO";
    static final String ENVO_ID = "ENVO_ID";

    // Atributos do SOLI
    static final String SOLI_ID = "SOLI_ID";
    static final String SOLI_RESE_ID = "SOLI_RESE_ID";
    static final String SOLI_RECU_ID = "SOLI_RECU_ID";
    static final String SOLI_QTD = "SOLI_QTD";

    private static final String MARCA_ENVO = Chaves.SEPARADOR + Chaves.ENVO;
    private static final String MARCA_PRES = Chaves.SEPARADOR + Chaves.PRES;

    private MapeadorReserva() {
    }

    // ---------- Gravação (usada pela tarefa 9.4) ----------

    /** Item {@code RESE#<id>/META} com o GSI4 (Minhas reservas). */
    public static Map<String, AttributeValue> paraItemMeta(Reserva r, LocalDateTime criadoEm) {
        long id = idObrigatorio(r);
        Objects.requireNonNull(criadoEm, "criadoEm");
        Map<String, AttributeValue> item = Atributos.chave(Chaves.pkReserva(id), Chaves.META);
        item.put(RESE_ID, n(id));
        item.put(UNIDADE, s(r.unidade()));
        textoOpcional(item, SOLICITANTE, r.solicitante());
        numeroOpcional(item, AMBI_ID, r.ambienteId());
        textoOpcional(item, COMPLEMENTO, r.complemento());
        numeroOpcional(item, DISP_ID, r.disposicaoId());
        textoOpcional(item, FINALIDADE, r.finalidade());
        if (r.participantes() != null) {
            item.put(PARTICIPANTES, n(r.participantes()));
        }
        item.put(CANCELADA, st(r.cancelada()));
        if (r.canceladaEm() != null) {
            item.put(CANCELADA_EM, s(Chaves.data(r.canceladaEm())));
        }
        if (r.alteradaEm() != null) {
            item.put(ALTERADA_EM, s(Chaves.data(r.alteradaEm())));
        }
        item.put(VERSAO, n(r.versao()));
        item.put(CRIADO_EM, s(Chaves.data(criadoEm)));
        if (r.solicitante() != null && !r.solicitante().isBlank()) {
            item.put(NomesTabela.GSI4PK, s(Chaves.gsi4Pk(r.solicitante())));
            item.put(NomesTabela.GSI4SK, s(Chaves.gsi4Sk(criadoEm, id)));
        }
        return item;
    }

    /**
     * Monta todos os itens PRES e SOLI (com atributos de GSI) de uma Reserva.
     *
     * <p>Exige ids em todos os Períodos. O SOLI_ID usado é o RECU_ID da solicitação (uma
     * solicitação por Recurso na reserva); para preservar SOLI_ID de origem (seed), use
     * {@link #itensSolicitacao(Reserva, long, Solicitacao)}.
     *
     * @param raizId  raiz da árvore do Ambiente; ignorado (pode ser {@code null}) em Local_Proprio
     * @param setores ENVO_IDs envolvidos (setores do Ambiente e dos Recursos), um item GSI2 por setor
     */
    public static List<Map<String, AttributeValue>> itensFilhos(Reserva r, Long raizId, Collection<Long> setores) {
        List<Map<String, AttributeValue>> itens = new ArrayList<>();
        for (Periodo p : r.periodos()) {
            itens.addAll(itensPeriodo(r, p, raizId, setores));
        }
        for (Solicitacao sol : r.solicitacoes()) {
            itens.addAll(itensSolicitacao(r, sol.recursoId(), sol));
        }
        return itens;
    }

    /** Item {@code PRES#<id>} (GSI1/GSI3) e as cópias {@code PRES#<id>#ENVO#<envoId>} (GSI2). */
    public static List<Map<String, AttributeValue>> itensPeriodo(Reserva r, Periodo p, Long raizId,
                                                                 Collection<Long> setores) {
        long reseId = idObrigatorio(r);
        long presId = Objects.requireNonNull(p.id(), "id do período é obrigatório");
        String pk = Chaves.pkReserva(reseId);
        String skData = Chaves.skDataReserva(p.inicio(), reseId);
        List<Map<String, AttributeValue>> itens = new ArrayList<>();

        Map<String, AttributeValue> principal = basePeriodo(pk, Chaves.skPeriodo(presId), r, p, reseId);
        if (!r.cancelada() && !r.localProprio()) {
            // Local_Proprio não participa de conflito/grade; cancelada sai do índice esparso
            principal.put(NomesTabela.GSI1PK, s(Chaves.gsi1Pk(Objects.requireNonNull(raizId, "raizId"))));
            principal.put(NomesTabela.GSI1SK, s(skData));
        }
        principal.put(NomesTabela.GSI3PK, s(Chaves.gsi3Pk(r.unidade())));
        principal.put(NomesTabela.GSI3SK, s(skData));
        itens.add(principal);

        if (setores != null) {
            for (Long envoId : new LinkedHashSet<>(setores)) {
                Map<String, AttributeValue> copia = basePeriodo(pk,
                        Chaves.skPeriodo(presId) + MARCA_ENVO + envoId, r, p, reseId);
                copia.put(ENVO_ID, n(envoId));
                copia.put(NomesTabela.GSI2PK, s(Chaves.gsi2Pk(envoId)));
                copia.put(NomesTabela.GSI2SK, s(skData));
                itens.add(copia);
            }
        }
        return itens;
    }

    /** Item {@code SOLI#<soliId>} e uma cópia {@code SOLI#<soliId>#PRES#<presId>} por Período (GSI5). */
    public static List<Map<String, AttributeValue>> itensSolicitacao(Reserva r, long soliId, Solicitacao sol) {
        long reseId = idObrigatorio(r);
        String pk = Chaves.pkReserva(reseId);
        List<Map<String, AttributeValue>> itens = new ArrayList<>();
        itens.add(baseSolicitacao(pk, Chaves.skSolicitacao(soliId), r, soliId, sol, reseId));
        for (Periodo p : r.periodos()) {
            long presId = Objects.requireNonNull(p.id(), "id do período é obrigatório");
            Map<String, AttributeValue> copia = baseSolicitacao(pk,
                    Chaves.skSolicitacao(soliId) + MARCA_PRES + presId, r, soliId, sol, reseId);
            copia.put(PRES_ID, n(presId));
            copia.put(PRES_INICIO, s(Chaves.data(p.inicio())));
            copia.put(PRES_TERMINO, s(Chaves.data(p.termino())));
            if (!r.cancelada()) {
                copia.put(NomesTabela.GSI5PK, s(Chaves.gsi5Pk(sol.recursoId())));
                copia.put(NomesTabela.GSI5SK, s(Chaves.skDataReserva(p.inicio(), reseId)));
            }
            itens.add(copia);
        }
        return itens;
    }

    private static Map<String, AttributeValue> basePeriodo(String pk, String sk, Reserva r, Periodo p, long reseId) {
        Map<String, AttributeValue> item = Atributos.chave(pk, sk);
        item.put(PRES_ID, n(p.id()));
        item.put(PRES_RESE_ID, n(reseId));
        item.put(PRES_INICIO, s(Chaves.data(p.inicio())));
        item.put(PRES_TERMINO, s(Chaves.data(p.termino())));
        item.put(UNIDADE, s(r.unidade()));
        numeroOpcional(item, AMBI_ID, r.ambienteId());
        item.put(CANCELADA, st(r.cancelada()));
        return item;
    }

    private static Map<String, AttributeValue> baseSolicitacao(String pk, String sk, Reserva r, long soliId,
                                                               Solicitacao sol, long reseId) {
        Map<String, AttributeValue> item = Atributos.chave(pk, sk);
        item.put(SOLI_ID, n(soliId));
        item.put(SOLI_RESE_ID, n(reseId));
        item.put(SOLI_RECU_ID, n(sol.recursoId()));
        item.put(SOLI_QTD, n(sol.quantidade()));
        item.put(CANCELADA, st(r.cancelada()));
        return item;
    }

    private static long idObrigatorio(Reserva r) {
        return Objects.requireNonNull(r.id(), "id da reserva é obrigatório");
    }

    // ---------- Leitura ----------

    /** Classificação dos itens da partição {@code RESE#<id>} pela SK. */
    static boolean ehMeta(Map<String, AttributeValue> item) {
        return Chaves.META.equals(lerTexto(item, NomesTabela.SK));
    }

    /** Item principal do Período (exclui as cópias por setor). */
    static boolean ehPeriodo(Map<String, AttributeValue> item) {
        String sk = lerTexto(item, NomesTabela.SK);
        return sk != null && sk.startsWith(Chaves.PRES) && !sk.contains(MARCA_ENVO);
    }

    /** Item base da Solicitação (exclui as cópias por período). */
    static boolean ehSolicitacao(Map<String, AttributeValue> item) {
        String sk = lerTexto(item, NomesTabela.SK);
        return sk != null && sk.startsWith(Chaves.SOLI) && !sk.contains(MARCA_PRES);
    }

    /** Monta a Reserva a partir do META e dos itens principais de PRES e SOLI. */
    static Reserva deItens(Map<String, AttributeValue> meta, List<Periodo> periodos, List<Solicitacao> solicitacoes) {
        Long participantes = lerNumero(meta, PARTICIPANTES);
        return new Reserva(
                numero(meta, RESE_ID),
                texto(meta, UNIDADE),
                lerTexto(meta, SOLICITANTE),
                lerNumero(meta, AMBI_ID),
                lerTexto(meta, COMPLEMENTO),
                lerNumero(meta, DISP_ID),
                lerTexto(meta, FINALIDADE),
                participantes == null ? null : participantes.intValue(),
                periodos,
                solicitacoes,
                indicador(meta, CANCELADA),
                dataOpcional(meta, CANCELADA_EM),
                dataOpcional(meta, ALTERADA_EM),
                numero(meta, VERSAO));
    }

    static Periodo dePeriodo(Map<String, AttributeValue> item) {
        return new Periodo(numero(item, PRES_ID), data(item, PRES_INICIO), data(item, PRES_TERMINO));
    }

    static Solicitacao deSolicitacao(Map<String, AttributeValue> item) {
        return new Solicitacao(numero(item, SOLI_RECU_ID), (int) numero(item, SOLI_QTD));
    }

    /** Item do GSI1 → Período ocupado do Ambiente. */
    static PeriodoOcupado dePeriodoOcupado(Map<String, AttributeValue> item) {
        return new PeriodoOcupado(numero(item, PRES_RESE_ID), numero(item, AMBI_ID), dePeriodo(item));
    }

    /** Item do GSI5 → quantidade comprometida do Recurso no Período. */
    static SolicitacaoOcupada deSolicitacaoOcupada(Map<String, AttributeValue> item) {
        Periodo p = new Periodo(lerNumero(item, PRES_ID), data(item, PRES_INICIO), data(item, PRES_TERMINO));
        return new SolicitacaoOcupada(numero(item, SOLI_RESE_ID), numero(item, SOLI_RECU_ID),
                (int) numero(item, SOLI_QTD), p);
    }

    static boolean cancelada(Map<String, AttributeValue> item) {
        return indicador(item, CANCELADA);
    }

    /** RESE_ID de qualquer item de índice (PRES, cópia por setor ou META). */
    static long reservaId(Map<String, AttributeValue> item) {
        Long id = lerNumero(item, PRES_RESE_ID);
        return id != null ? id : numero(item, RESE_ID);
    }

    private static LocalDateTime data(Map<String, AttributeValue> item, String nome) {
        return LocalDateTime.parse(texto(item, nome));
    }

    private static LocalDateTime dataOpcional(Map<String, AttributeValue> item, String nome) {
        String valor = lerTexto(item, nome);
        return valor == null ? null : LocalDateTime.parse(valor);
    }
}
