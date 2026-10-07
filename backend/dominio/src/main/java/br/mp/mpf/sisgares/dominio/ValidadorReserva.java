package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validador_Reserva: aplica as regras de negócio a uma {@link Reserva} e acumula todas as
 * violações encontradas, para que sejam devolvidas juntas no HTTP 422 (Req. 9.15).
 *
 * <p>Cada regra fica em um método privado próprio: RN1–RN6, RN8 e RN9 em {@link #validar}, RN12 em
 * {@link #validarAlteracao} e {@link #validarCancelamento}.
 * As mensagens não contêm dados pessoais e sempre trazem o código da regra.
 */
public final class ValidadorReserva {

    /** Detecção de conflitos de horário (RN5/RN6). */
    private final DetectorConflito detector = new DetectorConflito();

    /** Cálculo de disponibilidade de Recurso_Limitado (RN8). */
    private final CalculadoraDisponibilidade calculadora = new CalculadoraDisponibilidade();

    /**
     * Valida a reserva no contexto informado.
     *
     * @param r   reserva a validar
     * @param ctx dados externos (relógio, configuração, ocupação etc.)
     * @return lista imutável de violações; vazia quando a reserva é válida
     */
    public List<Violacao> validar(Reserva r, ContextoValidacao ctx) {
        Objects.requireNonNull(r, "reserva é obrigatória");
        Objects.requireNonNull(ctx, "contexto é obrigatório");

        List<Violacao> violacoes = new ArrayList<>();
        validarRn1Periodos(r, violacoes);
        validarRn2CamposObrigatorios(r, violacoes);
        validarRn3FaixaHoraria(r, ctx, violacoes);
        validarRn4Antecedencia(r, ctx, violacoes);
        validarRn5PeriodosProprios(r, violacoes);
        validarRn5Rn6Conflitos(r, ctx, violacoes);
        validarRn8Rn9Recursos(r, ctx, violacoes);
        return List.copyOf(violacoes);
    }

    /**
     * Valida a alteração de uma Reserva existente (Req. 12.1 e 12.2).
     *
     * <p>Aplica RN12.1 (reserva transcorrida ou cancelada não é editável) sobre o estado gravado em
     * {@code ctx.reservaAnterior()} e, em seguida, as mesmas regras do cadastro (RN1–RN9), acumulando
     * todas as violações.
     *
     * @param nova reserva com os dados alterados
     * @param ctx  contexto com {@code reservaAnterior} preenchido
     * @return lista imutável de violações; vazia quando a alteração é válida
     */
    public List<Violacao> validarAlteracao(Reserva nova, ContextoValidacao ctx) {
        Objects.requireNonNull(nova, "reserva é obrigatória");
        Objects.requireNonNull(ctx, "contexto é obrigatório");

        List<Violacao> violacoes = new ArrayList<>();
        // O status vem do estado gravado; sem ele, usa a própria reserva recebida
        Reserva base = ctx.reservaAnterior() != null ? ctx.reservaAnterior() : nova;
        Status status = new CalculadoraStatus(ctx.relogio()).status(base);
        if (status == Status.TRANSCORRIDA || status == Status.CANCELADA) {
            violacoes.add(Violacao.de(CodigosRegra.RN12_RESERVA_NAO_EDITAVEL,
                    "RN12: reservas transcorridas ou canceladas não podem ser alteradas. Cadastre uma nova reserva."));
        }
        violacoes.addAll(validar(nova, ctx));
        return List.copyOf(violacoes);
    }

    /**
     * Valida o pedido de cancelamento (Req. 12.6 e 12.7).
     *
     * @param r          reserva a cancelar (estado gravado)
     * @param confirmado indica se o pedido traz a confirmação explícita do Solicitante
     * @param ctx        contexto com relógio e Antecedência_Mínima
     * @return lista imutável de violações; vazia quando o cancelamento é permitido
     */
    public List<Violacao> validarCancelamento(Reserva r, boolean confirmado, ContextoValidacao ctx) {
        Objects.requireNonNull(r, "reserva é obrigatória");
        Objects.requireNonNull(ctx, "contexto é obrigatório");

        List<Violacao> violacoes = new ArrayList<>();
        // RN12.6: confirmação explícita obrigatória
        if (!confirmado) {
            violacoes.add(Violacao.de(CodigosRegra.RN12_CONFIRMACAO_OBRIGATORIA,
                    "RN12: confirme o cancelamento da reserva antes de enviar o pedido."));
        }
        // RN12.7: agora + Antecedência_Mínima não pode passar do primeiro início
        int antecedencia = ctx.configuracao().antecedenciaMinutos();
        LocalDateTime limite = ctx.agora().plusMinutes(antecedencia);
        r.primeiroInicio().ifPresent(inicio -> {
            if (limite.isAfter(inicio)) {
                violacoes.add(Violacao.de(CodigosRegra.RN12_CANCELAMENTO_SEM_ANTECEDENCIA,
                        "RN12: o cancelamento exige pelo menos " + antecedencia
                                + " minutos de antecedência do primeiro período. Procure o atendimento da unidade."));
            }
        });
        return List.copyOf(violacoes);
    }

    /** RN1: ao menos um Período e término posterior ao início (atravessar a meia-noite é válido). */
    private void validarRn1Periodos(Reserva r, List<Violacao> violacoes) {
        if (r.periodos().isEmpty()) {
            violacoes.add(new Violacao(CodigosRegra.RN1_SEM_PERIODO,
                    "RN1: informe ao menos um período para a reserva.", "periodos"));
            return;
        }
        for (int i = 0; i < r.periodos().size(); i++) {
            // Compara data e hora completas, então 18:00 de um dia a 09:00 do dia seguinte é aceito
            if (!r.periodos().get(i).valido()) {
                violacoes.add(new Violacao(CodigosRegra.RN1_TERMINO_INVALIDO,
                        "RN1: o término do período deve ser posterior ao início. Ajuste a data ou o horário de término.",
                        campoPeriodo(i)));
            }
        }
    }

    /** RN2: finalidade, participantes (≥ 1) e, para Local_Proprio, complemento obrigatórios. */
    private void validarRn2CamposObrigatorios(Reserva r, List<Violacao> violacoes) {
        if (vazio(r.finalidade())) {
            violacoes.add(new Violacao(CodigosRegra.RN2_FINALIDADE_OBRIGATORIA,
                    "RN2: informe a finalidade da reserva.", "finalidade"));
        }
        if (r.participantes() == null || r.participantes() < 1) {
            violacoes.add(new Violacao(CodigosRegra.RN2_PARTICIPANTES_OBRIGATORIO,
                    "RN2: informe a quantidade de participantes (no mínimo 1).", "participantes"));
        }
        if (r.localProprio() && vazio(r.complemento())) {
            violacoes.add(new Violacao(CodigosRegra.RN2_COMPLEMENTO_OBRIGATORIO,
                    "RN2: para local próprio, informe o complemento do ambiente (onde o evento ocorrerá).",
                    "complemento"));
        }
    }

    /** RN3: início e término de cada Período dentro da Faixa_Horária aplicável (unidade ou global). */
    private void validarRn3FaixaHoraria(Reserva r, ContextoValidacao ctx, List<Violacao> violacoes) {
        Configuracao.FaixaHoraria faixa = ctx.configuracao().faixaAplicavel(r.unidade());
        for (int i = 0; i < r.periodos().size(); i++) {
            Periodo p = r.periodos().get(i);
            if (!faixa.contem(p.inicio().toLocalTime()) || !faixa.contem(p.termino().toLocalTime())) {
                violacoes.add(new Violacao(CodigosRegra.RN3_FORA_FAIXA,
                        "RN3: o início e o término do período devem estar entre " + faixa.minimo()
                                + " e " + faixa.maximo() + ". Ajuste os horários.",
                        campoPeriodo(i)));
            }
        }
    }

    /**
     * RN4: início de cada Período não pode ser anterior a agora + Antecedência_Mínima.
     *
     * <p>Em alteração de reserva "em andamento" (Req. 12.3), só os Períodos novos ou com início
     * alterado em relação a {@code ctx.reservaAnterior()} são verificados.
     */
    private void validarRn4Antecedencia(Reserva r, ContextoValidacao ctx, List<Violacao> violacoes) {
        int antecedencia = ctx.configuracao().antecedenciaMinutos();
        LocalDateTime limite = ctx.agora().plusMinutes(antecedencia);
        Reserva anterior = ctx.reservaAnterior();
        boolean emAndamento = anterior != null
                && new CalculadoraStatus(ctx.relogio()).status(anterior) == Status.EM_ANDAMENTO;
        for (int i = 0; i < r.periodos().size(); i++) {
            Periodo p = r.periodos().get(i);
            if (emAndamento && !novoOuInicioAlterado(p, anterior)) {
                continue;
            }
            if (p.inicio().isBefore(limite)) {
                violacoes.add(new Violacao(CodigosRegra.RN4_SEM_ANTECEDENCIA,
                        "RN4: o período deve começar com pelo menos " + antecedencia
                                + " minutos de antecedência. Escolha um início posterior.",
                        campoPeriodo(i)));
            }
        }
    }

    /**
     * Indica se o Período é novo (sem id ou id inexistente na reserva anterior) ou teve o início
     * alterado em relação ao Período gravado de mesmo id.
     */
    private static boolean novoOuInicioAlterado(Periodo p, Reserva anterior) {
        if (p.id() == null) {
            return true;
        }
        for (Periodo antigo : anterior.periodos()) {
            if (p.id().equals(antigo.id())) {
                return !p.inicio().equals(antigo.inicio());
            }
        }
        return true;
    }

    /** RN5 (Req. 9.13): dois Períodos da própria Reserva não podem conflitar segundo a Margem. */
    private void validarRn5PeriodosProprios(Reserva r, List<Violacao> violacoes) {
        // Local_Proprio não verifica conflito de horário (Req. 10.4)
        if (r.localProprio()) {
            return;
        }
        List<Periodo> periodos = r.periodos();
        for (int i = 0; i < periodos.size(); i++) {
            for (int j = i + 1; j < periodos.size(); j++) {
                if (detector.conflita(periodos.get(i), periodos.get(j))) {
                    violacoes.add(new Violacao(CodigosRegra.RN5_CONFLITO_HORARIO,
                            "RN5: os períodos " + (i + 1) + " e " + (j + 1) + " desta reserva conflitam entre si"
                                    + " (é necessário intervalo de 30 minutos). Ajuste os horários.",
                            campoPeriodo(j)));
                }
            }
        }
    }

    /**
     * RN5/RN6 (Req. 10.2, 10.3, 10.8): conflitos com outras Reservas não canceladas do mesmo Ambiente
     * ou de Ambiente_Relacionado; os Períodos da própria Reserva são ignorados pelo DetectorConflito.
     */
    private void validarRn5Rn6Conflitos(Reserva r, ContextoValidacao ctx, List<Violacao> violacoes) {
        if (r.localProprio()) {
            return;
        }
        ArvoreAmbientes arvore = new ArvoreAmbientes(ctx.ambientes().values());
        for (Conflito c : detector.conflitos(r, ctx.periodosOcupados(), arvore)) {
            int indice = r.periodos().indexOf(c.periodo());
            String regra = CodigosRegra.RN5_CONFLITO_HORARIO.equals(c.codigo())
                    ? "RN5: o ambiente já está reservado neste período"
                    : "RN6: um ambiente relacionado (pai ou filho) já está reservado neste período";
            violacoes.add(new Violacao(c.codigo(),
                    regra + " (período " + (indice + 1) + ", conflito com a reserva " + c.reservaConflitanteId()
                            + "). Escolha outro horário ou ambiente.",
                    indice >= 0 ? campoPeriodo(indice) : null));
        }
    }

    /**
     * RN8 (Req. 11.2, 11.3) e RN9 (Req. 11.5–11.7) para cada Solicitação de Recurso.
     *
     * <p>RN9 bloqueia recurso inexistente, inativo, de outra Unidade_Macro ou com vínculo VREC a
     * ambientes que não incluem o Ambiente da Reserva. RN8 só se aplica a Recurso_Limitado.
     */
    private void validarRn8Rn9Recursos(Reserva r, ContextoValidacao ctx, List<Violacao> violacoes) {
        // Quantidades de outras reservas; na alteração, exclui as da própria reserva
        List<SolicitacaoOcupada> outras = new ArrayList<>();
        for (SolicitacaoOcupada s : ctx.solicitacoesOcupadas()) {
            if (r.id() == null || s.reservaId() != r.id()) {
                outras.add(s);
            }
        }

        for (int i = 0; i < r.solicitacoes().size(); i++) {
            Solicitacao sol = r.solicitacoes().get(i);
            String campo = "solicitacoes[" + i + "]";
            Recurso rec = ctx.recurso(sol.recursoId()).orElse(null);

            // RN9: recurso precisa existir, estar ativo e ser permitido na unidade e no ambiente
            if (rec == null || !recursoPermitido(rec, r)) {
                violacoes.add(new Violacao(CodigosRegra.RN9_RECURSO_INDISPONIVEL,
                        "RN9: o recurso solicitado não está disponível para esta unidade ou ambiente. Remova-o da reserva.",
                        campo));
                continue;
            }
            if (!rec.limitado()) {
                continue;
            }
            // RN8: quantidade mínima
            if (sol.quantidade() < 1) {
                violacoes.add(new Violacao(CodigosRegra.RN8_QUANTIDADE_INVALIDA,
                        "RN8: informe quantidade de no mínimo 1 para o recurso " + rec.descricao() + ".", campo));
                continue;
            }
            // RN8: em cada Período, a quantidade pedida não pode exceder a disponível
            int pedido = quantidadeTotal(r, rec.id());
            for (int k = 0; k < r.periodos().size(); k++) {
                Periodo p = r.periodos().get(k);
                int disponivel = calculadora.disponivel(rec, p, comPeriodosProprios(outras, r, k, rec.id(), pedido));
                if (pedido > disponivel) {
                    violacoes.add(new Violacao(CodigosRegra.RN8_RECURSO_INSUFICIENTE,
                            "RN8: quantidade insuficiente do recurso " + rec.descricao() + " no período " + (k + 1)
                                    + ". Disponível: " + Math.max(0, disponivel) + ". Reduza a quantidade ou altere o período.",
                            campo));
                }
            }
        }
    }

    /** RN9: recurso ativo, da Unidade_Macro da reserva (ou de todas) e, se tiver VREC, de ambiente vinculado. */
    private static boolean recursoPermitido(Recurso rec, Reserva r) {
        if (!rec.ativo() || !rec.atendeUnidade(r.unidade())) {
            return false;
        }
        if (rec.ambientesVinculados().isEmpty()) {
            return true;
        }
        return !r.localProprio() && rec.ambientesVinculados().contains(r.ambienteId());
    }

    /** Soma das quantidades do mesmo recurso nas solicitações da reserva (pedidos repetidos se somam). */
    private static int quantidadeTotal(Reserva r, long recursoId) {
        int total = 0;
        for (Solicitacao s : r.solicitacoes()) {
            if (s.recursoId() == recursoId && s.quantidade() > 0) {
                total += s.quantidade();
            }
        }
        return total;
    }

    /**
     * Acrescenta às ocupações externas os demais Períodos da própria reserva, pois o mesmo recurso
     * fica em uso neles simultaneamente quando se cruzam (ex.: Local_Proprio com períodos sobrepostos).
     */
    private static List<SolicitacaoOcupada> comPeriodosProprios(List<SolicitacaoOcupada> outras, Reserva r,
                                                                int indiceAtual, long recursoId, int quantidade) {
        List<SolicitacaoOcupada> resultado = new ArrayList<>(outras);
        long idPropria = r.id() != null ? r.id() : -1L;
        for (int k = 0; k < r.periodos().size(); k++) {
            if (k != indiceAtual) {
                resultado.add(new SolicitacaoOcupada(idPropria, recursoId, quantidade, r.periodos().get(k)));
            }
        }
        return resultado;
    }

    /** Caminho do Período no corpo da requisição, ex.: {@code periodos[0]}. */
    private static String campoPeriodo(int indice) {
        return "periodos[" + indice + "]";
    }

    /** Texto nulo, vazio ou só com espaços. */
    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
