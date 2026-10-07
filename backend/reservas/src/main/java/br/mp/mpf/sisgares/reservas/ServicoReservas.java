package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoNaoEncontrado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.comumaws.repositorio.EventoOutbox;
import br.mp.mpf.sisgares.comumaws.repositorio.GravadorReservas;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.ArvoreAmbientes;
import br.mp.mpf.sisgares.dominio.CalculadoraStatus;
import br.mp.mpf.sisgares.dominio.CodigosRegra;
import br.mp.mpf.sisgares.dominio.ComparadorVersoes;
import br.mp.mpf.sisgares.dominio.Conflito;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.ContextoValidacao;
import br.mp.mpf.sisgares.dominio.DetectorConflito;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Recurso;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.SolicitacaoOcupada;
import br.mp.mpf.sisgares.dominio.ValidadorReserva;
import br.mp.mpf.sisgares.dominio.VersaoReserva;
import br.mp.mpf.sisgares.dominio.Violacao;
import br.mp.mpf.sisgares.reservas.dto.CancelamentoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.ConversorReserva;
import br.mp.mpf.sisgares.reservas.dto.ReservaAlteracaoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.ReservaRequisicao;
import br.mp.mpf.sisgares.reservas.dto.ReservaResposta;
import br.mp.mpf.sisgares.reservas.dto.ValidadorEntrada;
import br.mp.mpf.sisgares.reservas.dto.VerificarPeriodoRequisicao;
import br.mp.mpf.sisgares.reservas.dto.VerificarPeriodoResposta;
import br.mp.mpf.sisgares.reservas.dto.VersaoResposta;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso de {@code /api/reservas}: orquestra leitura, validação no domínio e gravação atômica.
 *
 * <p>Regras de negócio (RN1–RN13) ficam no {@link ValidadorReserva}; aqui só se monta o
 * {@link ContextoValidacao}, aplica-se a autorização simplificada (o Cedar entra na tarefa 15.2) e
 * grava-se via {@link PortaReservas}. O evento é gravado no outbox na mesma transação e, após o
 * commit, publicado pelo {@link PublicadorEventos} e removido do outbox; se a publicação falhar, o
 * {@link RepublicadorOutbox} tenta de novo (Req. 2.3, 2.8).
 *
 * <p>Autorização simplificada:
 * <ul>
 *   <li>Solicitante altera, cancela e vê versões somente das próprias Reservas;</li>
 *   <li>Administrador acessa as Reservas da mesma Unidade_Macro;</li>
 *   <li>Setor_Atendente envolvido pode ver o detalhe (Req. 6.17);</li>
 *   <li>fora disso, 403.</li>
 * </ul>
 */
public final class ServicoReservas {

    public static final String GRUPO_SOLICITANTE = "Solicitante";
    public static final String GRUPO_ADMINISTRADOR = "Administrador";
    public static final String GRUPO_ATENDENTE = "Setor_Atendente";

    // Tipos do Evento_Reserva: também são o detail-type e são aceitos por TipoEvento.de (eventos)
    public static final String EVENTO_CRIADA = "ReservaCriada";
    public static final String EVENTO_ALTERADA = "ReservaAlterada";
    public static final String EVENTO_CANCELADA = "ReservaCancelada";

    /** Código usado quando o Ambiente informado não pode ser reservado pela unidade do usuário. */
    public static final String AMBIENTE_INDISPONIVEL = "AMBIENTE_INDISPONIVEL";

    /** Janela padrão da listagem por unidade (Administrador) quando {@code de}/{@code ate} faltam. */
    static final int DIAS_ANTES_PADRAO = 30;
    static final int DIAS_DEPOIS_PADRAO = 90;

    private final PortaReservas reservas;
    private final PortaCatalogo catalogo;
    private final Clock relogio;
    private final ResolvedorNome resolvedorNome;
    private final PublicadorEventos publicador;
    private final LogEstruturado log;
    private final ValidadorReserva validador = new ValidadorReserva();
    private final DetectorConflito detector = new DetectorConflito();
    private final CalculadoraStatus calculadoraStatus;

    /**
     * Fonte do nome do Solicitante. O modelo atual não persiste nomes; a implementação padrão
     * devolve o nome do próprio usuário autenticado quando ele é o Solicitante.
     */
    @FunctionalInterface
    public interface ResolvedorNome {
        /** Nome do Solicitante {@code sub}, visto pelo {@code leitor} (já autorizado a vê-lo). */
        Optional<String> nome(String sub, Principal leitor);

        /** Resolvedor padrão: somente o nome do próprio leitor (claim {@code name}). */
        static ResolvedorNome doPrincipal() {
            return (sub, leitor) -> sub != null && sub.equals(leitor.sub())
                    ? Optional.ofNullable(leitor.nome())
                    : Optional.empty();
        }
    }

    /** Construtor sem barramento (testes e modo local): o evento só é registrado em log. */
    public ServicoReservas(PortaReservas reservas, PortaCatalogo catalogo, Clock relogio,
                           ResolvedorNome resolvedorNome) {
        this(reservas, catalogo, relogio, resolvedorNome, null, new LogEstruturado(relogio, "reservas"));
    }

    public ServicoReservas(PortaReservas reservas, PortaCatalogo catalogo, Clock relogio,
                           ResolvedorNome resolvedorNome, PublicadorEventos publicador, LogEstruturado log) {
        this.reservas = Objects.requireNonNull(reservas, "reservas");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.resolvedorNome = Objects.requireNonNull(resolvedorNome, "resolvedorNome");
        this.log = Objects.requireNonNull(log, "log");
        this.publicador = publicador != null ? publicador : PublicadorEventos.nulo(this.log);
        this.calculadoraStatus = new CalculadoraStatus(relogio);
    }

    // ---------- Casos de uso ----------

    /** POST: valida (RN1–RN9), grava a versão 1 e o evento no outbox (Req. 9.14, 9.15). */
    public ReservaResposta criar(ReservaRequisicao req, Principal principal) {
        exigirAutenticado(principal);
        if (!principal.pertenceA(GRUPO_SOLICITANTE) && !principal.pertenceA(GRUPO_ADMINISTRADOR)) {
            throw new ExcecaoAcessoNegado("Somente solicitantes podem cadastrar reservas.");
        }
        Reserva candidata = ConversorReserva.paraDominio(req, principal);
        Preparacao prep = preparar(candidata, null);
        List<Violacao> violacoes = new ArrayList<>(prep.violacoesEntrada());
        violacoes.addAll(validador.validar(candidata, prep.contexto()));
        lancarSeHouver(violacoes);

        LocalDateTime agora = agora();
        long id = reservas.reservarIds(PortaReservas.SEQUENCIA_RESERVA, 1);
        // Na criação, ids de Período vindos do cliente são descartados
        List<Periodo> periodos = atribuirIdsPeriodos(candidata.periodos().stream()
                .map(p -> new Periodo(null, p.inicio(), p.termino())).toList());
        Reserva nova = copiar(candidata, id, candidata.unidade(), candidata.solicitante(), periodos,
                false, null, agora, 1L);
        EventoOutbox evento = evento(EVENTO_CRIADA, nova);
        reservas.gravar(nova, agora, prep.raizId(), prep.setores(), prep.versoesCtrl(), null, evento);
        publicarAposCommit(evento);
        return resposta(nova, principal);
    }

    /**
     * GET lista: {@code minhas=true} (padrão) devolve as Reservas do usuário; {@code minhas=false}
     * é restrito ao Administrador e devolve as da Unidade_Macro com período iniciando em [de, ate].
     */
    public List<ReservaResposta> listar(Principal principal, boolean minhas, LocalDate de, LocalDate ate) {
        exigirAutenticado(principal);
        List<Reserva> lista;
        if (minhas) {
            lista = reservas.porSolicitante(principal.sub());
        } else {
            if (!ehAdministrador(principal) || vazio(principal.unidade())) {
                throw new ExcecaoAcessoNegado("Somente administradores listam as reservas da unidade.");
            }
            LocalDate hoje = agora().toLocalDate();
            LocalDate inicio = de != null ? de : hoje.minusDays(DIAS_ANTES_PADRAO);
            LocalDate fim = ate != null ? ate : hoje.plusDays(DIAS_DEPOIS_PADRAO);
            lista = reservas.porUnidade(principal.unidade(), inicio.atStartOfDay(), fim.atTime(23, 59, 59));
        }
        return lista.stream().map(r -> resposta(r, principal)).toList();
    }

    /** GET detalhe: dono, Administrador da unidade ou Setor_Atendente envolvido. */
    public ReservaResposta obter(long id, Principal principal) {
        exigirAutenticado(principal);
        Reserva r = carregar(id);
        if (!podeVer(r, principal)) {
            throw new ExcecaoAcessoNegado("Você não tem permissão para ver esta reserva.");
        }
        return resposta(r, principal);
    }

    /** PUT: valida a alteração (RN12 + RN1–RN9) e grava nova versão (Req. 12.1, 12.4). */
    public ReservaResposta alterar(long id, ReservaAlteracaoRequisicao req, Principal principal) {
        exigirAutenticado(principal);
        Reserva anterior = carregar(id);
        exigirPodeAlterar(anterior, principal);
        // paraDominio aplica Bean Validation; corpo ausente vira 422
        Reserva convertida = ConversorReserva.paraDominio(req == null ? null : req.paraRequisicao(), principal,
                id, anterior.versao());
        // Solicitante e Unidade_Macro são preservados (o Administrador pode alterar reserva de outro)
        List<Periodo> periodos = reconciliarPeriodos(convertida.periodos(), anterior.periodos());
        Reserva candidata = copiar(convertida, id, anterior.unidade(), anterior.solicitante(), periodos,
                false, null, anterior.alteradaEm(), anterior.versao());

        Preparacao prep = preparar(candidata, anterior);
        List<Violacao> violacoes = new ArrayList<>(prep.violacoesEntrada());
        violacoes.addAll(validador.validarAlteracao(candidata, prep.contexto()));
        lancarSeHouver(violacoes);

        LocalDateTime agora = agora();
        Reserva nova = copiar(candidata, id, candidata.unidade(), candidata.solicitante(),
                atribuirIdsPeriodos(candidata.periodos()), false, null, agora, anterior.versao() + 1);
        EventoOutbox evento = evento(EVENTO_ALTERADA, nova);
        reservas.gravar(nova, criadoEm(id, agora), prep.raizId(), prep.setores(), prep.versoesCtrl(),
                anterior.versao(), evento);
        publicarAposCommit(evento);
        return resposta(nova, principal);
    }

    /** POST cancelamento: exige {@code confirmado=true} (RN12) e grava nova versão cancelada (Req. 12.6, 12.8). */
    public ReservaResposta cancelar(long id, CancelamentoRequisicao req, Principal principal) {
        exigirAutenticado(principal);
        Reserva anterior = carregar(id);
        exigirPodeAlterar(anterior, principal);
        // O ContextoValidacao do cancelamento só precisa de relógio e Configuração
        ContextoValidacao ctx = new ContextoValidacao(relogio, catalogo.configuracao(), null, null,
                null, null, anterior);
        List<Violacao> violacoes = new ArrayList<>();
        if (anterior.cancelada()) {
            violacoes.add(Violacao.de(CodigosRegra.RN12_RESERVA_NAO_EDITAVEL,
                    "RN12: esta reserva já está cancelada."));
        }
        violacoes.addAll(validador.validarCancelamento(anterior, req != null && req.foiConfirmado(), ctx));
        lancarSeHouver(violacoes);

        // Releitura dos controles para manter a condição otimista (RN7) também no cancelamento
        Map<Long, Ambiente> ambientes = mapaAmbientes();
        Long raizId = raizDe(anterior, ambientes);
        Map<Long, Recurso> recursos = recursosDe(anterior);
        Map<Long, Long> versoesCtrl = lerVersoesControle(raizId, recursos);

        LocalDateTime agora = agora();
        Reserva cancelada = copiar(anterior, id, anterior.unidade(), anterior.solicitante(), anterior.periodos(),
                true, agora, agora, anterior.versao() + 1);
        EventoOutbox evento = evento(EVENTO_CANCELADA, cancelada);
        reservas.gravar(cancelada, criadoEm(id, agora), raizId, setoresEnvolvidos(anterior, recursos.keySet()),
                versoesCtrl, anterior.versao(), evento);
        publicarAposCommit(evento);
        return resposta(cancelada, principal);
    }

    /** POST verificar-periodo: conflitos RN5/RN6 de um Período candidato (Req. 10.5), sem dados pessoais. */
    public VerificarPeriodoResposta verificarPeriodo(VerificarPeriodoRequisicao req, Principal principal) {
        exigirAutenticado(principal);
        ValidadorEntrada.validar(req);
        long ambienteId = Long.parseLong(req.ambienteId());
        Long reservaId = req.reservaId() == null ? null : Long.valueOf(req.reservaId());
        Map<Long, Ambiente> ambientes = mapaAmbientes();
        Ambiente ambiente = ambientes.get(ambienteId);
        if (ambiente == null) {
            throw new ExcecaoValidacao(List.of(new Violacao(AMBIENTE_INDISPONIVEL,
                    "O ambiente informado não existe. Escolha um ambiente cadastrado.", "ambienteId")));
        }
        Periodo periodo = ConversorReserva.paraPeriodo(req.periodo());
        if (!periodo.valido()) {
            // RN1: sem período válido não há conflito a verificar
            return new VerificarPeriodoResposta(List.of());
        }
        ArvoreAmbientes arvore = new ArvoreAmbientes(ambientes.values());
        long raizId = arvore.raiz(ambienteId).id();
        List<PeriodoOcupado> ocupados =
                reservas.periodosOcupadosPorRaiz(raizId, periodo.inicio(), periodo.termino());
        Reserva candidata = new Reserva(reservaId, ambiente.unidade(), principal.sub(), ambienteId, null,
                null, null, null, List.of(periodo), List.of(), false, null, null, 0L);
        List<VerificarPeriodoResposta.ConflitoDto> conflitos = new ArrayList<>();
        for (Conflito c : detector.conflitos(candidata, ocupados, arvore)) {
            String regra = CodigosRegra.RN5_CONFLITO_HORARIO.equals(c.codigo())
                    ? "RN5: o ambiente já está reservado neste período"
                    : "RN6: um ambiente relacionado (pai ou filho) já está reservado neste período";
            conflitos.add(new VerificarPeriodoResposta.ConflitoDto(c.codigo(),
                    regra + " (reserva " + c.reservaConflitanteId() + "). Escolha outro horário ou ambiente.",
                    String.valueOf(c.reservaConflitanteId())));
        }
        return new VerificarPeriodoResposta(conflitos);
    }

    /** GET versoes: histórico com diferenças entre versões consecutivas (dono ou Administrador). */
    public List<VersaoResposta> versoes(long id, Principal principal) {
        exigirAutenticado(principal);
        Reserva atual = carregar(id);
        exigirPodeAlterar(atual, principal);
        List<VersaoReserva> lista = reservas.versoes(id);
        List<VersaoResposta> resultado = new ArrayList<>(lista.size());
        VersaoReserva anterior = null;
        for (VersaoReserva v : lista) {
            resultado.add(new VersaoResposta(v.numero(), v.reserva().alteradaEm(),
                    resposta(v.reserva(), principal), ComparadorVersoes.comparar(anterior, v)));
            anterior = v;
        }
        return resultado;
    }

    // ---------- Montagem do contexto ----------

    /** Resultado da preparação: contexto de validação e dados para a gravação. */
    private record Preparacao(ContextoValidacao contexto, Long raizId, Set<Long> setores,
                              Map<Long, Long> versoesCtrl, List<Violacao> violacoesEntrada) {
    }

    /**
     * Lê catálogo, versões de CTRL (ANTES das consultas de ocupação, para a condição otimista RN7
     * cobrir o que foi lido) e as ocupações na janela dos Períodos.
     */
    private Preparacao preparar(Reserva r, Reserva anterior) {
        List<Violacao> entrada = new ArrayList<>();
        Map<Long, Ambiente> ambientes = mapaAmbientes();
        Long raizId = null;
        if (!r.localProprio()) {
            Ambiente amb = ambientes.get(r.ambienteId());
            if (amb == null || !amb.ativo() || !amb.unidade().equals(r.unidade())) {
                entrada.add(new Violacao(AMBIENTE_INDISPONIVEL,
                        "O ambiente informado não está disponível para a sua unidade. Escolha outro ambiente.",
                        "ambienteId"));
            } else {
                raizId = new ArvoreAmbientes(ambientes.values()).raiz(amb.id()).id();
            }
        }
        Map<Long, Recurso> recursos = recursosDe(r);
        // 1) Versões dos controles antes de qualquer leitura de ocupação
        Map<Long, Long> versoesCtrl = lerVersoesControle(raizId, recursos);

        // 2) Ocupações na janela [menor instante, maior instante] dos Períodos
        List<PeriodoOcupado> ocupados = new ArrayList<>();
        List<SolicitacaoOcupada> solicitacoes = new ArrayList<>();
        Optional<LocalDateTime[]> janela = janela(r.periodos());
        if (janela.isPresent()) {
            LocalDateTime ini = janela.get()[0];
            LocalDateTime fim = janela.get()[1];
            if (raizId != null) {
                ocupados.addAll(reservas.periodosOcupadosPorRaiz(raizId, ini, fim));
            }
            for (Recurso rec : recursos.values()) {
                if (rec.limitado()) {
                    solicitacoes.addAll(reservas.solicitacoesOcupadasPorRecurso(rec.id(), ini, fim));
                }
            }
        }
        ContextoValidacao ctx = new ContextoValidacao(relogio, catalogo.configuracao(), ambientes, recursos,
                ocupados, solicitacoes, anterior);
        return new Preparacao(ctx, raizId, setoresEnvolvidos(r, recursos.keySet()), versoesCtrl, entrada);
    }

    private Map<Long, Long> lerVersoesControle(Long raizId, Map<Long, Recurso> recursos) {
        Map<Long, Long> versoes = new HashMap<>();
        if (raizId != null) {
            versoes.put(GravadorReservas.chaveControleAmbiente(raizId),
                    reservas.lerVersaoControle(Chaves.pkControleAmbiente(raizId)));
        }
        for (Recurso rec : recursos.values()) {
            if (rec.limitado()) {
                versoes.put(rec.id(), reservas.lerVersaoControle(Chaves.pkControleRecurso(rec.id())));
            }
        }
        return versoes;
    }

    /** Menor e maior instante entre inícios e términos (cobre períodos inválidos sem erro). */
    private static Optional<LocalDateTime[]> janela(List<Periodo> periodos) {
        LocalDateTime ini = null;
        LocalDateTime fim = null;
        for (Periodo p : periodos) {
            for (LocalDateTime t : List.of(p.inicio(), p.termino())) {
                ini = ini == null || t.isBefore(ini) ? t : ini;
                fim = fim == null || t.isAfter(fim) ? t : fim;
            }
        }
        return ini == null ? Optional.empty() : Optional.of(new LocalDateTime[] {ini, fim});
    }

    private Map<Long, Ambiente> mapaAmbientes() {
        Map<Long, Ambiente> mapa = new HashMap<>();
        for (Ambiente a : catalogo.ambientes()) {
            mapa.put(a.id(), a);
        }
        return mapa;
    }

    private static Long raizDe(Reserva r, Map<Long, Ambiente> ambientes) {
        if (r.localProprio() || !ambientes.containsKey(r.ambienteId())) {
            return null;
        }
        return new ArvoreAmbientes(ambientes.values()).raiz(r.ambienteId()).id();
    }

    /** Recursos existentes no catálogo para as Solicitações (ausentes viram RN9 no domínio). */
    private Map<Long, Recurso> recursosDe(Reserva r) {
        Map<Long, Recurso> recursos = new HashMap<>();
        for (Solicitacao s : r.solicitacoes()) {
            catalogo.recurso(s.recursoId()).ifPresent(rec -> recursos.put(rec.id(), rec));
        }
        return recursos;
    }

    /** ENVO_IDs do Ambiente e dos Recursos solicitados (cópias GSI2 e Atendente envolvido). */
    private Set<Long> setoresEnvolvidos(Reserva r, Set<Long> recursoIds) {
        Set<Long> setores = new LinkedHashSet<>();
        if (!r.localProprio()) {
            setores.addAll(catalogo.setoresDoAmbiente(r.ambienteId()));
        }
        for (Long recursoId : recursoIds) {
            setores.addAll(catalogo.setoresDoRecurso(recursoId));
        }
        return setores;
    }

    // ---------- Ids e versões ----------

    /**
     * Preserva o PRES_ID quando o frontend não o reenvia: período sem id com o mesmo início e término
     * de um período anterior reaproveita o id (RN4 em andamento compara pelo id). Ids que não
     * pertencem à Reserva são descartados.
     */
    private static List<Periodo> reconciliarPeriodos(List<Periodo> novos, List<Periodo> antigos) {
        Set<Long> idsAntigos = new LinkedHashSet<>();
        antigos.forEach(p -> idsAntigos.add(p.id()));
        Set<Long> usados = new LinkedHashSet<>();
        List<Periodo> resultado = new ArrayList<>(novos.size());
        for (Periodo p : novos) {
            Long id = p.id() != null && idsAntigos.contains(p.id()) && !usados.contains(p.id()) ? p.id() : null;
            if (id == null) {
                for (Periodo a : antigos) {
                    if (!usados.contains(a.id()) && a.inicio().equals(p.inicio()) && a.termino().equals(p.termino())) {
                        id = a.id();
                        break;
                    }
                }
            }
            if (id != null) {
                usados.add(id);
            }
            resultado.add(new Periodo(id, p.inicio(), p.termino()));
        }
        return resultado;
    }

    /** Atribui PRES_ID (contador SEQ#PRES) aos Períodos novos. */
    private List<Periodo> atribuirIdsPeriodos(List<Periodo> periodos) {
        int novos = (int) periodos.stream().filter(p -> p.id() == null).count();
        if (novos == 0) {
            return periodos;
        }
        long proximo = reservas.reservarIds(PortaReservas.SEQUENCIA_PERIODO, novos);
        List<Periodo> resultado = new ArrayList<>(periodos.size());
        for (Periodo p : periodos) {
            resultado.add(p.id() != null ? p : new Periodo(proximo++, p.inicio(), p.termino()));
        }
        return resultado;
    }

    private LocalDateTime criadoEm(long id, LocalDateTime padrao) {
        return reservas.criadoEm(id).orElse(padrao);
    }

    /**
     * Publica o evento já gravado e remove o item OUTBOX (Req. 2.8). Falhas não desfazem a operação:
     * a resposta continua de sucesso e o evento fica no outbox para o {@link RepublicadorOutbox}.
     */
    private void publicarAposCommit(EventoOutbox evento) {
        try {
            publicador.publicar(evento);
        } catch (RuntimeException e) {
            log.erro(evento.eventoId(), "Falha ao publicar Evento_Reserva; mantido no outbox", e);
            return;
        }
        try {
            reservas.removerOutbox(evento);
        } catch (RuntimeException e) {
            // Já publicado: o republicador reenviará (consumidores são idempotentes por eventoId)
            log.erro(evento.eventoId(), "Falha ao remover Evento_Reserva do outbox", e);
        }
    }

    private EventoOutbox evento(String tipo, Reserva r) {
        return new EventoOutbox(UUID.randomUUID().toString(), tipo, r.id(), r.versao(), relogio.instant());
    }

    private static Reserva copiar(Reserva base, Long id, String unidade, String solicitante, List<Periodo> periodos,
                                  boolean cancelada, LocalDateTime canceladaEm, LocalDateTime alteradaEm,
                                  long versao) {
        return new Reserva(id, unidade, solicitante, base.ambienteId(), base.complemento(), base.disposicaoId(),
                base.finalidade(), base.participantes(), periodos, base.solicitacoes(), cancelada, canceladaEm,
                alteradaEm, versao);
    }

    // ---------- Autorização (simplificada até a tarefa 15.2) ----------

    private static boolean ehAdministrador(Principal p) {
        return p.pertenceA(GRUPO_ADMINISTRADOR);
    }

    private static boolean dono(Reserva r, Principal p) {
        return r.solicitante() != null && r.solicitante().equals(p.sub());
    }

    private static boolean adminDaUnidade(Reserva r, Principal p) {
        return ehAdministrador(p) && !vazio(p.unidade()) && p.unidade().equals(r.unidade());
    }

    private boolean atendenteEnvolvido(Reserva r, Principal p) {
        if (!p.pertenceA(GRUPO_ATENDENTE) || vazio(p.setor())) {
            return false;
        }
        Long setor;
        try {
            setor = Long.valueOf(p.setor().trim());
        } catch (NumberFormatException e) {
            return false;
        }
        Set<Long> recursoIds = new LinkedHashSet<>();
        r.solicitacoes().forEach(s -> recursoIds.add(s.recursoId()));
        return setoresEnvolvidos(r, recursoIds).contains(setor);
    }

    private boolean podeVer(Reserva r, Principal p) {
        return dono(r, p) || adminDaUnidade(r, p) || atendenteEnvolvido(r, p);
    }

    private static void exigirPodeAlterar(Reserva r, Principal p) {
        if (!dono(r, p) && !adminDaUnidade(r, p)) {
            throw new ExcecaoAcessoNegado("Você só pode alterar ou cancelar as suas próprias reservas.");
        }
    }

    private static void exigirAutenticado(Principal p) {
        Objects.requireNonNull(p, "principal é obrigatório");
    }

    // ---------- Resposta ----------

    /**
     * Converte a Reserva na resposta. O nome do Solicitante só é incluído para quem pode vê-lo
     * (dono, Administrador da unidade ou Setor_Atendente envolvido, Req. 6.17).
     */
    private ReservaResposta resposta(Reserva r, Principal leitor) {
        String nome = podeVer(r, leitor)
                ? resolvedorNome.nome(r.solicitante(), leitor).orElse(null)
                : null;
        return ConversorReserva.paraResposta(r, calculadoraStatus.status(r), nome, List.of());
    }

    private Reserva carregar(long id) {
        return reservas.obter(id).orElseThrow(() -> new ExcecaoNaoEncontrado("Reserva não encontrada."));
    }

    private LocalDateTime agora() {
        return LocalDateTime.ofInstant(relogio.instant(), ConstantesDominio.ZONA);
    }

    private static void lancarSeHouver(List<Violacao> violacoes) {
        if (!violacoes.isEmpty()) {
            throw new ExcecaoValidacao(violacoes);
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
