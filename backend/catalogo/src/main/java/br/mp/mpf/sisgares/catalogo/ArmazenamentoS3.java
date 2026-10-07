package br.mp.mpf.sisgares.catalogo;

import java.util.Objects;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/** Grava as imagens de Disposição no bucket privado {@code BUCKET_IMAGENS} (criptografia KMS). */
public final class ArmazenamentoS3 implements ArmazenamentoImagens {

    public static final String VAR_BUCKET = "BUCKET_IMAGENS";

    private final S3Client cliente;
    private final String bucket;

    public ArmazenamentoS3(S3Client cliente, String bucket) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
    }

    /** Cria a partir da variável {@code BUCKET_IMAGENS}; sem ela, o upload falha com erro interno. */
    public static ArmazenamentoImagens doAmbiente() {
        String bucket = System.getenv(VAR_BUCKET);
        if (bucket == null || bucket.isBlank()) {
            return (chave, conteudo, tipo) -> {
                throw new IllegalStateException("Variável " + VAR_BUCKET + " não configurada");
            };
        }
        return new ArmazenamentoS3(S3Client.create(), bucket);
    }

    @Override
    public void gravar(String chave, byte[] conteudo, String contentType) {
        cliente.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(chave)
                        .contentType(contentType)
                        .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                        .build(),
                RequestBody.fromBytes(conteudo));
    }
}
