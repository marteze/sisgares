package br.mp.mpf.sisgares.infra;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.AutoVerifiedAttrs;
import software.amazon.awscdk.services.cognito.CfnUserPoolGroup;
import software.amazon.awscdk.services.cognito.CognitoDomainOptions;
import software.amazon.awscdk.services.cognito.ICustomAttribute;
import software.amazon.awscdk.services.cognito.Mfa;
import software.amazon.awscdk.services.cognito.MfaSecondFactor;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.PasswordPolicy;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.StandardAttribute;
import software.amazon.awscdk.services.cognito.StandardAttributes;
import software.amazon.awscdk.services.cognito.StringAttribute;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolClientOptions;
import software.amazon.awscdk.services.cognito.UserPoolDomain;
import software.amazon.awscdk.services.cognito.UserPoolDomainOptions;
import software.constructs.Construct;

/**
 * Autenticação do SISGARES no Amazon Cognito (Req. 1.7, 2.2, 5.1, 5.2, 5.4).
 *
 * <ul>
 *   <li>User Pool sem auto-cadastro, login por e-mail, senha ≥ 12 com complexidade e MFA TOTP opcional</li>
 *   <li>Grupos Administrador, Setor_Atendente e Solicitante (claim {@code cognito:groups})</li>
 *   <li>App Client público (sem segredo) com authorization code + PKCE para a SPA</li>
 *   <li>Domínio do Hosted UI com prefixo definido pelo contexto {@code cognitoPrefixoDominio}</li>
 * </ul>
 *
 * <p>Contextos opcionais: {@code cognitoPrefixoDominio} (padrão {@code sisgares-hackaton}),
 * {@code cognitoUrlsCallback} e {@code cognitoUrlsLogout} (listas separadas por vírgula;
 * padrão {@code http://localhost:4200/...}).
 */
public class AutenticacaoConstruct extends Construct {

    /** Nomes dos grupos, iguais aos usados pelo frontend e pelas políticas Cedar. */
    public static final String GRUPO_ADMINISTRADOR = "Administrador";
    public static final String GRUPO_SETOR_ATENDENTE = "Setor_Atendente";
    public static final String GRUPO_SOLICITANTE = "Solicitante";

    private static final String PREFIXO_PADRAO = "sisgares-hackaton";
    private static final String CALLBACK_PADRAO = "http://localhost:4200/auth/callback";
    private static final String LOGOUT_PADRAO = "http://localhost:4200/login";

    private final UserPool userPool;
    private final UserPoolClient userPoolClient;
    private final UserPoolDomain dominio;

    public AutenticacaoConstruct(final Construct escopo, final String id) {
        super(escopo, id);

        this.userPool = UserPool.Builder.create(this, "UserPool")
                .userPoolName("sisgares-usuarios")
                // Usuários são provisionados pelo administrador (sem auto-cadastro)
                .selfSignUpEnabled(false)
                .signInAliases(SignInAliases.builder().email(true).build())
                .autoVerify(AutoVerifiedAttrs.builder().email(true).build())
                .standardAttributes(StandardAttributes.builder()
                        .email(StandardAttribute.builder().required(true).mutable(true).build())
                        .fullname(StandardAttribute.builder().required(false).mutable(true).build())
                        .build())
                // Unidade_Macro e Setor_Envolvido do usuário, usados pelo Autorizador (Cedar)
                .customAttributes(Map.<String, ICustomAttribute>of(
                        "unidade", StringAttribute.Builder.create().mutable(true).maxLen(64).build(),
                        "setor", StringAttribute.Builder.create().mutable(true).maxLen(64).build()))
                // Req. 5.2: mínimo de 12 caracteres com maiúscula, minúscula, número e símbolo
                .passwordPolicy(PasswordPolicy.builder()
                        .minLength(12)
                        .requireUppercase(true)
                        .requireLowercase(true)
                        .requireDigits(true)
                        .requireSymbols(true)
                        .tempPasswordValidity(Duration.days(3))
                        .build())
                // Req. 5.2: MFA opcional, somente por TOTP (sem SMS)
                .mfa(Mfa.OPTIONAL)
                .mfaSecondFactor(MfaSecondFactor.builder().otp(true).sms(false).build())
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                // Somente para o ambiente de hackathon: em produção usar RemovalPolicy.RETAIN
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Grupos de perfil (precedência menor = prioridade maior no token)
        criarGrupo("GrupoAdministrador", GRUPO_ADMINISTRADOR,
                "Administra cadastros e Configuração da própria Unidade_Macro", 1);
        criarGrupo("GrupoSetorAtendente", GRUPO_SETOR_ATENDENTE,
                "Atende reservas do Setor_Envolvido", 2);
        criarGrupo("GrupoSolicitante", GRUPO_SOLICITANTE,
                "Solicita e acompanha as próprias reservas", 3);

        // App Client público da SPA: authorization code sem segredo; o Cognito exige PKCE
        // (S256) quando o cliente envia code_challenge, o que o frontend sempre faz.
        this.userPoolClient = this.userPool.addClient("ClienteWeb", UserPoolClientOptions.builder()
                .userPoolClientName("sisgares-web")
                .generateSecret(false)
                .authFlows(AuthFlow.builder().userSrp(true).build())
                .oAuth(OAuthSettings.builder()
                        .flows(OAuthFlows.builder()
                                .authorizationCodeGrant(true)
                                .implicitCodeGrant(false)
                                .build())
                        .scopes(List.of(OAuthScope.OPENID, OAuthScope.EMAIL, OAuthScope.PROFILE))
                        .callbackUrls(lerLista("cognitoUrlsCallback", CALLBACK_PADRAO))
                        .logoutUrls(lerLista("cognitoUrlsLogout", LOGOUT_PADRAO))
                        .build())
                .preventUserExistenceErrors(true)
                .accessTokenValidity(Duration.hours(1))
                .idTokenValidity(Duration.hours(1))
                .refreshTokenValidity(Duration.hours(8))
                .enableTokenRevocation(true)
                .build());

        // Hosted UI com prefixo configurável por contexto (precisa ser único na região)
        Object prefixo = this.getNode().tryGetContext("cognitoPrefixoDominio");
        this.dominio = this.userPool.addDomain("DominioHostedUi", UserPoolDomainOptions.builder()
                .cognitoDomain(CognitoDomainOptions.builder()
                        .domainPrefix(prefixo == null ? PREFIXO_PADRAO : prefixo.toString())
                        .build())
                .build());
    }

    private void criarGrupo(String id, String nome, String descricao, int precedencia) {
        CfnUserPoolGroup.Builder.create(this, id)
                .userPoolId(this.userPool.getUserPoolId())
                .groupName(nome)
                .description(descricao)
                .precedence(precedencia)
                .build();
    }

    /** Lê uma lista separada por vírgula do contexto do CDK, com valor padrão. */
    private List<String> lerLista(String chave, String padrao) {
        Object valor = this.getNode().tryGetContext(chave);
        String texto = valor == null ? padrao : valor.toString();
        return Arrays.stream(texto.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** User Pool, usado pelo authorizer JWT da ApiStack (tarefa 21.2). */
    public UserPool getUserPool() {
        return userPool;
    }

    /** App Client da SPA; seu ID é a audiência aceita pelo authorizer. */
    public UserPoolClient getUserPoolClient() {
        return userPoolClient;
    }

    /** Domínio do Hosted UI. */
    public UserPoolDomain getDominio() {
        return dominio;
    }
}
