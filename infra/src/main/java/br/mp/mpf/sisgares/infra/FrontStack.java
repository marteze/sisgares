package br.mp.mpf.sisgares.infra;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import software.amazon.awscdk.Aws;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CustomResource;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.ICertificate;
import software.amazon.awscdk.services.cloudfront.AccessLevel;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.CachePolicy;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ErrorResponse;
import software.amazon.awscdk.services.cloudfront.HttpVersion;
import software.amazon.awscdk.services.cloudfront.OriginProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.OriginRequestPolicy;
import software.amazon.awscdk.services.cloudfront.OriginSslPolicy;
import software.amazon.awscdk.services.cloudfront.PriceClass;
import software.amazon.awscdk.services.cloudfront.S3OriginAccessControl;
import software.amazon.awscdk.services.cloudfront.SecurityPolicyProtocol;
import software.amazon.awscdk.services.cloudfront.Signing;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
// No Java o módulo aws_cloudfront_origins fica em services.cloudfront.origins
import software.amazon.awscdk.services.cloudfront.origins.HttpOrigin;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOriginWithOACProps;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.amazon.awscdk.services.ses.EmailIdentity;
import software.amazon.awscdk.services.ses.Identity;
import software.amazon.awscdk.services.wafv2.CfnWebACL;
import software.amazon.awscdk.customresources.Provider;
import software.constructs.Construct;

/**
 * Stack de borda e carga inicial: CloudFront com OAC, WAF, envio do SPA, de {@code data/} e de
 * {@code imagens/}, recurso customizado que aciona o Importador_Seed e identidade SES
 * (Req. 4.1, 4.2, 4.13, 6.10, 6.11, 6.13, 23.1).
 *
 * <p>Decisões de projeto:
 * <ul>
 *   <li><b>OAC entre stacks</b>: o bucket do frontend e a CMK pertencem a outras stacks. Se o
 *       {@code S3BucketOrigin} alterasse a bucket policy com o ARN exato da distribuição, a
 *       DadosStack passaria a depender da FrontStack (ciclo). Por isso a origem usa uma referência
 *       importada do bucket (o CDK não altera a policy) e a permissão é adicionada explicitamente:
 *       {@code s3:GetObject} e {@code kms:Decrypt} para {@code cloudfront.amazonaws.com}, condicionados
 *       a {@code AWS:SourceArn} = distribuições desta conta. Nenhum acesso público é aberto
 *       (Block Public Access continua ativo).</li>
 *   <li><b>TLS 1.2+</b>: {@code minimumProtocolVersion TLS_V1_2_2021} só vale com certificado
 *       próprio. Com os contextos {@code certificadoArn} (ACM em us-east-1) e {@code dominioFrontend}
 *       o domínio é configurado; sem eles, o domínio padrão {@code *.cloudfront.net} usa a política
 *       da AWS para esse certificado. A origem da API é acessada somente via HTTPS com TLS 1.2.</li>
 *   <li><b>WAF</b>: Web ACL de escopo {@code CLOUDFRONT} (exige us-east-1) com
 *       AWSManagedRulesCommonRuleSet, AWSManagedRulesKnownBadInputsRuleSet e limite de 1000
 *       requisições por IP em janela de 5 minutos. O WAF regional <b>não</b> pode ser associado a
 *       HTTP API (API Gateway v2), somente a REST API. A proteção da API fica, portanto, no
 *       CloudFront (behavior {@code /api/*} passa pela mesma Web ACL) e no throttling do stage
 *       (50 req/s, rajada 100, definido na ApiStack). O endpoint {@code execute-api} continua
 *       acessível diretamente, mas exige JWT do Cognito em todas as rotas.</li>
 *   <li><b>SPA</b>: respostas 403/404 viram {@code /index.html} com status 200. Essa regra é da
 *       distribuição inteira e também se aplica ao behavior {@code /api/*}; o frontend deve tratar
 *       a API por código de status 401/422/409 e não depender de 403/404 JSON vindos pelo CloudFront.</li>
 *   <li><b>Seed</b>: o recurso customizado roda a cada deploy (propriedade {@code hashExecucao}
 *       aleatória) depois dos envios de {@code data/} e {@code imagens/}. A carga é idempotente
 *       (Req. 4.2) e a remoção da stack não apaga dados.</li>
 *   <li><b>SES</b>: a {@link EmailIdentity} só é criada com o contexto {@code sesIdentidade}
 *       (e-mail ou domínio), o mesmo usado pelo Notificador na EventosStack.</li>
 * </ul>
 */
public class FrontStack extends Stack {

    private static final String VERSAO_ARTEFATO = "0.1.0-SNAPSHOT";
    private static final String JAR_SEED = "../backend/seed/target/seed-" + VERSAO_ARTEFATO + ".jar";
    private static final String DIRETORIO_FRONTEND = "../frontend/dist/sisgares/browser";
    private static final String DIRETORIO_DADOS = "../data";
    private static final String DIRETORIO_IMAGENS = "../imagens";
    private static final String NOME_SEED = "sisgares-seed";
    private static final int MEMORIA_SEED_MB = 1024;
    private static final int TIMEOUT_SEED_MINUTOS = 10;
    private static final int MEMORIA_DEPLOYMENT_MB = 512;
    /** Limite do WAF: 1000 requisições por IP na janela padrão de 5 minutos (Req. 6.13). */
    private static final int LIMITE_TAXA_IP = 1000;

    private final Distribution distribuicao;
    private final CfnWebACL webAcl;

    public FrontStack(final Construct escopo, final String id, final StackProps props,
                      final IKey chave, final IBucket bucketFrontend, final IBucket bucketSeed,
                      final IBucket bucketImagens, final ITable tabela, final HttpApi api) {
        super(escopo, id, props);
        Objects.requireNonNull(chave, "a CMK é obrigatória");
        Objects.requireNonNull(bucketFrontend, "o bucket do frontend é obrigatório");
        Objects.requireNonNull(bucketSeed, "o bucket de seed é obrigatório");
        Objects.requireNonNull(bucketImagens, "o bucket de imagens é obrigatório");
        Objects.requireNonNull(tabela, "a tabela é obrigatória");
        Objects.requireNonNull(api, "a HTTP API é obrigatória");

        // ---------- WAF (escopo CLOUDFRONT, us-east-1) ----------
        this.webAcl = CfnWebACL.Builder.create(this, "WebAcl")
                .name("sisgares-cloudfront")
                .scope("CLOUDFRONT")
                .defaultAction(CfnWebACL.DefaultActionProperty.builder()
                        .allow(CfnWebACL.AllowActionProperty.builder().build())
                        .build())
                .visibilityConfig(visibilidade("sisgares-cloudfront"))
                .rules(List.of(
                        regraGerenciada("AWSManagedRulesCommonRuleSet", 0),
                        regraGerenciada("AWSManagedRulesKnownBadInputsRuleSet", 1),
                        CfnWebACL.RuleProperty.builder()
                                .name("LimitePorIp")
                                .priority(2)
                                .action(CfnWebACL.RuleActionProperty.builder()
                                        .block(CfnWebACL.BlockActionProperty.builder().build())
                                        .build())
                                .statement(CfnWebACL.StatementProperty.builder()
                                        .rateBasedStatement(CfnWebACL.RateBasedStatementProperty.builder()
                                                .limit(LIMITE_TAXA_IP)
                                                .aggregateKeyType("IP")
                                                .build())
                                        .build())
                                .visibilityConfig(visibilidade("sisgares-limite-ip"))
                                .build()))
                .build();

        // ---------- Origens ----------
        // Referência importada: o CDK não altera a bucket policy da DadosStack (evita ciclo)
        IBucket frontendImportado = Bucket.fromBucketName(this, "BucketFrontendRef", bucketFrontend.getBucketName());
        S3OriginAccessControl oac = S3OriginAccessControl.Builder.create(this, "Oac")
                .description("OAC do SPA do SISGARES")
                .signing(Signing.SIGV4_ALWAYS)
                .build();
        var origemSpa = S3BucketOrigin.withOriginAccessControl(frontendImportado,
                S3BucketOriginWithOACProps.builder()
                        .originAccessControl(oac)
                        .originAccessLevels(List.of(AccessLevel.READ))
                        .build());

        // Leitura do SPA somente por distribuições CloudFront desta conta (OAC)
        String arnDistribuicoesConta = "arn:" + Aws.PARTITION + ":cloudfront::" + Aws.ACCOUNT_ID + ":distribution/*";
        bucketFrontend.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("PermitirLeituraCloudFrontOac")
                .principals(List.of(new ServicePrincipal("cloudfront.amazonaws.com")))
                .actions(List.of("s3:GetObject"))
                .resources(List.of(bucketFrontend.arnForObjects("*")))
                .conditions(Map.of("ArnLike", Map.of("AWS:SourceArn", arnDistribuicoesConta)))
                .build());
        // Em key policy o recurso "*" significa "esta própria chave"
        chave.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("PermitirCloudFrontDecifrarSpa")
                .principals(List.of(new ServicePrincipal("cloudfront.amazonaws.com")))
                .actions(List.of("kms:Decrypt"))
                .resources(List.of("*"))
                .conditions(Map.of("ArnLike", Map.of("AWS:SourceArn", arnDistribuicoesConta)))
                .build());

        // HTTP API: domínio execute-api, somente HTTPS com TLS 1.2
        String dominioApi = api.getApiId() + ".execute-api." + Stack.of(api).getRegion() + ".amazonaws.com";
        HttpOrigin origemApi = HttpOrigin.Builder.create(dominioApi)
                .protocolPolicy(OriginProtocolPolicy.HTTPS_ONLY)
                .originSslProtocols(List.of(OriginSslPolicy.TLS_V1_2))
                .build();

        // ---------- Distribuição ----------
        Object certificadoCtx = this.getNode().tryGetContext("certificadoArn");
        Object dominioCtx = this.getNode().tryGetContext("dominioFrontend");
        ICertificate certificado = null;
        List<String> dominios = null;
        if (certificadoCtx != null && dominioCtx != null) {
            certificado = Certificate.fromCertificateArn(this, "Certificado", certificadoCtx.toString());
            dominios = List.of(dominioCtx.toString());
        }

        this.distribuicao = Distribution.Builder.create(this, "Distribuicao")
                .comment("SISGARES - SPA e /api")
                .defaultRootObject("index.html")
                .minimumProtocolVersion(SecurityPolicyProtocol.TLS_V1_2_2021)
                .certificate(certificado)
                .domainNames(dominios)
                .httpVersion(HttpVersion.HTTP2_AND_3)
                .priceClass(PriceClass.PRICE_CLASS_100)
                .webAclId(this.webAcl.getAttrArn())
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(origemSpa)
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                        .cachePolicy(CachePolicy.CACHING_OPTIMIZED)
                        .compress(true)
                        .build())
                .additionalBehaviors(Map.of("/api/*", BehaviorOptions.builder()
                        .origin(origemApi)
                        .viewerProtocolPolicy(ViewerProtocolPolicy.HTTPS_ONLY)
                        .allowedMethods(AllowedMethods.ALLOW_ALL)
                        .cachePolicy(CachePolicy.CACHING_DISABLED)
                        .originRequestPolicy(OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER)
                        .build()))
                // Rotas do SPA: 403/404 do S3 devolvem index.html
                .errorResponses(List.of(
                        respostaSpa(403),
                        respostaSpa(404)))
                .build();

        // ---------- Envio do frontend ----------
        BucketDeployment.Builder.create(this, "DeployFrontend")
                .sources(List.of(Source.asset(DIRETORIO_FRONTEND)))
                .destinationBucket(bucketFrontend)
                .distribution(this.distribuicao)
                .distributionPaths(List.of("/*"))
                .memoryLimit(MEMORIA_DEPLOYMENT_MB)
                .build();

        // ---------- Envio de data/ e imagens/ (Req. 4.1, 4.13) ----------
        // prune(false): preserva relatorios/ no seed e imagens enviadas pelo catálogo (Req. 6.12)
        BucketDeployment deployDados = BucketDeployment.Builder.create(this, "DeployDados")
                .sources(List.of(Source.asset(DIRETORIO_DADOS)))
                .destinationBucket(bucketSeed)
                .prune(false)
                .memoryLimit(MEMORIA_DEPLOYMENT_MB)
                .build();
        BucketDeployment deployImagens = BucketDeployment.Builder.create(this, "DeployImagens")
                .sources(List.of(Source.asset(DIRETORIO_IMAGENS)))
                .destinationBucket(bucketImagens)
                .prune(false)
                .memoryLimit(MEMORIA_DEPLOYMENT_MB)
                .build();

        // ---------- Importador_Seed (Req. 4.2) ----------
        Function seed = criarLambdaSeed(chave, bucketSeed, bucketImagens, tabela);
        Provider provedor = Provider.Builder.create(this, "ProvedorSeed")
                .onEventHandler(seed)
                .build();
        CustomResource recursoSeed = CustomResource.Builder.create(this, "ExecucaoSeed")
                .serviceToken(provedor.getServiceToken())
                .resourceType("Custom::SisgaresSeed")
                // Valor novo a cada synth força Update e, portanto, nova carga a cada deploy
                .properties(Map.of("hashExecucao", UUID.randomUUID().toString()))
                .build();
        // A carga só roda depois que CSVs e imagens estiverem nos buckets
        recursoSeed.getNode().addDependency(deployDados, deployImagens);

        // ---------- SES (opcional) ----------
        Object identidadeCtx = this.getNode().tryGetContext("sesIdentidade");
        String identidadeSes = identidadeCtx == null ? "" : identidadeCtx.toString().trim();
        if (!identidadeSes.isEmpty()) {
            EmailIdentity.Builder.create(this, "IdentidadeSes")
                    .identity(identidadeSes.contains("@")
                            ? Identity.email(identidadeSes)
                            : Identity.domain(identidadeSes))
                    .build();
        }

        CfnOutput.Builder.create(this, "UrlCloudFront")
                .value("https://" + this.distribuicao.getDistributionDomainName())
                .build();
    }

    /**
     * Lambda do Importador_Seed com role exclusiva: leitura dos buckets seed e imagens, escrita
     * apenas em {@code relatorios/} no seed, leitura/escrita na tabela e uso da CMK.
     */
    private Function criarLambdaSeed(final IKey chave, final IBucket bucketSeed, final IBucket bucketImagens,
                                     final ITable tabela) {
        Role role = Role.Builder.create(this, "RoleSeed")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .description("Role mínima da Lambda " + NOME_SEED)
                .build();

        // Log group no prefixo /aws/lambda/sisgares-* já liberado na key policy (ApiStack)
        LogGroup logs = LogGroup.Builder.create(this, "LogsSeed")
                .logGroupName("/aws/lambda/" + NOME_SEED)
                .retention(RetentionDays.THREE_MONTHS)
                .encryptionKey(chave)
                // Somente para o ambiente de hackathon: em produção usar RemovalPolicy.RETAIN
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
        logs.grantWrite(role);

        Map<String, String> ambiente = new HashMap<>();
        ambiente.put("BUCKET_SEED", bucketSeed.getBucketName());
        ambiente.put("BUCKET_IMAGENS", bucketImagens.getBucketName());
        ambiente.put("TABELA_SISGARES", tabela.getTableName());
        // SNP_URL opcional; sem ele o Importador_Seed usa o endereço simulado padrão
        Object snpCtx = this.getNode().tryGetContext("snpUrl");
        if (snpCtx != null && !snpCtx.toString().isBlank()) {
            ambiente.put("SNP_URL", snpCtx.toString().trim());
        }

        Function funcao = Function.Builder.create(this, "FuncaoSeed")
                .functionName(NOME_SEED)
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.X86_64)
                .handler("br.mp.mpf.sisgares.seed.HandlerSeed::handleRequest")
                .code(Code.fromAsset(JAR_SEED))
                .memorySize(MEMORIA_SEED_MB)
                .timeout(Duration.minutes(TIMEOUT_SEED_MINUTOS))
                .tracing(Tracing.ACTIVE)
                .logGroup(logs)
                .role(role)
                .environment(ambiente)
                .build();

        // Leitura dos CSVs e imagens; escrita só do relatório; CMK para decifrar/cifrar
        bucketSeed.grantRead(role);
        bucketImagens.grantRead(role);
        bucketSeed.grantPut(role, "relatorios/*");
        tabela.grantReadWriteData(role);
        chave.grantEncryptDecrypt(role);
        return funcao;
    }

    /** Regra gerenciada da AWS sem override de ação. */
    private static CfnWebACL.RuleProperty regraGerenciada(final String nome, final int prioridade) {
        return CfnWebACL.RuleProperty.builder()
                .name(nome)
                .priority(prioridade)
                .overrideAction(CfnWebACL.OverrideActionProperty.builder()
                        .none(Map.of())
                        .build())
                .statement(CfnWebACL.StatementProperty.builder()
                        .managedRuleGroupStatement(CfnWebACL.ManagedRuleGroupStatementProperty.builder()
                                .vendorName("AWS")
                                .name(nome)
                                .build())
                        .build())
                .visibilityConfig(visibilidade(nome))
                .build();
    }

    /** Métricas no CloudWatch e amostragem de requisições para cada regra. */
    private static CfnWebACL.VisibilityConfigProperty visibilidade(final String metrica) {
        return CfnWebACL.VisibilityConfigProperty.builder()
                .cloudWatchMetricsEnabled(true)
                .sampledRequestsEnabled(true)
                .metricName(metrica)
                .build();
    }

    /** Resposta de erro do SPA: devolve index.html com 200 para o roteamento do Angular. */
    private static ErrorResponse respostaSpa(final int status) {
        return ErrorResponse.builder()
                .httpStatus(status)
                .responseHttpStatus(200)
                .responsePagePath("/index.html")
                .ttl(Duration.seconds(0))
                .build();
    }

    /** Distribuição CloudFront do SISGARES. */
    public Distribution getDistribuicao() {
        return distribuicao;
    }

    /** Web ACL do WAF associada ao CloudFront. */
    public CfnWebACL getWebAcl() {
        return webAcl;
    }
}
