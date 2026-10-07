package br.mp.mpf.sisgares.seed;

import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.comumaws.http.LogEstruturado;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Lambda do Importador_Seed (Requisito 4.2). Acionada por evento S3 (CSV gravado) ou pelo recurso
 * customizado do deploy (Provider do CDK).
 *
 * <p>Lê os CSVs de {@code BUCKET_SEED}, as Imagens_Ícone e {@code descricoes.md} de
 * {@code BUCKET_IMAGENS}, executa o {@link ImportadorSeed} e grava o relatório em
 * {@code relatorios/carga-<timestamp>.json} no bucket de seed (Requisito 4.11).
 * A notificação S3 deve filtrar o sufixo {@code .csv} para o relatório não reacionar a Lambda.
 */
public final class HandlerSeed implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    static final String VAR_BUCKET_SEED = "BUCKET_SEED";
    static final String VAR_BUCKET_IMAGENS = "BUCKET_IMAGENS";
    static final String VAR_SNP_URL = "SNP_URL";
    static final String SNP_URL_PADRAO = "https://snp-simulado.exemplo.gov.br/api";
    static final String CHAVE_DESCRICOES = "descricoes.md";
    static final String PREFIXO_RELATORIO = "relatorios/carga-";
    static final String ID_RECURSO_FISICO = "sisgares-seed";
    private static final DateTimeFormatter FORMATO_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final S3Client s3;
    private final DestinoSeed destino;
    private final Function<String, String> ambiente;
    private final Clock clock;
    private final LogEstruturado log;

    /** Construtor usado pelo runtime da Lambda. */
    public HandlerSeed() {
        this(S3Client.create(), System::getenv, ConstantesDominio.relogioPadrao());
    }

    private HandlerSeed(S3Client s3, Function<String, String> ambiente, Clock clock) {
        this(s3, new DestinoDynamo(ClienteDynamo.padrao().cliente(), ClienteDynamo.padrao().nomeTabela(), clock),
                ambiente, clock);
    }

    /** Construtor para testes (S3 local e destino em memória). */
    HandlerSeed(S3Client s3, DestinoSeed destino, Function<String, String> ambiente, Clock clock) {
        this.s3 = Objects.requireNonNull(s3, "s3");
        this.destino = Objects.requireNonNull(destino, "destino");
        this.ambiente = Objects.requireNonNull(ambiente, "ambiente");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = new LogEstruturado(clock, "seed");
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> evento, Context contexto) {
        String correlationId = contexto != null ? contexto.getAwsRequestId() : "local";
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("PhysicalResourceId", ID_RECURSO_FISICO);

        // Recurso customizado: remoção da stack não apaga dados
        if (evento != null && "Delete".equals(evento.get("RequestType"))) {
            log.info(correlationId, "Seed ignorado na remoção da stack.");
            return resposta;
        }
        if (!contemCsv(evento)) {
            log.info(correlationId, "Evento S3 sem CSV; seed não executado.");
            return resposta;
        }

        String bucketSeed = exigir(VAR_BUCKET_SEED);
        String bucketImagens = exigir(VAR_BUCKET_IMAGENS);
        String snpUrl = ambiente.apply(VAR_SNP_URL);
        if (snpUrl == null || snpUrl.isBlank()) {
            snpUrl = SNP_URL_PADRAO;
        }

        Map<String, String> csvs = new HashMap<>();
        for (String chave : listar(bucketSeed)) {
            if (chave.startsWith("relatorios/") || !chave.endsWith(".csv")) {
                continue;
            }
            int barra = chave.lastIndexOf('/');
            csvs.put(barra < 0 ? chave : chave.substring(barra + 1), ler(bucketSeed, chave));
        }
        List<String> imagens = new ArrayList<>();
        String descricoes = null;
        for (String chave : listar(bucketImagens)) {
            if (chave.equals(CHAVE_DESCRICOES) || chave.endsWith("/" + CHAVE_DESCRICOES)) {
                descricoes = ler(bucketImagens, chave);
            } else {
                imagens.add(chave);
            }
        }

        RelatorioCarga relatorio = new ImportadorSeed(destino, snpUrl).importar(csvs, imagens, descricoes);
        String chaveRelatorio = PREFIXO_RELATORIO + FORMATO_TIMESTAMP.format(clock.instant()) + ".json";
        s3.putObject(PutObjectRequest.builder().bucket(bucketSeed).key(chaveRelatorio)
                        .contentType("application/json").build(),
                RequestBody.fromString(Json.escrever(relatorio), StandardCharsets.UTF_8));

        // Log apenas com contagens (sem dados pessoais)
        Map<String, Object> resumo = new HashMap<>();
        resumo.put("reservas", relatorio.reservas());
        resumo.put("reservasLocalProprio", relatorio.reservasLocalProprio());
        resumo.put("rejeitadas", relatorio.arquivos().stream().mapToInt(RelatorioCarga.RelatorioArquivo::rejeitadas).sum());
        resumo.put("relatorio", chaveRelatorio);
        log.info(correlationId, "Seed concluído.", resumo);

        resposta.put("Data", Map.of("relatorio", chaveRelatorio));
        return resposta;
    }

    /** Evento S3 só dispara a carga quando algum objeto é CSV; demais eventos (custom resource) sempre. */
    static boolean contemCsv(Map<String, Object> evento) {
        if (evento == null || !(evento.get("Records") instanceof List<?> registros)) {
            return true;
        }
        for (Object registro : registros) {
            if (registro instanceof Map<?, ?> r
                    && r.get("s3") instanceof Map<?, ?> s3
                    && s3.get("object") instanceof Map<?, ?> objeto
                    && objeto.get("key") instanceof String chave
                    && chave.endsWith(".csv")
                    && !chave.startsWith("relatorios/")) {
                return true;
            }
        }
        return false;
    }

    private List<String> listar(String bucket) {
        List<String> chaves = new ArrayList<>();
        ListObjectsV2Request pedido = ListObjectsV2Request.builder().bucket(bucket).build();
        for (S3Object objeto : s3.listObjectsV2Paginator(pedido).contents()) {
            chaves.add(objeto.key());
        }
        return chaves;
    }

    private String ler(String bucket, String chave) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(chave).build())
                    .asString(StandardCharsets.UTF_8);
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    private String exigir(String variavel) {
        String valor = ambiente.apply(variavel);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + variavel + " não definida.");
        }
        return valor.trim();
    }
}
