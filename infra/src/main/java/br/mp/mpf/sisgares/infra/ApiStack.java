package br.mp.mpf.sisgares.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.aws_apigatewayv2_authorizers.HttpJwtAuthorizer;
import software.amazon.awscdk.aws_apigatewayv2_authorizers.HttpJwtAuthorizerProps;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.CfnStage;
import software.amazon.awscdk.services.apigatewayv2.CorsHttpMethod;
import software.amazon.awscdk.services.apigatewayv2.CorsPreflightOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.CfnFunction;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.verifiedpermissions.CfnPolicy;
import software.amazon.awscdk.services.verifiedpermissions.CfnPolicyStore;
import software.constructs.Construct;

/**
 * Stack da API: HTTP API (API Gateway v2) sob {@code /api}, Lambdas Java 21 por contexto
 * (catalogo, reservas, paineis, configuracao, assistente, exportacao) e Verified Permissions com as políticas Cedar
 * (Req. 2.1, 2.2, 6.1, 6.2, 6.11, 6.14, 6.18, 17, 18, 21.1).
 *
 * <p>Decisões de projeto:
 * <ul>
 *   <li><b>HTTP API em vez de REST API</b>: menor latência e custo, authorizer JWT nativo do
 *       Cognito ({@link HttpJwtAuthorizer}) que devolve 401 para token ausente/inválido antes de
 *       invocar a Lambda. HTTP API <b>não</b> possui validação por modelo; o JSON Schema de cada
 *       corpo é validado na própria Lambda (JSON malformado ou campo fora da whitelist → 400;
 *       violação de regra de negócio → 422), com o mesmo formato de erro de design.md.</li>
 *   <li><b>TLS 1.2+</b>: o endpoint padrão {@code execute-api} de HTTP API aceita somente TLS 1.2.
 *       Domínio customizado, se criado, deve usar {@code SecurityPolicy.TLS_1_2}.</li>
 *   <li><b>Throttling</b>: 50 req/s (taxa) e 100 (rajada) no stage padrão ({@code $default}).</li>
 *   <li><b>CORS</b>: somente a origem do contexto {@code origemFrontend} (domínio do CloudFront);
 *       padrão {@code http://localhost:4200} para desenvolvimento.</li>
 *   <li><b>SnapStart</b>: aplicado em versões publicadas (override no {@link CfnFunction}); a API
 *       integra o alias {@code atual}, que aponta para a versão corrente.</li>
 *   <li><b>Verified Permissions</b>: o schema em {@code cedar/schema.cedarschema} está no formato
 *       legível do Cedar, mas o {@code CfnPolicyStore} só aceita o schema em JSON
 *       ({@code cedarJson}). Para não manter dois arquivos, o policy store usa
 *       {@code validationSettings} OFF e o schema não é enviado; a coerência entre schema e
 *       políticas é verificada nos testes do Autorizador. Cada arquivo {@code .cedar} vira uma
 *       {@link CfnPolicy} estática lida do disco em tempo de synth.</li>
 *   <li><b>IAM mínimo</b>: uma role por Lambda; acesso à tabela via {@code grantReadData} ou
 *       {@code grantReadWriteData} conforme o contexto, CMK via grants e
 *       {@code verifiedpermissions:IsAuthorized} somente no ARN do policy store.</li>
 *   <li><b>Assistente (Req. 17)</b>: {@code bedrock:InvokeModel} somente no ARN do foundation model
 *       (contexto {@code modeloBedrock}, padrão {@value #MODELO_PADRAO}); com o contexto
 *       {@code guardrailId}, também {@code bedrock:ApplyGuardrail} somente no ARN do guardrail.</li>
 *   <li><b>Exportação (Req. 18)</b>: {@code s3:PutObject}/{@code s3:GetObject} somente em
 *       {@code exportacoes/*} do bucket de exportações, com uso da CMK (SSE-KMS).</li>
 *   <li><b>X-Ray (Req. 21.1)</b>: HTTP API (API Gateway v2) <b>não</b> tem rastreamento X-Ray
 *       nativo (só REST API tem). O rastreamento começa nas Lambdas integradas, todas com
 *       {@code Tracing.ACTIVE}; os access logs/métricas do stage cobrem a camada da API.</li>
 * </ul>
 * O authorizer em modo mock ({@code MODO_AUTH=mock}) nunca é configurado aqui.
 */
public class ApiStack extends Stack {

    /** Versão dos artefatos Maven do backend. */
    private static final String VERSAO_ARTEFATO = "0.1.0-SNAPSHOT";
    /** Origem padrão do CORS (desenvolvimento local do Angular). */
    private static final String ORIGEM_PADRAO = "http://localhost:4200";
    /** Diretório das políticas Cedar, relativo a {@code infra/} (onde o CDK roda). */
    private static final Path DIRETORIO_CEDAR = Path.of("cedar");
    private static final int MEMORIA_MB = 1024;
    private static final int TIMEOUT_SEGUNDOS = 15;
    private static final int THROTTLE_TAXA = 50;
    private static final int THROTTLE_RAJADA = 100;
    /** Barramento EventBridge criado pela EventosStack (referenciado por nome para evitar ciclo). */
    private static final String BARRAMENTO = "sisgares-bus";
    /** Modelo padrão do Assistente_Reserva no Bedrock. */
    private static final String MODELO_PADRAO = "amazon.nova-lite-v1:0";
    /** Versão padrão do guardrail quando o contexto {@code guardrailVersao} não é informado. */
    private static final String GUARDRAIL_VERSAO_PADRAO = "DRAFT";
    /** Prefixo dos objetos gerados pela exportação (ver ArmazenamentoS3 no backend). */
    private static final String PREFIXO_EXPORTACOES = "exportacoes/*";

    private final HttpApi api;
    private final CfnPolicyStore policyStore;

    public ApiStack(final Construct escopo, final String id, final StackProps props,
                    final IKey chave, final ITable tabela, final IBucket bucketImagens,
                    final IBucket bucketExportacoes, final AutenticacaoConstruct autenticacao) {
        super(escopo, id, props);
        Objects.requireNonNull(chave, "a CMK é obrigatória");
        Objects.requireNonNull(tabela, "a tabela é obrigatória");
        Objects.requireNonNull(bucketImagens, "o bucket de imagens é obrigatório");
        Objects.requireNonNull(bucketExportacoes, "o bucket de exportações é obrigatório");
        Objects.requireNonNull(autenticacao, "a autenticação é obrigatória");

        // Permite ao CloudWatch Logs usar a CMK apenas para log groups das Lambdas do SISGARES.
        // Em key policy o recurso "*" significa "esta própria chave" (não é curinga de recursos).
        Stack pilhaChave = Stack.of(chave);
        chave.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("PermitirLogsCifrarLambdasSisgares")
                .principals(List.of(new ServicePrincipal("logs." + pilhaChave.getRegion() + ".amazonaws.com")))
                .actions(List.of("kms:Encrypt", "kms:Decrypt", "kms:ReEncryptFrom", "kms:ReEncryptTo",
                        "kms:GenerateDataKey", "kms:GenerateDataKeyWithoutPlaintext", "kms:DescribeKey"))
                .resources(List.of("*"))
                .conditions(Map.of("ArnLike", Map.of(
                        "kms:EncryptionContext:aws:logs:arn",
                        "arn:aws:logs:" + pilhaChave.getRegion() + ":" + pilhaChave.getAccount()
                                + ":log-group:/aws/lambda/sisgares-*")))
                .build());

        // Verified Permissions: policy store + uma política por arquivo .cedar
        this.policyStore = CfnPolicyStore.Builder.create(this, "PolicyStore")
                .description("Políticas Cedar do SISGARES")
                .validationSettings(CfnPolicyStore.ValidationSettingsProperty.builder()
                        .mode("OFF")
                        .build())
                .build();
        criarPoliticasCedar();

        // Lambdas por contexto (escrita somente onde o contexto altera dados)
        // Catálogo recebe o nome do bucket de imagens de Disposição (Req. 6.12)
        Alias catalogo = criarLambda("catalogo", chave, tabela, true,
                Map.of("BUCKET_IMAGENS", bucketImagens.getBucketName()));
        Alias reservas = criarLambda("reservas", chave, tabela, true, Map.of());
        Alias paineis = criarLambda("paineis", chave, tabela, false, Map.of());
        Alias configuracao = criarLambda("configuracao", chave, tabela, true, Map.of());
        // Upload/leitura de imagens de Disposição (Req. 6.12) somente no catálogo.
        // O bucket usa SSE-KMS com a CMK: grantReadWrite concede também Encrypt/Decrypt/GenerateDataKey
        // na chave, e o catálogo (escrita=true) já recebe grantEncryptDecrypt na CMK.
        bucketImagens.grantReadWrite(catalogo);
        // Painéis apenas leem.

        // Assistente_Reserva (Req. 17): leitura da tabela e Bedrock restrito ao modelo/guardrail
        String modelo = contexto("modeloBedrock", MODELO_PADRAO);
        String guardrailId = contexto("guardrailId", "");
        Map<String, String> varsAssistente = new HashMap<>();
        varsAssistente.put("MODELO_BEDROCK", modelo);
        if (!guardrailId.isEmpty()) {
            varsAssistente.put("GUARDRAIL_ID", guardrailId);
            varsAssistente.put("GUARDRAIL_VERSAO", contexto("guardrailVersao", GUARDRAIL_VERSAO_PADRAO));
        }
        Alias assistente = criarLambda("assistente", chave, tabela, false, varsAssistente);
        assistente.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of("bedrock:InvokeModel"))
                .resources(List.of("arn:aws:bedrock:" + this.getRegion() + "::foundation-model/" + modelo))
                .build());
        if (!guardrailId.isEmpty()) {
            assistente.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                    .actions(List.of("bedrock:ApplyGuardrail"))
                    .resources(List.of("arn:aws:bedrock:" + this.getRegion() + ":" + this.getAccount()
                            + ":guardrail/" + guardrailId))
                    .build());
        }

        // Exportação (Req. 18): leitura da tabela e S3 somente no prefixo exportacoes/*
        Alias exportacao = criarLambda("exportacao", chave, tabela, false,
                Map.of("BUCKET_EXPORTACOES", bucketExportacoes.getBucketName()));
        exportacao.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of("s3:PutObject", "s3:GetObject"))
                .resources(List.of(bucketExportacoes.arnForObjects(PREFIXO_EXPORTACOES)))
                .build());
        // SSE-KMS: gravar exige GenerateDataKey; ler/URL pré-assinada exige Decrypt (já concedido)
        chave.grant(exportacao.getRole(), "kms:GenerateDataKey");

        // Authorizer JWT do Cognito: token ausente, expirado ou com audiência errada → 401
        String emissor = "https://cognito-idp." + Stack.of(autenticacao).getRegion() + ".amazonaws.com/"
                + autenticacao.getUserPool().getUserPoolId();
        HttpJwtAuthorizer autorizador = new HttpJwtAuthorizer("AutorizadorCognito", emissor,
                HttpJwtAuthorizerProps.builder()
                        .authorizerName("cognito-jwt")
                        .identitySource(List.of("$request.header.Authorization"))
                        .jwtAudience(List.of(autenticacao.getUserPoolClient().getUserPoolClientId()))
                        .build());

        Object origemCtx = this.getNode().tryGetContext("origemFrontend");
        String origem = origemCtx == null ? ORIGEM_PADRAO : origemCtx.toString();

        this.api = HttpApi.Builder.create(this, "ApiSisgares")
                .apiName("sisgares-api")
                .description("API REST do SISGARES sob /api")
                .defaultAuthorizer(autorizador)
                .corsPreflight(CorsPreflightOptions.builder()
                        .allowOrigins(List.of(origem))
                        .allowMethods(List.of(CorsHttpMethod.GET, CorsHttpMethod.POST, CorsHttpMethod.PUT,
                                CorsHttpMethod.PATCH, CorsHttpMethod.DELETE, CorsHttpMethod.OPTIONS))
                        .allowHeaders(List.of("Authorization", "Content-Type"))
                        .allowCredentials(false)
                        .maxAge(Duration.hours(1))
                        .build())
                .createDefaultStage(true)
                .build();

        // Throttling 50/100 no stage padrão ($default), via escape hatch do L1
        CfnStage stage = (CfnStage) this.api.getDefaultStage().getNode().getDefaultChild();
        stage.setDefaultRouteSettings(CfnStage.RouteSettingsProperty.builder()
                .throttlingRateLimit(THROTTLE_TAXA)
                .throttlingBurstLimit(THROTTLE_RAJADA)
                .build());

        // Rotas sob /api (contrato em docs/openapi.yaml); OPTIONS fica sem authorizer (preflight CORS)
        List<HttpMethod> metodos = List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.PATCH, HttpMethod.DELETE);
        adicionarRotas("/api/catalogo/{proxy+}", metodos, catalogo, "IntegracaoCatalogo");
        adicionarRotas("/api/reservas", List.of(HttpMethod.GET, HttpMethod.POST), reservas, "IntegracaoReservasRaiz");
        adicionarRotas("/api/reservas/{proxy+}", metodos, reservas, "IntegracaoReservas");
        adicionarRotas("/api/paineis/{proxy+}", List.of(HttpMethod.GET), paineis, "IntegracaoPaineis");
        adicionarRotas("/api/configuracao", List.of(HttpMethod.GET, HttpMethod.PUT), configuracao,
                "IntegracaoConfiguracao");
        adicionarRotas("/api/assistente/propostas", List.of(HttpMethod.POST), assistente, "IntegracaoAssistente");
        adicionarRotas("/api/exportacoes", List.of(HttpMethod.POST), exportacao, "IntegracaoExportacao");

        CfnOutput.Builder.create(this, "UrlApi").value(this.api.getApiEndpoint()).build();
        CfnOutput.Builder.create(this, "PolicyStoreId").value(this.policyStore.getAttrPolicyStoreId()).build();
    }

    /**
     * Cria a Lambda do módulo com role própria, X-Ray, log group cifrado (90 dias), SnapStart
     * em versões publicadas e alias {@code atual}.
     */
    private Alias criarLambda(final String modulo, final IKey chave, final ITable tabela, final boolean escrita,
                              final Map<String, String> variaveisExtras) {
        String sufixo = Character.toUpperCase(modulo.charAt(0)) + modulo.substring(1);
        String nome = "sisgares-" + modulo;

        // Role exclusiva; somente a política gerenciada de logs básicos (sem escrita em outros serviços)
        Role role = Role.Builder.create(this, "Role" + sufixo)
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .description("Role mínima da Lambda " + nome)
                .build();

        LogGroup logs = LogGroup.Builder.create(this, "Logs" + sufixo)
                .logGroupName("/aws/lambda/" + nome)
                .retention(RetentionDays.THREE_MONTHS)
                .encryptionKey(chave)
                // Somente para o ambiente de hackathon: em produção usar RemovalPolicy.RETAIN
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
        // Escrita de logs restrita ao próprio log group
        logs.grantWrite(role);

        Map<String, String> variaveis = new HashMap<>();
        variaveis.put("TABELA_SISGARES", tabela.getTableName());
        variaveis.put("POLICY_STORE_ID", this.policyStore.getAttrPolicyStoreId());
        variaveis.putAll(variaveisExtras);
        boolean publicaEventos = "reservas".equals(modulo);
        if (publicaEventos) {
            // Publicação dos eventos de reserva no barramento (Req. 2.4)
            variaveis.put("BARRAMENTO", BARRAMENTO);
        }

        Function funcao = Function.Builder.create(this, "Funcao" + sufixo)
                .functionName(nome)
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.X86_64)
                .handler("br.mp.mpf.sisgares." + modulo + ".Handler::handleRequest")
                .code(Code.fromAsset("../backend/" + modulo + "/target/" + modulo + "-" + VERSAO_ARTEFATO + ".jar"))
                .memorySize(MEMORIA_MB)
                .timeout(Duration.seconds(TIMEOUT_SEGUNDOS))
                .tracing(Tracing.ACTIVE)
                .logGroup(logs)
                .role(role)
                .environment(variaveis)
                .build();

        // SnapStart nas versões publicadas (escape hatch do L1)
        CfnFunction cfn = (CfnFunction) funcao.getNode().getDefaultChild();
        cfn.addPropertyOverride("SnapStart", Map.of("ApplyOn", "PublishedVersions"));

        // Dados: leitura ou leitura/escrita conforme o contexto; CMK para cifrar/decifrar
        if (escrita) {
            tabela.grantReadWriteData(role);
            chave.grantEncryptDecrypt(role);
        } else {
            tabela.grantReadData(role);
            chave.grantDecrypt(role);
        }
        // Autorização Cedar somente no policy store do SISGARES
        role.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("verifiedpermissions:IsAuthorized"))
                .resources(List.of(this.policyStore.getAttrArn()))
                .build());
        if (publicaEventos) {
            // PutEvents somente no ARN do sisgares-bus (construído por nome, sem referência à EventosStack)
            role.addToPolicy(PolicyStatement.Builder.create()
                    .actions(List.of("events:PutEvents"))
                    .resources(List.of("arn:aws:events:" + this.getRegion() + ":" + this.getAccount()
                            + ":event-bus/" + BARRAMENTO))
                    .build());
        }

        return Alias.Builder.create(this, "Alias" + sufixo)
                .aliasName("atual")
                .version(funcao.getCurrentVersion())
                .build();
    }

    /** Valor textual do contexto CDK ou o padrão quando ausente/vazio. */
    private String contexto(final String chave, final String padrao) {
        Object valor = this.getNode().tryGetContext(chave);
        String texto = valor == null ? "" : valor.toString().trim();
        return texto.isEmpty() ? padrao : texto;
    }

    /** Adiciona as rotas com o authorizer padrão integrando o alias da Lambda. */
    private void adicionarRotas(final String caminho, final List<HttpMethod> metodos, final Alias alvo,
                                final String idIntegracao) {
        this.api.addRoutes(AddRoutesOptions.builder()
                .path(caminho)
                .methods(metodos)
                .integration(new HttpLambdaIntegration(idIntegracao, alvo))
                .build());
    }

    /** Lê cada arquivo {@code .cedar} de {@code infra/cedar/} e cria uma política estática. */
    private void criarPoliticasCedar() {
        try (Stream<Path> arquivos = Files.list(DIRETORIO_CEDAR)) {
            List<Path> politicas = arquivos
                    .filter(p -> p.getFileName().toString().endsWith(".cedar"))
                    .sorted()
                    .toList();
            if (politicas.isEmpty()) {
                throw new IllegalStateException("Nenhuma política .cedar encontrada em " + DIRETORIO_CEDAR.toAbsolutePath());
            }
            for (Path arquivo : politicas) {
                String nomeArquivo = arquivo.getFileName().toString();
                String nome = nomeArquivo.substring(0, nomeArquivo.length() - ".cedar".length());
                String declaracao = Files.readString(arquivo, StandardCharsets.UTF_8);
                CfnPolicy.Builder.create(this, "Politica-" + nome)
                        .policyStoreId(this.policyStore.getAttrPolicyStoreId())
                        .definition(CfnPolicy.PolicyDefinitionProperty.builder()
                                .staticValue(CfnPolicy.StaticPolicyDefinitionProperty.builder()
                                        .description(nome)
                                        .statement(declaracao)
                                        .build())
                                .build())
                        .build();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler as políticas Cedar em " + DIRETORIO_CEDAR.toAbsolutePath(), e);
        }
    }

    /** HTTP API, usada pela FrontStack (WAF/CloudFront). */
    public HttpApi getApi() {
        return api;
    }

    /** Policy store do Verified Permissions. */
    public CfnPolicyStore getPolicyStore() {
        return policyStore;
    }
}
