package br.mp.mpf.sisgares.catalogo;

import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.AmbienteRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.DisposicaoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.GrupoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.RecursoRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.SetorRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.VinculoAmbienteRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Requisicoes.VinculoSetorRequisicao;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.AmbienteResposta;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.DisposicaoResposta;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.GrupoResposta;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.RecursoResposta;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.SetorResposta;
import br.mp.mpf.sisgares.catalogo.dto.Respostas.VinculoSetorResposta;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoNaoEncontrado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.ArvoreAmbientes;
import br.mp.mpf.sisgares.dominio.Recurso;
import br.mp.mpf.sisgares.dominio.Violacao;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Casos de uso do catálogo (Req. 7): consulta, inclusão, alteração e inativação de Setor_Envolvido,
 * Ambiente, Disposição, Grupo_Recurso e Recurso, vínculos EAMB/EREC/VREC e imagem da Disposição.
 *
 * <p>Consultas: todos os perfis autenticados (exceto Setor, só Administrador); por padrão apenas itens
 * ativos (Req. 7.14). Escritas e {@code todos=true}: somente Administrador (403 para os demais).
 * Validações acumulam todas as violações (422).
 */
public final class ServicoCatalogo {

    public static final String GRUPO_ADMINISTRADOR = "Administrador";

    public static final String CAMPO_OBRIGATORIO = "CAMPO_OBRIGATORIO";
    public static final String EMAIL_INVALIDO = "EMAIL_INVALIDO";
    public static final String DISPONIBILIDADE_INVALIDA = "DISPONIBILIDADE_INVALIDA";
    public static final String GRUPO_INEXISTENTE = "GRUPO_INEXISTENTE";
    public static final String SETOR_INEXISTENTE = "SETOR_INEXISTENTE";
    public static final String AMBIENTE_INEXISTENTE = "AMBIENTE_INEXISTENTE";
    public static final String SETOR_OUTRA_UNIDADE = "SETOR_OUTRA_UNIDADE";
    public static final String ORDEM_INVALIDA = "ORDEM_INVALIDA";

    /** Formato prático de e-mail: parte local, {@code @}, domínio com ao menos um ponto. */
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?)+$");
    /** Prefixo das chaves das imagens no bucket. */
    static final String PREFIXO_IMAGENS = "icones-disposicao/";

    private final PortaCatalogo porta;
    private final ArmazenamentoImagens imagens;

    public ServicoCatalogo(PortaCatalogo porta, ArmazenamentoImagens imagens) {
        this.porta = Objects.requireNonNull(porta, "porta");
        this.imagens = Objects.requireNonNull(imagens, "imagens");
    }

    // =====================================================================
    // Ambiente
    // =====================================================================

    public List<AmbienteResposta> listarAmbientes(Principal p, boolean todos) {
        exigirTodosSomenteAdmin(p, todos);
        return porta.listarAmbientes().stream()
                .filter(r -> todos || r.ambiente().ativo())
                .sorted(Comparator.comparing(r -> r.ambiente().descricao(), String.CASE_INSENSITIVE_ORDER))
                .map(ServicoCatalogo::resposta).toList();
    }

    public AmbienteResposta obterAmbiente(Principal p, long id) {
        return resposta(exigirAmbiente(id));
    }

    public AmbienteResposta criarAmbiente(AmbienteRequisicao req, Principal p) {
        exigirAdmin(p);
        return gravarAmbiente(null, exigirCorpo(req), true);
    }

    public AmbienteResposta alterarAmbiente(long id, AmbienteRequisicao req, Principal p) {
        exigirAdmin(p);
        RegistroAmbiente atual = exigirAmbiente(id);
        return gravarAmbiente(id, exigirCorpo(req), atual.ambiente().ativo());
    }

    public AmbienteResposta inativarAmbiente(long id, Principal p) {
        exigirAdmin(p);
        RegistroAmbiente r = exigirAmbiente(id);
        Ambiente a = r.ambiente();
        RegistroAmbiente inativo = new RegistroAmbiente(
                new Ambiente(a.id(), a.descricao(), false, a.idPai(), a.unidade()), r.raizId());
        porta.salvarAmbiente(inativo);
        return resposta(inativo);
    }

    /** Valida o pai via {@link ArvoreAmbientes} (Req. 7.5) e recalcula a raiz da subárvore afetada. */
    private AmbienteResposta gravarAmbiente(Long idExistente, AmbienteRequisicao req, boolean ativo) {
        List<Violacao> v = new ArrayList<>();
        String descricao = obrigatorio(req.descricao(), "descricao", "Informe a descrição do ambiente.", v);
        String unidade = obrigatorio(req.unidade(), "unidade", "Informe a Unidade_Macro do ambiente.", v);
        List<RegistroAmbiente> existentes = porta.listarAmbientes();
        ArvoreAmbientes arvore = new ArvoreAmbientes(existentes.stream().map(RegistroAmbiente::ambiente).toList());
        long idValidacao = idExistente == null ? -1L : idExistente;
        if (unidade != null) {
            v.addAll(arvore.validarPai(idValidacao, unidade, req.idPai()));
        }
        lancarSeHouver(v);

        long id = idExistente == null ? porta.proximoId(PortaCatalogo.SEQ_AMBIENTE) : idExistente;
        Ambiente novo = new Ambiente(id, descricao, ativo, req.idPai(), unidade);
        // Árvore com o Ambiente atualizado para recalcular a raiz dele e dos descendentes
        Map<Long, Ambiente> porId = existentes.stream().map(RegistroAmbiente::ambiente)
                .collect(Collectors.toMap(Ambiente::id, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
        porId.put(id, novo);
        ArvoreAmbientes nova = new ArvoreAmbientes(porId.values());
        Map<Long, Long> raizAtual = existentes.stream()
                .collect(Collectors.toMap(r -> r.ambiente().id(), RegistroAmbiente::raizId, (a, b) -> a));
        RegistroAmbiente registro = new RegistroAmbiente(novo, nova.raiz(id).id());
        porta.salvarAmbiente(registro);
        for (long descendente : nova.descendentes(id)) {
            long raiz = nova.raiz(descendente).id();
            if (!Objects.equals(raizAtual.get(descendente), raiz)) {
                porta.salvarAmbiente(new RegistroAmbiente(porId.get(descendente), raiz));
            }
        }
        return resposta(registro);
    }

    private RegistroAmbiente exigirAmbiente(long id) {
        return porta.obterAmbiente(id).orElseThrow(() -> naoEncontrado("Ambiente"));
    }

    private static AmbienteResposta resposta(RegistroAmbiente r) {
        Ambiente a = r.ambiente();
        return new AmbienteResposta(Long.toString(a.id()), a.descricao(), a.ativo(),
                a.idPai() == null ? null : Long.toString(a.idPai()), a.unidade(), Long.toString(r.raizId()));
    }

    // ---------- EAMB ----------

    public List<VinculoSetorResposta> listarSetoresDoAmbiente(Principal p, long ambienteId) {
        exigirAdmin(p);
        exigirAmbiente(ambienteId);
        return porta.listarSetoresDoAmbiente(ambienteId).stream().map(ServicoCatalogo::resposta).toList();
    }

    /** Vincula Setor_Envolvido da mesma Unidade_Macro ao Ambiente (Req. 7.6). */
    public VinculoSetorResposta vincularSetorAmbiente(long ambienteId, VinculoSetorRequisicao req, Principal p) {
        exigirAdmin(p);
        exigirCorpo(req);
        Ambiente ambiente = exigirAmbiente(ambienteId).ambiente();
        Setor setor = exigirSetorDoVinculo(req.setorId());
        if (!setor.unidade().equals(ambiente.unidade())) {
            throw validacao(SETOR_OUTRA_UNIDADE,
                    "O setor deve pertencer à mesma Unidade_Macro do ambiente. Escolha outro setor.", "setorId");
        }
        VinculoSetor eamb = new VinculoSetor(porta.proximoId(PortaCatalogo.SEQ_EAMB), setor.id(), ambienteId,
                vazioParaNulo(req.codServicoSnp()));
        porta.vincularSetorAmbiente(eamb);
        return resposta(eamb);
    }

    public void desvincularSetorAmbiente(long ambienteId, long setorId, Principal p) {
        exigirAdmin(p);
        porta.desvincularSetorAmbiente(ambienteId, setorId);
    }

    // =====================================================================
    // Setor_Envolvido
    // =====================================================================

    public List<SetorResposta> listarSetores(Principal p, boolean todos) {
        exigirAdmin(p);
        return porta.listarSetores().stream()
                .filter(s -> todos || s.ativo())
                .sorted(Comparator.comparing(Setor::descricao, String.CASE_INSENSITIVE_ORDER))
                .map(ServicoCatalogo::resposta).toList();
    }

    public SetorResposta obterSetor(Principal p, long id) {
        exigirAdmin(p);
        return resposta(exigirSetor(id));
    }

    public SetorResposta criarSetor(SetorRequisicao req, Principal p) {
        exigirAdmin(p);
        return gravarSetor(null, exigirCorpo(req), true);
    }

    public SetorResposta alterarSetor(long id, SetorRequisicao req, Principal p) {
        exigirAdmin(p);
        Setor atual = exigirSetor(id);
        return gravarSetor(id, exigirCorpo(req), atual.ativo());
    }

    public SetorResposta inativarSetor(long id, Principal p) {
        exigirAdmin(p);
        Setor s = exigirSetor(id);
        Setor inativo = new Setor(s.id(), s.descricao(), s.email(), false, s.unidade(), s.emailsAlternativos());
        porta.salvarSetor(inativo);
        return resposta(inativo);
    }

    /** Descrição, Unidade_Macro e e-mail padrão obrigatórios; todos os e-mails validados (Req. 7.2, 7.3). */
    private SetorResposta gravarSetor(Long idExistente, SetorRequisicao req, boolean ativo) {
        List<Violacao> v = new ArrayList<>();
        String descricao = obrigatorio(req.descricao(), "descricao", "Informe a descrição do setor.", v);
        String unidade = obrigatorio(req.unidade(), "unidade", "Informe a Unidade_Macro do setor.", v);
        String email = obrigatorio(req.email(), "email", "Informe o e-mail padrão do setor.", v);
        if (email != null && !emailValido(email)) {
            v.add(new Violacao(EMAIL_INVALIDO, "E-mail padrão com formato inválido. Corrija o endereço.", "email"));
        }
        List<String> alternativos = new ArrayList<>();
        List<String> informados = req.emailsAlternativos() == null ? List.of() : req.emailsAlternativos();
        for (int i = 0; i < informados.size(); i++) {
            String alt = informados.get(i) == null ? "" : informados.get(i).trim();
            if (!emailValido(alt)) {
                v.add(new Violacao(EMAIL_INVALIDO,
                        "E-mail alternativo com formato inválido. Corrija o endereço.", "emailsAlternativos[" + i + "]"));
            } else {
                alternativos.add(alt);
            }
        }
        lancarSeHouver(v);
        long id = idExistente == null ? porta.proximoId(PortaCatalogo.SEQ_SETOR) : idExistente;
        Setor setor = new Setor(id, descricao, email, ativo, unidade, alternativos);
        porta.salvarSetor(setor);
        return resposta(setor);
    }

    /** Validação de formato de e-mail (Req. 7.3). */
    static boolean emailValido(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    private Setor exigirSetor(long id) {
        return porta.obterSetor(id).orElseThrow(() -> naoEncontrado("Setor"));
    }

    /** Setor de um vínculo: ausente ou inexistente → 422 (não 404, pois o recurso da URL existe). */
    private Setor exigirSetorDoVinculo(Long setorId) {
        if (setorId == null) {
            throw validacao(CAMPO_OBRIGATORIO, "Informe o setor do vínculo.", "setorId");
        }
        return porta.obterSetor(setorId).orElseThrow(() -> validacao(SETOR_INEXISTENTE,
                "O setor informado não existe. Escolha um setor cadastrado.", "setorId"));
    }

    private static SetorResposta resposta(Setor s) {
        return new SetorResposta(Long.toString(s.id()), s.descricao(), s.email(), s.emailsAlternativos(),
                s.unidade(), s.ativo());
    }

    private static VinculoSetorResposta resposta(VinculoSetor v) {
        return new VinculoSetorResposta(Long.toString(v.id()), Long.toString(v.envoId()), Long.toString(v.alvoId()),
                v.codServicoSnp());
    }

    // =====================================================================
    // Disposição
    // =====================================================================

    public List<DisposicaoResposta> listarDisposicoes(Principal p, boolean todos) {
        exigirTodosSomenteAdmin(p, todos);
        return porta.listarDisposicoes().stream()
                .filter(d -> todos || d.ativo())
                .sorted(Comparator.comparing(Disposicao::descricao, String.CASE_INSENSITIVE_ORDER))
                .map(ServicoCatalogo::resposta).toList();
    }

    public DisposicaoResposta obterDisposicao(Principal p, long id) {
        return resposta(exigirDisposicao(id));
    }

    public DisposicaoResposta criarDisposicao(DisposicaoRequisicao req, Principal p) {
        exigirAdmin(p);
        exigirCorpo(req);
        String descricao = descricaoObrigatoria(req.descricao(), "Informe a descrição da disposição.");
        Disposicao d = new Disposicao(porta.proximoId(PortaCatalogo.SEQ_DISPOSICAO), descricao, true, null, null,
                vazioParaNulo(req.alt()));
        porta.salvarDisposicao(d);
        return resposta(d);
    }

    public DisposicaoResposta alterarDisposicao(long id, DisposicaoRequisicao req, Principal p) {
        exigirAdmin(p);
        exigirCorpo(req);
        Disposicao atual = exigirDisposicao(id);
        String descricao = descricaoObrigatoria(req.descricao(), "Informe a descrição da disposição.");
        Disposicao d = new Disposicao(id, descricao, atual.ativo(), atual.iconeArquivo(), atual.iconeRefOriginal(),
                vazioParaNulo(req.alt()));
        porta.salvarDisposicao(d);
        return resposta(d);
    }

    public DisposicaoResposta inativarDisposicao(long id, Principal p) {
        exigirAdmin(p);
        Disposicao a = exigirDisposicao(id);
        Disposicao d = new Disposicao(id, a.descricao(), false, a.iconeArquivo(), a.iconeRefOriginal(),
                a.textoAlternativo());
        porta.salvarDisposicao(d);
        return resposta(d);
    }

    /**
     * Grava a imagem única da Disposição (Req. 7.7, 7.8) após validar assinatura, tamanho e SVG sanitizado.
     * A imagem anterior é substituída pela nova referência.
     */
    public DisposicaoResposta enviarImagem(long id, byte[] conteudo, Principal p) {
        exigirAdmin(p);
        Disposicao atual = exigirDisposicao(id);
        ValidadorImagem.Tipo tipo = ValidadorImagem.validar(conteudo);
        String arquivo = "disposicao-" + id + "." + tipo.extensao();
        imagens.gravar(PREFIXO_IMAGENS + arquivo, conteudo, tipo.contentType());
        Disposicao d = new Disposicao(id, atual.descricao(), atual.ativo(), arquivo, atual.iconeRefOriginal(),
                atual.textoAlternativo());
        porta.salvarDisposicao(d);
        return resposta(d);
    }

    private Disposicao exigirDisposicao(long id) {
        return porta.obterDisposicao(id).orElseThrow(() -> naoEncontrado("Disposição"));
    }

    private static DisposicaoResposta resposta(Disposicao d) {
        return new DisposicaoResposta(Long.toString(d.id()), d.descricao(), d.ativo(), d.iconeArquivo(),
                d.textoAlternativo());
    }

    // =====================================================================
    // Grupo_Recurso
    // =====================================================================

    public List<GrupoResposta> listarGrupos(Principal p, boolean todos) {
        exigirTodosSomenteAdmin(p, todos);
        return porta.listarGrupos().stream()
                .filter(g -> todos || g.ativo())
                .sorted(Comparator.comparingInt(GrupoRecurso::ordem).thenComparing(GrupoRecurso::descricao))
                .map(ServicoCatalogo::resposta).toList();
    }

    public GrupoResposta obterGrupo(Principal p, long id) {
        return resposta(exigirGrupo(id));
    }

    public GrupoResposta criarGrupo(GrupoRequisicao req, Principal p) {
        exigirAdmin(p);
        return gravarGrupo(null, exigirCorpo(req), true);
    }

    public GrupoResposta alterarGrupo(long id, GrupoRequisicao req, Principal p) {
        exigirAdmin(p);
        GrupoRecurso atual = exigirGrupo(id);
        return gravarGrupo(id, exigirCorpo(req), atual.ativo());
    }

    public GrupoResposta inativarGrupo(long id, Principal p) {
        exigirAdmin(p);
        GrupoRecurso g = exigirGrupo(id);
        GrupoRecurso inativo = new GrupoRecurso(id, g.descricao(), g.ordem(), false);
        porta.salvarGrupo(inativo);
        return resposta(inativo);
    }

    /** Descrição obrigatória e ordem numérica opcional, não negativa (Req. 7.9). */
    private GrupoResposta gravarGrupo(Long idExistente, GrupoRequisicao req, boolean ativo) {
        List<Violacao> v = new ArrayList<>();
        String descricao = obrigatorio(req.descricao(), "descricao", "Informe a descrição do grupo.", v);
        if (req.ordem() != null && req.ordem() < 0) {
            v.add(new Violacao(ORDEM_INVALIDA, "A ordem do grupo deve ser um número não negativo.", "ordem"));
        }
        lancarSeHouver(v);
        long id = idExistente == null ? porta.proximoId(PortaCatalogo.SEQ_GRUPO) : idExistente;
        GrupoRecurso g = new GrupoRecurso(id, descricao, req.ordem() == null ? 0 : req.ordem(), ativo);
        porta.salvarGrupo(g);
        return resposta(g);
    }

    private GrupoRecurso exigirGrupo(long id) {
        return porta.obterGrupo(id).orElseThrow(() -> naoEncontrado("Grupo"));
    }

    private static GrupoResposta resposta(GrupoRecurso g) {
        return new GrupoResposta(Long.toString(g.id()), g.descricao(), g.ordem(), g.ativo());
    }

    // =====================================================================
    // Recurso
    // =====================================================================

    /** Recursos com grupo (nome e ordem) e ambientes permitidos (VREC), na ordem GREC_ORDEM (Req. 7.10). */
    public List<RecursoResposta> listarRecursos(Principal p, boolean todos) {
        exigirTodosSomenteAdmin(p, todos);
        Map<Long, GrupoRecurso> grupos = porta.listarGrupos().stream()
                .collect(Collectors.toMap(GrupoRecurso::id, Function.identity(), (a, b) -> a));
        return porta.listarRecursos().stream()
                .filter(r -> todos || r.recurso().ativo())
                .map(r -> resposta(r, r.grupoId() == null ? null : grupos.get(r.grupoId())))
                .sorted(Comparator.comparing((RecursoResposta r) -> r.grupoOrdem() == null ? Integer.MAX_VALUE : r.grupoOrdem())
                        .thenComparing(RecursoResposta::descricao, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public RecursoResposta obterRecurso(Principal p, long id) {
        RegistroRecurso r = exigirRecurso(id);
        return resposta(r, r.grupoId() == null ? null : porta.obterGrupo(r.grupoId()).orElse(null));
    }

    public RecursoResposta criarRecurso(RecursoRequisicao req, Principal p) {
        exigirAdmin(p);
        return gravarRecurso(null, exigirCorpo(req), true, null);
    }

    public RecursoResposta alterarRecurso(long id, RecursoRequisicao req, Principal p) {
        exigirAdmin(p);
        RegistroRecurso atual = exigirRecurso(id);
        return gravarRecurso(id, exigirCorpo(req), atual.recurso().ativo(), atual);
    }

    public RecursoResposta inativarRecurso(long id, Principal p) {
        exigirAdmin(p);
        RegistroRecurso r = exigirRecurso(id);
        Recurso a = r.recurso();
        RegistroRecurso inativo = new RegistroRecurso(new Recurso(a.id(), a.descricao(), false, a.limitado(),
                a.disponibilidade(), a.unidade(), a.ambientesVinculados()), r.grupoId(), r.iconeArquivo(),
                r.iconeRefOriginal());
        porta.salvarRecurso(inativo);
        return obterRecurso(p, id);
    }

    /**
     * Descrição, exatamente um Grupo_Recurso existente e ícone obrigatórios (Req. 7.11); se limitado,
     * disponibilidade ≥ 1 (Req. 7.12).
     */
    private RecursoResposta gravarRecurso(Long idExistente, RecursoRequisicao req, boolean ativo,
                                          RegistroRecurso atual) {
        List<Violacao> v = new ArrayList<>();
        String descricao = obrigatorio(req.descricao(), "descricao", "Informe a descrição do recurso.", v);
        String icone = obrigatorio(req.icone(), "icone", "Informe o ícone do recurso.", v);
        GrupoRecurso grupo = null;
        if (req.grupoId() == null) {
            v.add(new Violacao(CAMPO_OBRIGATORIO, "Informe o grupo do recurso.", "grupoId"));
        } else {
            grupo = porta.obterGrupo(req.grupoId()).orElse(null);
            if (grupo == null) {
                v.add(new Violacao(GRUPO_INEXISTENTE,
                        "O grupo informado não existe. Escolha um grupo cadastrado.", "grupoId"));
            }
        }
        boolean limitado = Boolean.TRUE.equals(req.limitado());
        int disponibilidade = req.disponibilidade() == null ? 0 : req.disponibilidade();
        if (limitado && disponibilidade < 1) {
            v.add(new Violacao(DISPONIBILIDADE_INVALIDA,
                    "Recurso limitado exige quantidade disponível de pelo menos 1. Informe a quantidade.",
                    "disponibilidade"));
        } else if (disponibilidade < 0) {
            v.add(new Violacao(DISPONIBILIDADE_INVALIDA,
                    "A quantidade disponível não pode ser negativa.", "disponibilidade"));
        }
        lancarSeHouver(v);
        long id = idExistente == null ? porta.proximoId(PortaCatalogo.SEQ_RECURSO) : idExistente;
        Recurso recurso = new Recurso(id, descricao, ativo, limitado, limitado ? disponibilidade : 0,
                vazioParaNulo(req.unidade()), atual == null ? null : atual.recurso().ambientesVinculados());
        RegistroRecurso registro = new RegistroRecurso(recurso, req.grupoId(), icone,
                atual == null ? null : atual.iconeRefOriginal());
        porta.salvarRecurso(registro);
        return resposta(registro, grupo);
    }

    private RegistroRecurso exigirRecurso(long id) {
        return porta.obterRecurso(id).orElseThrow(() -> naoEncontrado("Recurso"));
    }

    private static RecursoResposta resposta(RegistroRecurso r, GrupoRecurso grupo) {
        Recurso rec = r.recurso();
        List<String> ambientes = rec.ambientesVinculados().stream().sorted().map(String::valueOf).toList();
        return new RecursoResposta(Long.toString(rec.id()), rec.descricao(), rec.ativo(), rec.limitado(),
                rec.limitado() ? rec.disponibilidade() : null,
                r.grupoId() == null ? null : Long.toString(r.grupoId()),
                grupo == null ? null : grupo.descricao(), grupo == null ? null : grupo.ordem(),
                ambientes, rec.unidade(), r.iconeArquivo());
    }

    // ---------- EREC ----------

    public List<VinculoSetorResposta> listarSetoresDoRecurso(Principal p, long recursoId) {
        exigirAdmin(p);
        exigirRecurso(recursoId);
        return porta.listarSetoresDoRecurso(recursoId).stream().map(ServicoCatalogo::resposta).toList();
    }

    /** Vincula Setor_Envolvido ao Recurso (Req. 7.13). */
    public VinculoSetorResposta vincularSetorRecurso(long recursoId, VinculoSetorRequisicao req, Principal p) {
        exigirAdmin(p);
        exigirCorpo(req);
        exigirRecurso(recursoId);
        Setor setor = exigirSetorDoVinculo(req.setorId());
        VinculoSetor erec = new VinculoSetor(porta.proximoId(PortaCatalogo.SEQ_EREC), setor.id(), recursoId,
                vazioParaNulo(req.codServicoSnp()));
        porta.vincularSetorRecurso(erec);
        return resposta(erec);
    }

    public void desvincularSetorRecurso(long recursoId, long setorId, Principal p) {
        exigirAdmin(p);
        porta.desvincularSetorRecurso(recursoId, setorId);
    }

    // ---------- VREC ----------

    /** Vincula o Recurso a um Ambiente (Req. 7.13); devolve o Recurso com os ambientes permitidos. */
    public RecursoResposta vincularRecursoAmbiente(long recursoId, VinculoAmbienteRequisicao req, Principal p) {
        exigirAdmin(p);
        exigirCorpo(req);
        exigirRecurso(recursoId);
        if (req.ambienteId() == null) {
            throw validacao(CAMPO_OBRIGATORIO, "Informe o ambiente do vínculo.", "ambienteId");
        }
        if (porta.obterAmbiente(req.ambienteId()).isEmpty()) {
            throw validacao(AMBIENTE_INEXISTENTE,
                    "O ambiente informado não existe. Escolha um ambiente cadastrado.", "ambienteId");
        }
        porta.vincularRecursoAmbiente(new VinculoRecursoAmbiente(porta.proximoId(PortaCatalogo.SEQ_VREC),
                recursoId, req.ambienteId()));
        return obterRecurso(p, recursoId);
    }

    public RecursoResposta desvincularRecursoAmbiente(long recursoId, long ambienteId, Principal p) {
        exigirAdmin(p);
        exigirRecurso(recursoId);
        porta.desvincularRecursoAmbiente(recursoId, ambienteId);
        return obterRecurso(p, recursoId);
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /** Escritas e cadastros restritos ao Administrador (autorização no backend). */
    private static void exigirAdmin(Principal p) {
        if (p == null || !p.pertenceA(GRUPO_ADMINISTRADOR)) {
            throw new ExcecaoAcessoNegado("Operação restrita ao Administrador.");
        }
    }

    /** {@code todos=true} (inclui inativos) somente para o Administrador. */
    private static void exigirTodosSomenteAdmin(Principal p, boolean todos) {
        if (todos) {
            exigirAdmin(p);
        }
    }

    private static <T> T exigirCorpo(T corpo) {
        if (corpo == null) {
            throw validacao(CAMPO_OBRIGATORIO, "Envie o corpo da requisição.", null);
        }
        return corpo;
    }

    private static String obrigatorio(String valor, String campo, String mensagem, List<Violacao> v) {
        String limpo = vazioParaNulo(valor);
        if (limpo == null) {
            v.add(new Violacao(CAMPO_OBRIGATORIO, mensagem, campo));
        }
        return limpo;
    }

    private static String descricaoObrigatoria(String valor, String mensagem) {
        List<Violacao> v = new ArrayList<>();
        String d = obrigatorio(valor, "descricao", mensagem, v);
        lancarSeHouver(v);
        return d;
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static void lancarSeHouver(List<Violacao> v) {
        if (!v.isEmpty()) {
            throw new ExcecaoValidacao(v);
        }
    }

    private static ExcecaoValidacao validacao(String codigo, String mensagem, String campo) {
        return new ExcecaoValidacao(List.of(new Violacao(codigo, mensagem, campo)));
    }

    private static ExcecaoNaoEncontrado naoEncontrado(String entidade) {
        return new ExcecaoNaoEncontrado(entidade + " não encontrado.");
    }
}
