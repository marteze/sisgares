package br.mp.mpf.sisgares.exportacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Grava a exportação no bucket privado {@code BUCKET_EXPORTACOES} e gera URL pré-assinada de
 * leitura válida por 5 minutos (Req. 17.3). A criptografia em repouso (KMS) é a padrão do bucket.
 */
public final class ArmazenamentoS3 implements Armazenamento {

    public static final String VAR_BUCKET = "BUCKET_EXPORTACOES";
    public static final Duration VALIDADE = Duration.ofMinutes(5);
    private static final DateTimeFormatter CARIMBO =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public ArmazenamentoS3(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = Objects.requireNonNull(s3, "s3");
        this.presigner = Objects.requireNonNull(presigner, "presigner");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + VAR_BUCKET + " não definida.");
        }
        this.bucket = bucket.trim();
    }

    /** Instância a partir das variáveis de ambiente do processo. */
    public static ArmazenamentoS3 doAmbiente() {
        return new ArmazenamentoS3(S3Client.create(), S3Presigner.create(), System.getenv(VAR_BUCKET));
    }

    @Override
    public UrlAssinada gravar(String chave, byte[] conteudo, String contentType, String nomeArquivo) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(chave)
                        .contentType(contentType)
                        .contentDisposition("attachment; filename=\"" + nomeArquivo + "\"")
                        .build(),
                RequestBody.fromBytes(conteudo));
        PresignedGetObjectRequest assinada = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(VALIDADE)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(chave).build())
                .build());
        return new UrlAssinada(assinada.url().toString(), assinada.expiration());
    }

    /**
     * Chave {@code exportacoes/<sub-hash>/<timestamp>.<ext>}: o {@code sub} entra só como hash
     * SHA-256 truncado, sem dados pessoais na chave.
     */
    public static String chave(String sub, Instant instante, String extensao) {
        return "exportacoes/" + hashSub(sub) + "/" + CARIMBO.format(instante) + "." + extensao;
    }

    /** Variante que lê o instante do relógio injetado. */
    public static String chave(String sub, Clock relogio, String extensao) {
        return chave(sub, relogio.instant(), extensao);
    }

    static String hashSub(String sub) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(
                    Objects.requireNonNull(sub, "sub").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
