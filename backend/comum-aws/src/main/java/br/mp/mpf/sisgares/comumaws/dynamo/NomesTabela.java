package br.mp.mpf.sisgares.comumaws.dynamo;

/**
 * Nomes de atributos e índices da tabela única {@code sisgares} (design.md, seção Data Models).
 */
public final class NomesTabela {

    /** Nome padrão da tabela; o valor efetivo vem da variável {@code TABELA_SISGARES}. */
    public static final String TABELA_PADRAO = "sisgares";

    // Chave primária
    public static final String PK = "PK";
    public static final String SK = "SK";

    // Índices secundários globais
    /** Conflitos RN5/RN6 e grade: {@code RAIZ#<raizId>} / {@code <dataIni>#<reseId>}. */
    public static final String GSI1 = "GSI1";
    /** Painel Atendente e exportação: {@code ENVO#<envoId>} / {@code <dataIni>#<reseId>}. */
    public static final String GSI2 = "GSI2";
    /** Painel Admin: {@code UNID#<u>} / {@code <dataIni>#<reseId>}. */
    public static final String GSI3 = "GSI3";
    /** Minhas reservas: {@code SOLIC#<sub>} / {@code <criadoEm>#<reseId>}. */
    public static final String GSI4 = "GSI4";
    /** Disponibilidade RN8: {@code RECU#<id>} / {@code <dataIni>#<reseId>}. */
    public static final String GSI5 = "GSI5";

    // Atributos de chave dos GSIs
    public static final String GSI1PK = "GSI1PK";
    public static final String GSI1SK = "GSI1SK";
    public static final String GSI2PK = "GSI2PK";
    public static final String GSI2SK = "GSI2SK";
    public static final String GSI3PK = "GSI3PK";
    public static final String GSI3SK = "GSI3SK";
    public static final String GSI4PK = "GSI4PK";
    public static final String GSI4SK = "GSI4SK";
    public static final String GSI5PK = "GSI5PK";
    public static final String GSI5SK = "GSI5SK";

    // Atributos de controle
    /** Versão para bloqueio otimista (RN7) em {@code RESE#/META} e itens {@code CTRL}. */
    public static final String VERSAO = "versao";
    /** TTL (epoch em segundos) de notificações e eventos processados. */
    public static final String EXPIRA_EM = "expiraEm";
    /** Identificador da raiz da árvore de ambientes. */
    public static final String RAIZ_ID = "raizId";
    public static final String UNIDADE = "unidade";
    public static final String CRIADO_EM = "criadoEm";
    public static final String INICIO = "ini";
    public static final String FIM = "fim";
    public static final String PAYLOAD = "payload";
    public static final String TENTATIVAS = "tentativas";

    private NomesTabela() {
    }
}
