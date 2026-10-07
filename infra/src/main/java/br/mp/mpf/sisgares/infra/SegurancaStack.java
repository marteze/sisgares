package br.mp.mpf.sisgares.infra;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudtrail.Trail;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

/**
 * Stack de segurança: chave KMS (CMK) da solução e trilha do CloudTrail
 * (Req. 6.9, 6.10, 6.15, 23.1).
 *
 * <p>Inclui o Cognito ({@link AutenticacaoConstruct}). Verified Permissions/Cedar e WAF
 * serão incluídos em tarefas posteriores.
 */
public class SegurancaStack extends Stack {

    /** CMK usada por DynamoDB, S3, SQS e CloudTrail. */
    private final Key chave;
    /** Cognito (User Pool, grupos e App Client). */
    private final AutenticacaoConstruct autenticacao;

    public SegurancaStack(final Construct escopo, final String id, final StackProps props) {
        super(escopo, id, props);

        // CMK simétrica com rotação automática anual ativa
        this.chave = Key.Builder.create(this, "ChaveSisgares")
                .alias("alias/sisgares")
                .description("CMK do SISGARES para dados em repouso (DynamoDB, S3, SQS, CloudTrail)")
                .enableKeyRotation(true)
                // Somente para o ambiente de hackathon: em produção usar RemovalPolicy.RETAIN
                .removalPolicy(RemovalPolicy.DESTROY)
                .pendingWindow(Duration.days(7))
                .build();

        // Permite ao CloudTrail cifrar os logs com a CMK, restrito às trilhas desta conta.
        // Em key policy o recurso "*" significa "esta própria chave" (não é curinga de recursos).
        this.chave.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("PermitirCloudTrailCifrarLogs")
                .principals(List.of(new ServicePrincipal("cloudtrail.amazonaws.com")))
                .actions(List.of("kms:GenerateDataKey*"))
                .resources(List.of("*"))
                .conditions(Map.of("StringLike", Map.of(
                        "kms:EncryptionContext:aws:cloudtrail:arn",
                        "arn:aws:cloudtrail:*:" + this.getAccount() + ":trail/*")))
                .build());
        this.chave.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("PermitirCloudTrailDescreverChave")
                .principals(List.of(new ServicePrincipal("cloudtrail.amazonaws.com")))
                .actions(List.of("kms:DescribeKey"))
                .resources(List.of("*"))
                .build());

        // Bucket dedicado aos logs do CloudTrail (privado, somente TLS)
        Bucket bucketTrilha = Bucket.Builder.create(this, "BucketTrilha")
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .enforceSsl(true)
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .id("ExpirarLogsAntigos")
                        .expiration(Duration.days(365))
                        .build()))
                // Somente para o ambiente de hackathon: em produção usar RETAIN e sem autoDeleteObjects
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        // Trilha multirregião com validação de integridade dos arquivos de log
        Trail.Builder.create(this, "TrilhaSisgares")
                .bucket(bucketTrilha)
                .encryptionKey(this.chave)
                .isMultiRegionTrail(true)
                .includeGlobalServiceEvents(true)
                .enableFileValidation(true)
                .build();

        // Cognito: User Pool, grupos, App Client PKCE e Hosted UI (Req. 5.1, 5.2).
        // O authorizer JWT do API Gateway (401 para token inválido, Req. 5.4) será criado
        // na ApiStack, na tarefa 21.2, a partir de getAutenticacao().
        this.autenticacao = new AutenticacaoConstruct(this, "Autenticacao");
    }

    /** Recursos do Cognito, repassados à ApiStack para o authorizer JWT. */
    public AutenticacaoConstruct getAutenticacao() {
        return autenticacao;
    }

    /** CMK da solução, repassada às demais stacks pelo construtor. */
    public IKey getChave() {
        return chave;
    }
}
