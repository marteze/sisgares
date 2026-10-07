package br.mp.mpf.sisgares.dominio;

/**
 * Códigos das regras de negócio devolvidos em {@link Violacao#codigo()} e no corpo HTTP 422/409.
 */
public final class CodigosRegra {

    // RN1 — períodos
    public static final String RN1_SEM_PERIODO = "RN1_SEM_PERIODO";
    public static final String RN1_TERMINO_INVALIDO = "RN1_TERMINO_INVALIDO";

    // RN2 — campos obrigatórios
    public static final String RN2_FINALIDADE_OBRIGATORIA = "RN2_FINALIDADE_OBRIGATORIA";
    public static final String RN2_PARTICIPANTES_OBRIGATORIO = "RN2_PARTICIPANTES_OBRIGATORIO";
    public static final String RN2_COMPLEMENTO_OBRIGATORIO = "RN2_COMPLEMENTO_OBRIGATORIO";

    // RN3 — faixa horária
    public static final String RN3_FORA_FAIXA = "RN3_FORA_FAIXA";

    // RN4 — antecedência mínima
    public static final String RN4_SEM_ANTECEDENCIA = "RN4_SEM_ANTECEDENCIA";

    // RN5/RN6 — conflitos de horário
    public static final String RN5_CONFLITO_HORARIO = "RN5_CONFLITO_HORARIO";
    public static final String RN6_CONFLITO_PAI_FILHO = "RN6_CONFLITO_PAI_FILHO";

    // RN7 — concorrência otimista
    public static final String RN7_CONFLITO_AO_SALVAR = "RN7_CONFLITO_AO_SALVAR";

    // RN8/RN9 — recursos
    public static final String RN8_QUANTIDADE_INVALIDA = "RN8_QUANTIDADE_INVALIDA";
    public static final String RN8_RECURSO_INSUFICIENTE = "RN8_RECURSO_INSUFICIENTE";
    public static final String RN9_RECURSO_INDISPONIVEL = "RN9_RECURSO_INDISPONIVEL";

    // RN12 — alteração e cancelamento
    public static final String RN12_RESERVA_NAO_EDITAVEL = "RN12_RESERVA_NAO_EDITAVEL";
    public static final String RN12_CONFIRMACAO_OBRIGATORIA = "RN12_CONFIRMACAO_OBRIGATORIA";
    public static final String RN12_CANCELAMENTO_SEM_ANTECEDENCIA = "RN12_CANCELAMENTO_SEM_ANTECEDENCIA";

    private CodigosRegra() {
    }
}
