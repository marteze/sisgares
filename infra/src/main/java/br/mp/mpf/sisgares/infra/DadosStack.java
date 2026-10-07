package br.mp.mpf.sisgares.infra;

import java.util.Objects;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableEncryption;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.ObjectOwnership;
import software.amazon.awscdk.services.ssm.ParameterTier;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

/**
 * Stack de dados: tabela single-table {@code sisgares} com GSI1–GSI5, buckets S3
 * e parâmetros SSM de configuração padrão (Req. 3.7, 3.8, 6.9, 6.12, 6.18, 23.1).
 *
 * <p>Modelo de chaves documentado em design.md (Data Models).
 */
public class DadosStack extends Stack {

    /** Quantidade de GSIs do modelo single-table (GSI1 a GSI5). */
    private static final int TOTAL_GSIS = 5;

    /** Valores padrão da Configuração (mesmos do Importador_Seed): 120 minutos e faixa 07:00–20:00. */
    private static final String ANTECEDENCIA_PADRAO_MINUTOS = "120";
    private static final String FAIXA_PADRAO = "07:00-20:00";

    private final Table tabela;
    private final Bucket bucketFrontend;
    private final Bucket bucketSeed;
    private final Bucket bucketImagens;
    private final Bucket bucketExportacoes;

    public DadosStack(final Construct escopo, final String id, final StackProps props, final IKey chave) {
        super(escopo, id, props);
        Objects.requireNonNull(chave, "a CMK da SegurancaStack é obrigatória");

        // Tabela single-table: PK/SK string, PITR, TTL expiraEm e criptografia com a CMK
        this.tabela = Table.Builder.create(this, "TabelaSisgares")
                .tableName("sisgares")
                .partitionKey(Attribute.builder().name("PK").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("SK").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .pointInTimeRecovery(true)
                .timeToLiveAttribute("expiraEm")
                .encryption(TableEncryption.CUSTOMER_MANAGED)
                .encryptionKey(chave)
                // Somente para o ambiente de hackathon: em produção usar RemovalPolicy.RETAIN
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // GSI1 (raiz/conflitos), GSI2 (envolvido), GSI3 (unidade), GSI4 (solicitante), GSI5 (recurso)
        for (int i = 1; i <= TOTAL_GSIS; i++) {
            this.tabela.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                    .indexName("GSI" + i)
                    .partitionKey(Attribute.builder().name("GSI" + i + "PK").type(AttributeType.STRING).build())
                    .sortKey(Attribute.builder().name("GSI" + i + "SK").type(AttributeType.STRING).build())
                    .projectionType(ProjectionType.ALL)
                    .build());
        }

        // Buckets privados, cifrados com a CMK e acessíveis somente via TLS
        this.bucketFrontend = criarBucket("BucketFrontend", chave, false);
        this.bucketSeed = criarBucket("BucketSeed", chave, true);
        this.bucketImagens = criarBucket("BucketImagens", chave, false);
        this.bucketExportacoes = criarBucket("BucketExportacoes", chave, false);

        // Parâmetros SSM com a Configuração padrão (sem segredos)
        StringParameter.Builder.create(this, "ParametroAntecedencia")
                .parameterName("/sisgares/configuracao/antecedencia-minutos")
                .description("Antecedência_Mínima padrão em minutos")
                .stringValue(ANTECEDENCIA_PADRAO_MINUTOS)
                .tier(ParameterTier.STANDARD)
                .build();
        StringParameter.Builder.create(this, "ParametroFaixaPadrao")
                .parameterName("/sisgares/configuracao/faixa-padrao")
                .description("Faixa_Horária global padrão (HH:mm-HH:mm)")
                .stringValue(FAIXA_PADRAO)
                .tier(ParameterTier.STANDARD)
                .build();
    }

    /** Cria bucket com SSE-KMS (CMK), Block Public Access e enforceSSL. */
    private Bucket criarBucket(final String id, final IKey chave, final boolean versionado) {
        return Bucket.Builder.create(this, id)
                .encryption(BucketEncryption.KMS)
                .encryptionKey(chave)
                .bucketKeyEnabled(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                .enforceSsl(true)
                .versioned(versionado)
                // Somente para o ambiente de hackathon: em produção usar RETAIN e sem autoDeleteObjects
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();
    }

    public Table getTabela() {
        return tabela;
    }

    public Bucket getBucketFrontend() {
        return bucketFrontend;
    }

    public Bucket getBucketSeed() {
        return bucketSeed;
    }

    public Bucket getBucketImagens() {
        return bucketImagens;
    }

    public Bucket getBucketExportacoes() {
        return bucketExportacoes;
    }
}
