package br.mp.mpf.sisgares.comumaws.http;

import java.io.PrintStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Métricas de negócio no CloudWatch via Embedded Metric Format (EMF), Req. 21.2.
 *
 * <p>Cada chamada escreve uma linha JSON no {@code stdout} com o bloco {@code _aws.CloudWatchMetrics};
 * o CloudWatch Logs extrai a métrica sem SDK extra. Namespace {@value #NAMESPACE}, unidade
 * {@code Count}. Dimensões são apenas códigos fixos (ex.: {@code Regra=RN5}); nunca dados pessoais.
 * Falhas na emissão são silenciosas: métrica nunca interrompe o caso de uso.
 */
public final class Metricas {

    public static final String NAMESPACE = "SISGARES";
    public static final String RESERVA_CRIADA = "ReservaCriada";
    public static final String CONFLITO = "Conflito";
    public static final String VIOLACAO_VALIDACAO = "ViolacaoValidacao";
    public static final String FALHA_NOTIFICACAO = "FalhaNotificacao";
    public static final String FALHA_SNP = "FalhaSnp";
    /** Nome da dimensão usada em {@link #CONFLITO} e {@link #VIOLACAO_VALIDACAO}. */
    public static final String DIMENSAO_REGRA = "Regra";

    private final Clock clock;
    private final PrintStream saida;

    /** Métricas que escrevem em {@code System.out}. */
    public Metricas(Clock clock) {
        this(clock, System.out);
    }

    /** Construtor com saída explícita (útil em testes). */
    public Metricas(Clock clock, PrintStream saida) {
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
        this.saida = Objects.requireNonNull(saida, "saida é obrigatória");
    }

    /** Incrementa em 1 a métrica sem dimensões. */
    public void contar(String nome) {
        emitir(nome, Map.of());
    }

    /** Incrementa em 1 a métrica com uma dimensão. */
    public void contar(String nome, String dimensao, String valor) {
        emitir(nome, Map.of(dimensao, valor));
    }

    private void emitir(String nome, Map<String, String> dimensoes) {
        try {
            saida.println(formatar(nome, dimensoes));
        } catch (RuntimeException e) {
            // Métrica é acessória: nunca propaga erro ao chamador
        }
    }

    /** Monta a linha EMF sem escrevê-la. */
    String formatar(String nome, Map<String, String> dimensoes) {
        Objects.requireNonNull(nome, "nome é obrigatório");
        Map<String, Object> metrica = new LinkedHashMap<>();
        metrica.put("Namespace", NAMESPACE);
        // Lista vazia de dimensões publica a métrica sem dimensão
        metrica.put("Dimensions", List.of(List.copyOf(dimensoes.keySet())));
        metrica.put("Metrics", List.of(Map.of("Name", nome, "Unit", "Count")));

        Map<String, Object> aws = new LinkedHashMap<>();
        aws.put("Timestamp", clock.millis());
        aws.put("CloudWatchMetrics", List.of(metrica));

        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("_aws", aws);
        linha.putAll(dimensoes);
        linha.put(nome, 1);
        return Json.escrever(linha);
    }
}
