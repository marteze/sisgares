package br.mp.mpf.sisgares.comumaws.repositorio;

import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.n;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.numero;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.s;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.texto;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.Configuracao.FaixaHoraria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

/**
 * Repositório da Configuração: {@code PK=CONFIG}, {@code SK=GLOBAL} (antecedência, faixa global,
 * URL do SNP) e {@code SK=UNID#<u>} (faixa própria da Unidade_Macro).
 *
 * <p>Leitura com cache em memória de {@value #VALIDADE_CACHE_SEGUNDOS} s medido pelo {@link Clock}:
 * uma configuração salva passa a valer, em todos os contêineres, para validações iniciadas até
 * 60 s depois, sem novo deploy (Req. 8.6). A carga é uma única Query na partição {@code CONFIG}.
 */
public final class RepositorioConfiguracao {

    public static final long VALIDADE_CACHE_SEGUNDOS = 60;
    static final Duration VALIDADE_CACHE = Duration.ofSeconds(VALIDADE_CACHE_SEGUNDOS);

    static final String ANTECEDENCIA = "antecedenciaMinutos";
    static final String FAIXA_MINIMA = "faixaMinima";
    static final String FAIXA_MAXIMA = "faixaMaxima";
    static final String SNP_URL = "snpUrl";
    static final String UNIDADE = NomesTabela.UNIDADE;

    /** Cópia imutável carregada do DynamoDB e o instante da carga. */
    private record Instantaneo(Configuracao configuracao, String snpUrl, Instant carregadoEm) {
    }

    private final OperacoesDynamo dynamo;
    private final Clock clock;
    private final AtomicReference<Instantaneo> cache = new AtomicReference<>();

    public RepositorioConfiguracao(DynamoDbClient cliente, String nomeTabela, Clock clock) {
        this.dynamo = new OperacoesDynamo(cliente, nomeTabela);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Configuração vigente (do cache, se carregada há menos de 60 s). */
    public Configuracao obter() {
        return instantaneo().configuracao();
    }

    /** URL base do SNP, se configurada. */
    public Optional<String> snpUrl() {
        return Optional.ofNullable(instantaneo().snpUrl());
    }

    /**
     * Grava a configuração global e as faixas por unidade numa única transação, removendo faixas de
     * unidades que deixaram de existir no mapa. Invalida o cache local.
     */
    public void salvar(Configuracao configuracao, String snpUrl) {
        Objects.requireNonNull(configuracao, "configuracao");
        List<TransactWriteItem> itens = new ArrayList<>();

        Map<String, AttributeValue> global = Atributos.chave(Chaves.CONFIG, Chaves.GLOBAL);
        global.put(ANTECEDENCIA, n(configuracao.antecedenciaMinutos()));
        gravarFaixa(global, configuracao.faixaGlobal());
        Atributos.textoOpcional(global, SNP_URL, snpUrl);
        itens.add(dynamo.put(global));

        configuracao.faixasPorUnidade().forEach((unidade, faixa) -> {
            Map<String, AttributeValue> item =
                    Atributos.chave(Chaves.CONFIG, Chaves.skConfiguracaoUnidade(unidade));
            item.put(UNIDADE, s(unidade));
            gravarFaixa(item, faixa);
            itens.add(dynamo.put(item));
        });

        // Remove faixas de unidades ausentes na nova configuração
        for (Map<String, AttributeValue> existente : dynamo.consultar(Chaves.CONFIG, Chaves.UNID)) {
            String unidade = texto(existente, UNIDADE);
            if (!configuracao.faixasPorUnidade().containsKey(unidade)) {
                itens.add(dynamo.delete(Chaves.CONFIG, Chaves.skConfiguracaoUnidade(unidade)));
            }
        }
        dynamo.transacao(itens);
        cache.set(null);
    }

    private Instantaneo instantaneo() {
        Instant agora = clock.instant();
        Instantaneo atual = cache.get();
        if (atual != null && agora.isBefore(atual.carregadoEm().plus(VALIDADE_CACHE))) {
            return atual;
        }
        Instantaneo novo = carregar(agora);
        cache.set(novo);
        return novo;
    }

    /** Query {@code PK=CONFIG}: item GLOBAL e itens {@code UNID#<u>}. */
    private Instantaneo carregar(Instant agora) {
        Map<String, AttributeValue> global = null;
        Map<String, FaixaHoraria> faixas = new HashMap<>();
        for (Map<String, AttributeValue> item : dynamo.consultar(Chaves.CONFIG)) {
            String sk = texto(item, NomesTabela.SK);
            if (Chaves.GLOBAL.equals(sk)) {
                global = item;
            } else if (sk.startsWith(Chaves.UNID)) {
                faixas.put(texto(item, UNIDADE), lerFaixa(item));
            }
        }
        if (global == null) {
            throw new IllegalStateException("Configuração global não encontrada; cadastre-a antes de validar reservas.");
        }
        Configuracao configuracao = new Configuracao((int) numero(global, ANTECEDENCIA), lerFaixa(global), faixas);
        return new Instantaneo(configuracao, Atributos.lerTexto(global, SNP_URL), agora);
    }

    private static void gravarFaixa(Map<String, AttributeValue> item, FaixaHoraria faixa) {
        item.put(FAIXA_MINIMA, s(faixa.minimo().toString()));
        item.put(FAIXA_MAXIMA, s(faixa.maximo().toString()));
    }

    private static FaixaHoraria lerFaixa(Map<String, AttributeValue> item) {
        return new FaixaHoraria(LocalTime.parse(texto(item, FAIXA_MINIMA)), LocalTime.parse(texto(item, FAIXA_MAXIMA)));
    }
}
