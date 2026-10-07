package br.mp.mpf.sisgares.seed;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.UnidadeMacro;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.comumaws.repositorio.Perfil;
import br.mp.mpf.sisgares.comumaws.repositorio.Usuario;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.ArvoreAmbientes;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.DetectorConflito;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Recurso;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.ResolvedorIcone;
import br.mp.mpf.sisgares.dominio.ResultadoIcone;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.csv.LeitorCsv;
import br.mp.mpf.sisgares.dominio.csv.LinhaAmbi;
import br.mp.mpf.sisgares.dominio.csv.LinhaDisp;
import br.mp.mpf.sisgares.dominio.csv.LinhaEamb;
import br.mp.mpf.sisgares.dominio.csv.LinhaEnvo;
import br.mp.mpf.sisgares.dominio.csv.LinhaErec;
import br.mp.mpf.sisgares.dominio.csv.LinhaGrec;
import br.mp.mpf.sisgares.dominio.csv.LinhaPres;
import br.mp.mpf.sisgares.dominio.csv.LinhaRecu;
import br.mp.mpf.sisgares.dominio.csv.LinhaSoli;
import br.mp.mpf.sisgares.dominio.csv.LinhaVrec;
import br.mp.mpf.sisgares.dominio.csv.MapeadorAmbi;
import br.mp.mpf.sisgares.dominio.csv.MapeadorDisp;
import br.mp.mpf.sisgares.dominio.csv.MapeadorEamb;
import br.mp.mpf.sisgares.dominio.csv.MapeadorEnvo;
import br.mp.mpf.sisgares.dominio.csv.MapeadorErec;
import br.mp.mpf.sisgares.dominio.csv.MapeadorGrec;
import br.mp.mpf.sisgares.dominio.csv.MapeadorLinha;
import br.mp.mpf.sisgares.dominio.csv.MapeadorPres;
import br.mp.mpf.sisgares.dominio.csv.MapeadorRecu;
import br.mp.mpf.sisgares.dominio.csv.MapeadorSoli;
import br.mp.mpf.sisgares.dominio.csv.MapeadorVrec;
import br.mp.mpf.sisgares.dominio.csv.Rejeicao;
import br.mp.mpf.sisgares.dominio.csv.ResultadoLeitura;
import br.mp.mpf.sisgares.seed.RelatorioCarga.Ocorrencia;
import br.mp.mpf.sisgares.seed.RelatorioCarga.RelatorioArquivo;
import br.mp.mpf.sisgares.seed.ReservaSeed.SolicitacaoSeed;
import java.io.StringReader;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Importador_Seed (Requisito 4): lógica pura da carga, sem conhecer S3.
 *
 * <p>Recebe o conteúdo dos CSVs (nome do arquivo → texto), a lista de Imagens_Ícone e o texto de
 * {@code descricoes.md}; grava por meio de {@link DestinoSeed} e devolve o {@link RelatorioCarga}.
 * Tudo é determinístico (ordem por ID), de modo que duas execuções gravam os mesmos itens com as
 * mesmas chaves (Requisito 4.12).
 */
public final class ImportadorSeed {

    // ---------- Arquivos ----------
    public static final String ARQ_AMBI = "dados-ambiente.csv";
    public static final String ARQ_DISP = "dados-disposicao.csv";
    public static final String ARQ_EAMB = "dados-envolvido-ambiente.csv";
    public static final String ARQ_EREC = "dados-envolvido-recurso.csv";
    public static final String ARQ_ENVO = "dados-envolvido.csv";
    public static final String ARQ_GREC = "dados-grupo-recurso.csv";
    public static final String ARQ_PRES = "dados-periodo-reserva.csv";
    public static final String ARQ_RECU = "dados-recurso.csv";
    public static final String ARQ_SOLI = "dados-solicitacao.csv";
    public static final String ARQ_VREC = "dados-vinculo-recurso.csv";

    /** Ordem de apresentação no relatório. */
    static final List<String> ARQUIVOS = List.of(ARQ_GREC, ARQ_DISP, ARQ_AMBI, ARQ_ENVO, ARQ_RECU,
            ARQ_EAMB, ARQ_EREC, ARQ_VREC, ARQ_PRES, ARQ_SOLI);

    // ---------- Valores fixos do seed ----------
    public static final String UNIDADE = "PR/CE";
    static final String NOME_UNIDADE = "Procuradoria da República no Ceará";
    static final int ANTECEDENCIA_MINUTOS = 120;
    static final LocalTime FAIXA_INICIO = LocalTime.of(7, 0);
    static final LocalTime FAIXA_FIM = LocalTime.of(20, 0);
    static final String FINALIDADE = "Reserva importada (seed)";
    static final int PARTICIPANTES = 10;
    static final String COMPLEMENTO_LOCAL_PROPRIO = "Local próprio (seed)";
    /** Versão inicial das reservas importadas. */
    static final long VERSAO_INICIAL = 1;

    /** Solicitantes fictícios (sub do Cognito), entre os quais as reservas são distribuídas (Req. 4.7). */
    static final List<String> SOLICITANTES =
            List.of("seed-solicitante-01", "seed-solicitante-02", "seed-solicitante-03");
    static final String ADMINISTRADOR = "seed-administrador-01";
    static final String PREFIXO_ATENDENTE = "seed-atendente-";
    /** Prefixo do código de serviço fictício do SNP atribuído aos EREC de ID ímpar (Req. 4.9). */
    static final String PREFIXO_COD_SNP = "SNP-SEED-";

    // ---------- Motivos do relatório ----------
    public static final String ID_INEXISTENTE = "ID_INEXISTENTE";
    public static final String ARQUIVO_AUSENTE = "ARQUIVO_AUSENTE";
    public static final String PERIODO_INVALIDO = "PERIODO_INVALIDO";

    private static final String SIM = "S";

    private final DestinoSeed destino;
    private final String snpUrl;
    private final ResolvedorIcone resolvedorIcone = new ResolvedorIcone();
    private final DetectorConflito detector = new DetectorConflito();

    /**
     * @param destino destino idempotente da gravação
     * @param snpUrl  endpoint do SNP simulado gravado na Configuração padrão (Req. 4.8)
     */
    public ImportadorSeed(DestinoSeed destino, String snpUrl) {
        this.destino = Objects.requireNonNull(destino, "destino é obrigatório");
        this.snpUrl = snpUrl;
    }

    /**
     * Executa a carga completa.
     *
     * @param csvs          nome do arquivo CSV (sem pasta) → conteúdo
     * @param imagens       chaves das Imagens_Ícone, com a pasta da categoria
     *                      (ex.: {@code icones-recurso/equip_005_projetor-multimidia.png})
     * @param descricoesMd  conteúdo de {@code imagens/descricoes.md} (pode ser nulo)
     */
    public RelatorioCarga importar(Map<String, String> csvs, List<String> imagens, String descricoesMd) {
        Objects.requireNonNull(csvs, "csvs é obrigatório");
        Map<String, Acumulador> acumuladores = new LinkedHashMap<>();
        for (String arquivo : ARQUIVOS) {
            acumuladores.put(arquivo, new Acumulador());
        }

        // Unidade_Macro e Configuração padrão (Req. 4.6 e 4.8)
        destino.salvarUnidade(new UnidadeMacro(UNIDADE, NOME_UNIDADE));
        destino.salvarConfiguracao(new Configuracao(ANTECEDENCIA_MINUTOS,
                new Configuracao.FaixaHoraria(FAIXA_INICIO, FAIXA_FIM), Map.of()), snpUrl);

        // Imagens e textos alternativos (Req. 4.14 e 4.17)
        Map<String, String> alts = LeitorDescricoes.ler(descricoesMd);
        Set<String> imagensDisp = new TreeSet<>();
        Set<String> imagensRecu = new TreeSet<>();
        int comAlt = 0;
        List<String> semAlt = new ArrayList<>();
        for (String chave : imagens == null ? List.<String>of() : new TreeSet<>(imagens)) {
            String nome = nomeArquivo(chave);
            String pasta = pasta(chave);
            if (nome.isEmpty() || nome.endsWith(".md")) {
                continue;
            }
            if (pasta.contains("disposicao")) {
                imagensDisp.add(nome);
            } else if (pasta.contains("recurso")) {
                imagensRecu.add(nome);
            } else {
                continue;
            }
            String alt = alts.get(nome);
            if (alt == null) {
                semAlt.add(nome);
            } else {
                destino.salvarTextoAlternativo(nome, pasta, alt);
                comAlt++;
            }
        }

        // ---------- GREC ----------
        Lidos<LinhaGrec> grec = ler(csvs, ARQ_GREC, new MapeadorGrec(), acumuladores);
        Set<Long> grupos = new HashSet<>();
        for (Lido<LinhaGrec> l : grec.lidos()) {
            LinhaGrec g = l.registro();
            destino.salvarGrupo(new GrupoRecurso(g.grecId(), g.grecDesc(), g.grecOrdem(), SIM.equals(g.grecStAtivo())));
            grupos.add(g.grecId());
            grec.acumulador().gravadas++;
        }

        // ---------- DISP (ícone resolvido + alt) ----------
        Lidos<LinhaDisp> disp = ler(csvs, ARQ_DISP, new MapeadorDisp(), acumuladores);
        for (Lido<LinhaDisp> l : disp.lidos()) {
            LinhaDisp d = l.registro();
            ResultadoIcone icone = resolverIcone(d.dispIconeArquivo(), ResolvedorIcone.Categoria.DISPOSICAO,
                    imagensDisp, l.linha(), disp.acumulador());
            destino.salvarDisposicao(new Disposicao(d.dispId(), d.dispDesc(), SIM.equals(d.dispStAtivo()),
                    icone.nomeResolvido(), icone.referenciaOriginal(), alts.get(icone.nomeResolvido())));
            disp.acumulador().gravadas++;
        }

        // ---------- AMBI (pai precisa existir; remoção em cascata de filhos órfãos) ----------
        Lidos<LinhaAmbi> ambi = ler(csvs, ARQ_AMBI, new MapeadorAmbi(), acumuladores);
        Map<Long, Lido<LinhaAmbi>> ambientesLidos = new TreeMap<>();
        for (Lido<LinhaAmbi> l : ambi.lidos()) {
            if (ambientesLidos.containsKey(l.registro().ambiId())) {
                ambi.acumulador().rejeitar(l.linha(), Rejeicao.FORMATO_INVALIDO, "AMBI_ID repetido");
            } else {
                ambientesLidos.put(l.registro().ambiId(), l);
            }
        }
        boolean removeu = true;
        while (removeu) {
            removeu = false;
            for (var it = ambientesLidos.values().iterator(); it.hasNext(); ) {
                Lido<LinhaAmbi> l = it.next();
                Long pai = l.registro().ambiIdPai();
                if (pai != null && (pai == l.registro().ambiId() || !ambientesLidos.containsKey(pai))) {
                    ambi.acumulador().rejeitar(l.linha(), ID_INEXISTENTE, "AMBI_ID_PAI inexistente");
                    it.remove();
                    removeu = true;
                }
            }
        }
        Map<Long, Ambiente> ambientes = new TreeMap<>();
        ambientesLidos.forEach((id, l) -> ambientes.put(id, new Ambiente(id, l.registro().ambiDesc(),
                SIM.equals(l.registro().ambiStAtivo()), l.registro().ambiIdPai(), UNIDADE)));
        // Ciclos (A→B→A) não têm raiz: rejeita os ambientes envolvidos
        for (var it = ambientes.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!temRaiz(e.getValue(), ambientes)) {
                ambi.acumulador().rejeitar(ambientesLidos.get(e.getKey()).linha(), ID_INEXISTENTE,
                        "AMBI_ID_PAI forma ciclo sem raiz");
                it.remove();
            }
        }
        ArvoreAmbientes arvore = new ArvoreAmbientes(ambientes.values());
        for (Ambiente a : ambientes.values()) {
            destino.salvarAmbiente(new RegistroAmbiente(a, arvore.raiz(a.id()).id()));
            ambi.acumulador().gravadas++;
        }

        // ---------- ENVO e usuários fictícios ----------
        Lidos<LinhaEnvo> envo = ler(csvs, ARQ_ENVO, new MapeadorEnvo(), acumuladores);
        Set<Long> setores = new TreeSet<>();
        for (Lido<LinhaEnvo> l : envo.lidos()) {
            LinhaEnvo e = l.registro();
            destino.salvarSetor(new Setor(e.envoId(), e.envoDesc(), e.envoEmail(), SIM.equals(e.envoStAtivo()),
                    UNIDADE, List.of()));
            setores.add(e.envoId());
            envo.acumulador().gravadas++;
        }
        int usuarios = criarUsuarios(setores);

        // ---------- RECU (sem Unidade_Macro, ícone resolvido) ----------
        Lidos<LinhaRecu> recu = ler(csvs, ARQ_RECU, new MapeadorRecu(), acumuladores);
        Map<Long, Recurso> recursos = new TreeMap<>();
        for (Lido<LinhaRecu> l : recu.lidos()) {
            LinhaRecu r = l.registro();
            if (!grupos.contains(r.recuGrecId())) {
                recu.acumulador().rejeitar(l.linha(), ID_INEXISTENTE, "RECU_GREC_ID inexistente");
                continue;
            }
            ResultadoIcone icone = resolverIcone(r.recuIconeArquivo(), ResolvedorIcone.Categoria.RECURSO,
                    imagensRecu, l.linha(), recu.acumulador());
            // Unidade ausente: o Recurso atende todas as unidades (Req. 4.6)
            Recurso recurso = new Recurso(r.recuId(), r.recuDesc(), SIM.equals(r.recuStAtivo()),
                    SIM.equals(r.recuStLimitado()), r.recuDisponibilidade(), null, Set.of());
            destino.salvarRecurso(new RegistroRecurso(recurso, r.recuGrecId(), icone.nomeResolvido(),
                    icone.referenciaOriginal()));
            recursos.put(recurso.id(), recurso);
            recu.acumulador().gravadas++;
        }

        // ---------- EAMB ----------
        Lidos<LinhaEamb> eamb = ler(csvs, ARQ_EAMB, new MapeadorEamb(), acumuladores);
        Map<Long, Set<Long>> setoresPorAmbiente = new HashMap<>();
        for (Lido<LinhaEamb> l : eamb.lidos()) {
            LinhaEamb v = l.registro();
            String faltante = !setores.contains(v.eambEnvoId()) ? "EAMB_ENVO_ID inexistente"
                    : !ambientes.containsKey(v.eambAmbiId()) ? "EAMB_AMBI_ID inexistente" : null;
            if (faltante != null) {
                eamb.acumulador().rejeitar(l.linha(), ID_INEXISTENTE, faltante);
                continue;
            }
            destino.vincularSetorAmbiente(new VinculoSetor(v.eambId(), v.eambEnvoId(), v.eambAmbiId(), null));
            setoresPorAmbiente.computeIfAbsent(v.eambAmbiId(), k -> new TreeSet<>()).add(v.eambEnvoId());
            eamb.acumulador().gravadas++;
        }

        // ---------- EREC (código SNP fictício nos IDs ímpares; pares sem código - RN11) ----------
        Lidos<LinhaErec> erec = ler(csvs, ARQ_EREC, new MapeadorErec(), acumuladores);
        Map<Long, Set<Long>> setoresPorRecurso = new HashMap<>();
        for (Lido<LinhaErec> l : erec.lidos()) {
            LinhaErec v = l.registro();
            String faltante = !setores.contains(v.erecEnvoId()) ? "EREC_ENVO_ID inexistente"
                    : !recursos.containsKey(v.erecRecuId()) ? "EREC_RECU_ID inexistente" : null;
            if (faltante != null) {
                erec.acumulador().rejeitar(l.linha(), ID_INEXISTENTE, faltante);
                continue;
            }
            String codigo = v.erecId() % 2 == 1 ? PREFIXO_COD_SNP + String.format("%03d", v.erecId()) : null;
            destino.vincularSetorRecurso(new VinculoSetor(v.erecId(), v.erecEnvoId(), v.erecRecuId(), codigo));
            setoresPorRecurso.computeIfAbsent(v.erecRecuId(), k -> new TreeSet<>()).add(v.erecEnvoId());
            erec.acumulador().gravadas++;
        }

        // ---------- VREC ----------
        Lidos<LinhaVrec> vrec = ler(csvs, ARQ_VREC, new MapeadorVrec(), acumuladores);
        for (Lido<LinhaVrec> l : vrec.lidos()) {
            LinhaVrec v = l.registro();
            String faltante = !recursos.containsKey(v.vrecRecuId()) ? "VREC_RECU_ID inexistente"
                    : !ambientes.containsKey(v.vrecAmbiId()) ? "VREC_AMBI_ID inexistente" : null;
            if (faltante != null) {
                vrec.acumulador().rejeitar(l.linha(), ID_INEXISTENTE, faltante);
                continue;
            }
            destino.vincularRecursoAmbiente(new VinculoRecursoAmbiente(v.vrecId(), v.vrecRecuId(), v.vrecAmbiId()));
            vrec.acumulador().gravadas++;
        }

        // ---------- PRES e SOLI agrupados por RESE_ID ----------
        Map<Long, List<Periodo>> periodosPorReserva = new TreeMap<>();
        Map<Long, List<SolicitacaoSeed>> solicitacoesPorReserva = new TreeMap<>();
        Lidos<LinhaPres> pres = ler(csvs, ARQ_PRES, new MapeadorPres(), acumuladores);
        Set<Long> presIds = new HashSet<>();
        for (Lido<LinhaPres> l : pres.lidos()) {
            LinhaPres p = l.registro();
            Periodo periodo = new Periodo(p.presId(), p.presDthrInicio(), p.presDthrTermino());
            if (!presIds.add(p.presId())) {
                pres.acumulador().rejeitar(l.linha(), Rejeicao.FORMATO_INVALIDO, "PRES_ID repetido");
            } else if (!periodo.valido()) {
                pres.acumulador().rejeitar(l.linha(), PERIODO_INVALIDO, "término deve ser posterior ao início");
            } else {
                periodosPorReserva.computeIfAbsent(p.presReseId(), k -> new ArrayList<>()).add(periodo);
            }
        }
        Lidos<LinhaSoli> soli = ler(csvs, ARQ_SOLI, new MapeadorSoli(), acumuladores);
        Set<Long> soliIds = new HashSet<>();
        for (Lido<LinhaSoli> l : soli.lidos()) {
            LinhaSoli s = l.registro();
            Recurso recurso = recursos.get(s.soliRecuId());
            if (recurso == null) {
                soli.acumulador().rejeitar(l.linha(), ID_INEXISTENTE, "SOLI_RECU_ID inexistente");
                continue;
            }
            if (!soliIds.add(s.soliId())) {
                soli.acumulador().rejeitar(l.linha(), Rejeicao.FORMATO_INVALIDO, "SOLI_ID repetido");
                continue;
            }
            // SOLI_QTD vazio: 1 para Recurso_Limitado, ausente para não limitado (Req. 4.5)
            Integer quantidade = s.soliQtd() != null ? s.soliQtd() : (recurso.limitado() ? Integer.valueOf(1) : null);
            solicitacoesPorReserva.computeIfAbsent(s.soliReseId(), k -> new ArrayList<>())
                    .add(new SolicitacaoSeed(s.soliId(), s.soliRecuId(), quantidade));
        }

        // ---------- Reservas determinísticas (Req. 4.7) ----------
        Set<Long> reservaIds = new TreeSet<>(periodosPorReserva.keySet());
        reservaIds.addAll(solicitacoesPorReserva.keySet());
        List<Long> ambientesAtivos = ambientes.values().stream().filter(Ambiente::ativo)
                .map(Ambiente::id).sorted().toList();
        List<PeriodoOcupado> ocupados = new ArrayList<>();
        int indice = 0;
        int localProprio = 0;
        for (long reseId : reservaIds) {
            List<Periodo> periodos = new ArrayList<>(periodosPorReserva.getOrDefault(reseId, List.of()));
            periodos.sort(Comparator.comparing(Periodo::inicio).thenComparing(Periodo::id));
            List<SolicitacaoSeed> sols = new ArrayList<>(solicitacoesPorReserva.getOrDefault(reseId, List.of()));
            sols.sort(Comparator.comparingLong(SolicitacaoSeed::soliId));
            // A quantidade ausente (não limitado) usa 0 no domínio; o destino não grava SOLI_QTD nesse caso
            List<Solicitacao> solicitacoesDominio = sols.stream()
                    .map(s -> new Solicitacao(s.recursoId(), s.quantidade() == null ? 0 : s.quantidade()))
                    .toList();
            String solicitante = SOLICITANTES.get(indice % SOLICITANTES.size());
            indice++;

            Long ambienteEscolhido = escolherAmbiente(reseId, periodos, ambientesAtivos, ocupados, arvore);
            Reserva reserva = new Reserva(reseId, UNIDADE, solicitante, ambienteEscolhido,
                    ambienteEscolhido == null ? COMPLEMENTO_LOCAL_PROPRIO : null, null, FINALIDADE, PARTICIPANTES,
                    periodos, solicitacoesDominio, false, null, null, VERSAO_INICIAL);
            Set<Long> envolvidos = new TreeSet<>();
            Long raizId = null;
            if (ambienteEscolhido != null) {
                raizId = arvore.raiz(ambienteEscolhido).id();
                envolvidos.addAll(setoresPorAmbiente.getOrDefault(ambienteEscolhido, Set.of()));
                for (Periodo p : periodos) {
                    ocupados.add(new PeriodoOcupado(reseId, ambienteEscolhido, p));
                }
            } else {
                localProprio++;
            }
            for (SolicitacaoSeed s : sols) {
                envolvidos.addAll(setoresPorRecurso.getOrDefault(s.recursoId(), Set.of()));
            }
            destino.salvarReserva(new ReservaSeed(reserva, raizId, envolvidos, sols));
            pres.acumulador().gravadas += periodos.size();
            soli.acumulador().gravadas += sols.size();
        }

        List<RelatorioArquivo> arquivos = new ArrayList<>();
        acumuladores.forEach((nome, a) -> arquivos.add(a.relatorio(nome)));
        return new RelatorioCarga(UNIDADE, arquivos, usuarios, reservaIds.size(), localProprio, comAlt, semAlt);
    }

    /**
     * Primeiro Ambiente ativo sem conflito (RN5/RN6), em rodízio a partir de {@code RESE_ID mod n};
     * {@code null} (Local_Proprio) quando nenhum estiver livre.
     */
    private Long escolherAmbiente(long reseId, List<Periodo> periodos, List<Long> ativos,
                                  List<PeriodoOcupado> ocupados, ArvoreAmbientes arvore) {
        if (ativos.isEmpty()) {
            return null;
        }
        int inicio = (int) Math.floorMod(reseId, (long) ativos.size());
        for (int i = 0; i < ativos.size(); i++) {
            long ambienteId = ativos.get((inicio + i) % ativos.size());
            Reserva candidata = new Reserva(reseId, UNIDADE, null, ambienteId, null, null, null, null,
                    periodos, List.of(), false, null, null, VERSAO_INICIAL);
            if (detector.conflitos(candidata, ocupados, arvore).isEmpty()) {
                return ambienteId;
            }
        }
        return null;
    }

    /** 3 Solicitantes, 1 Administrador e 1 Atendente por Setor_Envolvido, todos em "PR/CE". */
    private int criarUsuarios(Set<Long> setores) {
        int total = 0;
        for (String sub : SOLICITANTES) {
            destino.salvarUsuario(new Usuario(sub, Perfil.SOLICITANTE, UNIDADE, null));
            total++;
        }
        destino.salvarUsuario(new Usuario(ADMINISTRADOR, Perfil.ADMINISTRADOR, UNIDADE, null));
        total++;
        for (long envoId : setores) {
            destino.salvarUsuario(new Usuario(PREFIXO_ATENDENTE + envoId, Perfil.ATENDENTE, UNIDADE, envoId));
            total++;
        }
        return total;
    }

    /** Resolve o ícone e registra alerta {@code ICONE_NAO_ENCONTRADO}/{@code ICONE_AMBIGUO} (Req. 4.16). */
    private ResultadoIcone resolverIcone(String ref, ResolvedorIcone.Categoria categoria, Set<String> arquivos,
                                         int linha, Acumulador acumulador) {
        ResultadoIcone icone = resolvedorIcone.resolver(ref, categoria, arquivos);
        if (!icone.resolvido()) {
            acumulador.alertasIcone.add(new Ocorrencia(linha, icone.motivo().name(),
                    "referência " + (ref == null ? "vazia" : ref) + "; gravado " + icone.nomeResolvido()));
        }
        return icone;
    }

    /** Ambiente cuja cadeia de pais chega a uma raiz (sem ciclo). */
    private static boolean temRaiz(Ambiente a, Map<Long, Ambiente> ambientes) {
        Set<Long> visitados = new LinkedHashSet<>();
        Ambiente atual = a;
        while (atual != null && atual.idPai() != null) {
            if (!visitados.add(atual.id())) {
                return false;
            }
            atual = ambientes.get(atual.idPai());
        }
        return atual != null;
    }

    /** Lê o CSV, registra rejeições e associa a linha física a cada registro aceito. */
    private static <T> Lidos<T> ler(Map<String, String> csvs, String arquivo, MapeadorLinha<T> mapeador,
                                    Map<String, Acumulador> acumuladores) {
        Acumulador acumulador = acumuladores.get(arquivo);
        String texto = csvs.get(arquivo);
        if (texto == null) {
            acumulador.rejeicoes.add(new Ocorrencia(0, ARQUIVO_AUSENTE, "arquivo não encontrado no bucket"));
            return new Lidos<>(List.of(), acumulador);
        }
        ResultadoLeitura<T> resultado = LeitorCsv.ler(new StringReader(texto), mapeador);
        Set<Integer> rejeitadas = new HashSet<>();
        for (Rejeicao r : resultado.rejeicoes()) {
            acumulador.rejeitar(r.linha(), r.motivo(), r.detalhe());
            rejeitadas.add(r.linha());
        }
        List<Integer> linhas = LinhasCsv.linhasAceitas(texto, rejeitadas);
        List<Lido<T>> lidos = new ArrayList<>();
        for (int i = 0; i < resultado.registros().size(); i++) {
            int linha = i < linhas.size() ? linhas.get(i) : 0;
            lidos.add(new Lido<>(resultado.registros().get(i), linha));
        }
        // Lidas = registros aceitos + rejeitados pelo leitor
        acumulador.lidas += resultado.registros().size() + resultado.rejeicoes().size();
        return new Lidos<>(lidos, acumulador);
    }

    private static String nomeArquivo(String chave) {
        int barra = chave.lastIndexOf('/');
        return barra < 0 ? chave : chave.substring(barra + 1);
    }

    private static String pasta(String chave) {
        int barra = chave.lastIndexOf('/');
        if (barra < 0) {
            return "";
        }
        String caminho = chave.substring(0, barra);
        int anterior = caminho.lastIndexOf('/');
        return anterior < 0 ? caminho : caminho.substring(anterior + 1);
    }

    /** Registro aceito com a linha física de origem. */
    private record Lido<T>(T registro, int linha) {
    }

    private record Lidos<T>(List<Lido<T>> lidos, Acumulador acumulador) {
    }

    /** Contadores mutáveis de um arquivo, confinados a uma execução de {@link #importar}. */
    private static final class Acumulador {
        int lidas;
        int gravadas;
        final List<Ocorrencia> rejeicoes = new ArrayList<>();
        final List<Ocorrencia> alertasIcone = new ArrayList<>();

        void rejeitar(int linha, String motivo, String detalhe) {
            rejeicoes.add(new Ocorrencia(linha, motivo, detalhe));
        }

        RelatorioArquivo relatorio(String nome) {
            List<Ocorrencia> ordenadas = new ArrayList<>(rejeicoes);
            ordenadas.sort(Comparator.comparingInt(Ocorrencia::linha));
            return new RelatorioArquivo(nome, lidas, gravadas, ordenadas.size(), ordenadas, alertasIcone);
        }
    }
}
