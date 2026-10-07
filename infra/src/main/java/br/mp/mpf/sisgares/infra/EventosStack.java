package br.mp.mpf.sisgares.infra;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.MetricOptions;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.events.EventBus;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.events.Match;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.events.targets.SfnStateMachine;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.CfnFunction;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionUrl;
import software.amazon.awscdk.services.lambda.FunctionUrlAuthType;
import software.amazon.awscdk.services.lambda.FunctionUrlOptions;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.amazon.awscdk.services.stepfunctions.Chain;
import software.amazon.awscdk.services.stepfunctions.CatchProps;
import software.amazon.awscdk.services.stepfunctions.DefinitionBody;
import software.amazon.awscdk.services.stepfunctions.Errors;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.Parallel;
import software.amazon.awscdk.services.stepfunctions.RetryProps;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.StateMachineType;
import software.amazon.awscdk.services.stepfunctions.Succeed;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.amazon.awscdk.services.stepfunctions.tasks.SqsSendMessage;
import software.constructs.Construct;

/**
 * Stack de eventos: barramento EventBridge {@code sisgares-bus}, regra {@code Reserva*},
 * Step Functions {@code Fluxo_Pós_Reserva} com ramos paralelos Notificador e Cliente_SNP,
 * SQS DLQ cifrada com a CMK, alarme da DLQ e agendamento do republicador de outbox
 * (Req. 2.4, 2.5, 2.7, 23.1).
 *
 * <p>Decisões de projeto:
 * <ul>
 *   <li><b>Retry</b>: cada ramo tenta a Lambda e, em falha, faz 3 novas tentativas com intervalo
 *       inicial de 2 s e backoff ×2 (2 s, 4 s, 8 s). Esgotadas, o {@code catch} do ramo envia o
 *       evento e o erro para a DLQ; o outro ramo não é cancelado.</li>
 *   <li><b>Idempotência</b>: o EventBridge e o republicador podem entregar o mesmo evento mais de
 *       uma vez; os handlers deduplicam por {@code eventoId} (Req. 2.6).</li>
 *   <li><b>SNP mock</b>: exposto por Function URL com {@code AWS_IAM}; somente a role do
 *       Cliente_SNP recebe {@code lambda:InvokeFunctionUrl}.</li>
 *   <li><b>Logs</b>: os log groups seguem o prefixo {@code /aws/lambda/sisgares-*}, já liberado
 *       na key policy da CMK (statement criado pela ApiStack).</li>
 *   <li><b>SES</b>: com o contexto {@code sesIdentidade} o Notificador recebe {@code ses:SendEmail}
 *       restrito ao ARN da identidade; sem ele, roda com {@code MODO_EMAIL=simulado} e sem SES.</li>
 * </ul>
 */
public class EventosStack extends Stack {

    private static final String VERSAO_ARTEFATO = "0.1.0-SNAPSHOT";
    private static final String JAR_EVENTOS = "../backend/eventos/target/eventos-" + VERSAO_ARTEFATO + ".jar";
    private static final String JAR_RESERVAS = "../backend/reservas/target/reservas-" + VERSAO_ARTEFATO + ".jar";
    private static final String NOME_BARRAMENTO = "sisgares-bus";
    private static final String ORIGEM_EVENTOS = "sisgares.reservas";
    private static final int MEMORIA_MB = 1024;
    private static final int TIMEOUT_SEGUNDOS = 30;
    /** Política de retry dos ramos (Req. 2.5). */
    private static final int TENTATIVAS = 3;
    private static final int INTERVALO_SEGUNDOS = 2;
    private static final double BACKOFF = 2.0;

    private final EventBus barramento;
    private final Queue dlq;
    private final StateMachine fluxoPosReserva;

    public EventosStack(final Construct escopo, final String id, final StackProps props,
                        final IKey chave, final ITable tabela) {
        super(escopo, id, props);
        Objects.requireNonNull(chave, "a CMK é obrigatória");
        Objects.requireNonNull(tabela, "a tabela é obrigatória");

        // Barramento próprio da solução
        this.barramento = EventBus.Builder.create(this, "Barramento")
                .eventBusName(NOME_BARRAMENTO)
                .build();

        // DLQ cifrada com a CMK; retenção máxima para análise e reprocessamento manual
        this.dlq = Queue.Builder.create(this, "DlqFluxoPosReserva")
                .queueName("sisgares-fluxo-pos-reserva-dlq")
                .encryption(QueueEncryption.KMS)
                .encryptionMasterKey(chave)
                .enforceSsl(true)
                .retentionPeriod(Duration.days(14))
                .build();

        // ---------- Lambdas ----------
        // SNP mock exposto por Function URL com autenticação IAM (SigV4)
        Function snpMock = criarLambda("SnpMock", "sisgares-snp-mock", JAR_EVENTOS,
                "br.mp.mpf.sisgares.eventos.snp.HandlerSnpMock::handleRequest", chave, Map.of()).funcao();
        FunctionUrl urlSnp = snpMock.addFunctionUrl(FunctionUrlOptions.builder()
                .authType(FunctionUrlAuthType.AWS_IAM)
                .build());

        // Notificador: SES real somente se a identidade vier do contexto
        Object identidadeCtx = this.getNode().tryGetContext("sesIdentidade");
        String identidadeSes = identidadeCtx == null ? "" : identidadeCtx.toString().trim();
        Map<String, String> ambienteNotificador = new HashMap<>();
        ambienteNotificador.put("TABELA_SISGARES", tabela.getTableName());
        if (identidadeSes.isEmpty()) {
            ambienteNotificador.put("MODO_EMAIL", "simulado");
        } else {
            ambienteNotificador.put("MODO_EMAIL", "ses");
            ambienteNotificador.put("SES_IDENTIDADE", identidadeSes);
        }
        LambdaCriada notificador = criarLambda("Notificador", "sisgares-notificador", JAR_EVENTOS,
                "br.mp.mpf.sisgares.eventos.notificador.HandlerNotificador::handleRequest", chave,
                ambienteNotificador);
        concederTabela(notificador.role(), tabela, chave);
        if (!identidadeSes.isEmpty()) {
            notificador.role().addToPolicy(PolicyStatement.Builder.create()
                    .actions(List.of("ses:SendEmail"))
                    .resources(List.of("arn:aws:ses:" + getRegion() + ":" + getAccount()
                            + ":identity/" + identidadeSes))
                    .build());
        }

        // Cliente_SNP: chama o mock via Function URL e registra o Pedido_SNP na tabela
        LambdaCriada clienteSnp = criarLambda("ClienteSnp", "sisgares-cliente-snp", JAR_EVENTOS,
                "br.mp.mpf.sisgares.eventos.snp.HandlerSnp::handleRequest", chave,
                Map.of("TABELA_SISGARES", tabela.getTableName(), "SNP_URL", urlSnp.getUrl()));
        concederTabela(clienteSnp.role(), tabela, chave);
        // lambda:InvokeFunctionUrl somente no ARN do SNP mock
        urlSnp.grantInvokeUrl(clienteSnp.role());

        // Republicador de outbox (módulo reservas): PutEvents só no barramento
        LambdaCriada republicador = criarLambda("Republicador", "sisgares-republicador-outbox", JAR_RESERVAS,
                "br.mp.mpf.sisgares.reservas.RepublicadorOutbox::handleRequest", chave,
                Map.of("TABELA_SISGARES", tabela.getTableName(), "BARRAMENTO", NOME_BARRAMENTO));
        concederTabela(republicador.role(), tabela, chave);
        republicador.role().addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("events:PutEvents"))
                .resources(List.of(this.barramento.getEventBusArn()))
                .build());

        // ---------- Step Functions ----------
        Parallel paralelo = Parallel.Builder.create(this, "RamosParalelos")
                .comment("Notificador e Cliente_SNP em paralelo")
                // A saída dos ramos não é usada; preserva o evento original
                .resultPath(JsonPath.DISCARD)
                .build();
        paralelo.branch(criarRamo("Notificador", notificador.alias()));
        paralelo.branch(criarRamo("ClienteSnp", clienteSnp.alias()));

        this.fluxoPosReserva = StateMachine.Builder.create(this, "FluxoPosReserva")
                .stateMachineName("sisgares-fluxo-pos-reserva")
                .stateMachineType(StateMachineType.STANDARD)
                .definitionBody(DefinitionBody.fromChainable(
                        Chain.start(paralelo).next(new Succeed(this, "Concluido"))))
                .tracingEnabled(true)
                .timeout(Duration.minutes(15))
                .build();
        // O role do Step Functions recebe automaticamente lambda:InvokeFunction nos aliases e
        // sqs:SendMessage + kms (GenerateDataKey/Decrypt) apenas na DLQ e na CMK

        // ---------- Regras do EventBridge ----------
        // Eventos de reserva (ReservaCriada/Alterada/Cancelada) disparam o fluxo
        Rule.Builder.create(this, "RegraReserva")
                .ruleName("sisgares-reserva")
                .eventBus(this.barramento)
                .eventPattern(EventPattern.builder()
                        .source(List.of(ORIGEM_EVENTOS))
                        .detailType(Match.prefix("Reserva"))
                        .build())
                .targets(List.of(SfnStateMachine.Builder.create(this.fluxoPosReserva)
                        .retryAttempts(TENTATIVAS)
                        .build()))
                .build();

        // Republicador do outbox a cada 1 minuto (barramento padrão para agendamentos)
        Rule.Builder.create(this, "AgendaRepublicador")
                .ruleName("sisgares-republicador-outbox")
                .schedule(Schedule.rate(Duration.minutes(1)))
                .targets(List.of(LambdaFunction.Builder.create(republicador.alias())
                        .retryAttempts(0)
                        .build()))
                .build();

        // ---------- Alarme da DLQ ----------
        // CloudWatch precisa usar a CMK para publicar no tópico cifrado
        chave.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("PermitirCloudWatchPublicarTopicosSisgares")
                .principals(List.of(new ServicePrincipal("cloudwatch.amazonaws.com")))
                .actions(List.of("kms:Decrypt", "kms:GenerateDataKey"))
                // Em key policy "*" significa a própria chave; restrito à conta da solução
                .resources(List.of("*"))
                .conditions(Map.of("StringEquals", Map.of("aws:SourceAccount", getAccount())))
                .build());
        Topic topicoAlarmes = Topic.Builder.create(this, "TopicoAlarmes")
                .topicName("sisgares-alarmes-eventos")
                .masterKey(chave)
                .enforceSsl(true)
                .build();
        Alarm alarmeDlq = Alarm.Builder.create(this, "AlarmeDlq")
                .alarmName("sisgares-fluxo-pos-reserva-dlq")
                .alarmDescription("Há mensagens na DLQ do Fluxo_Pós_Reserva: verifique Notificador/Cliente_SNP.")
                .metric(this.dlq.metricApproximateNumberOfMessagesVisible(MetricOptions.builder()
                        .period(Duration.minutes(1))
                        .statistic("Maximum")
                        .build()))
                .threshold(1)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build();
        alarmeDlq.addAlarmAction(new SnsAction(topicoAlarmes));

        CfnOutput.Builder.create(this, "ArnBarramento").value(this.barramento.getEventBusArn()).build();
        CfnOutput.Builder.create(this, "UrlSnpMock").value(urlSnp.getUrl()).build();
    }

    /** Par função/alias e role, usado para conceder permissões após a criação. */
    private record LambdaCriada(Function funcao, Alias alias, Role role) {
    }

    /**
     * Ramo do paralelo: invoca o alias da Lambda com retry 3x (2 s, ×2) e, esgotadas as
     * tentativas, envia o evento com o erro para a DLQ.
     */
    private Chain criarRamo(final String nome, final Alias alvo) {
        LambdaInvoke invocar = LambdaInvoke.Builder.create(this, "Invocar" + nome)
                .lambdaFunction(alvo)
                .payload(TaskInput.fromJsonPathAt("$"))
                // Desliga o retry padrão do CDK para valer somente a política do Req. 2.5
                .retryOnServiceExceptions(false)
                .resultPath(JsonPath.DISCARD)
                .build();
        invocar.addRetry(RetryProps.builder()
                .errors(List.of(Errors.ALL))
                .maxAttempts(TENTATIVAS)
                .interval(Duration.seconds(INTERVALO_SEGUNDOS))
                .backoffRate(BACKOFF)
                .build());

        // Mensagem da DLQ: ramo que falhou, evento original (entrada da execução) e erro final
        SqsSendMessage enviarDlq = SqsSendMessage.Builder.create(this, "EnviarDlq" + nome)
                .queue(this.dlq)
                .messageBody(TaskInput.fromObject(Map.of(
                        "ramo", nome,
                        "evento", JsonPath.stringAt("$$.Execution.Input"),
                        "erro", JsonPath.stringAt("$.erro"))))
                .resultPath(JsonPath.DISCARD)
                .build();

        // Esgotadas as tentativas, o erro vai para $.erro e o ramo segue para a DLQ
        invocar.addCatch(enviarDlq, CatchProps.builder()
                .errors(List.of(Errors.ALL))
                .resultPath("$.erro")
                .build());
        return Chain.start(invocar);
    }

    /** Leitura/escrita na tabela e uso da CMK. */
    private static void concederTabela(final Role role, final ITable tabela, final IKey chave) {
        tabela.grantReadWriteData(role);
        chave.grantEncryptDecrypt(role);
    }

    /**
     * Cria a Lambda no mesmo padrão da ApiStack: role exclusiva, X-Ray, log group cifrado
     * (90 dias), SnapStart em versões publicadas e alias {@code atual}.
     */
    private LambdaCriada criarLambda(final String sufixo, final String nome, final String jar,
                                     final String handler, final IKey chave,
                                     final Map<String, String> ambiente) {
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
        logs.grantWrite(role);

        Function funcao = Function.Builder.create(this, "Funcao" + sufixo)
                .functionName(nome)
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.X86_64)
                .handler(handler)
                .code(Code.fromAsset(jar))
                .memorySize(MEMORIA_MB)
                .timeout(Duration.seconds(TIMEOUT_SEGUNDOS))
                .tracing(Tracing.ACTIVE)
                .logGroup(logs)
                .role(role)
                .environment(ambiente)
                .build();

        // SnapStart nas versões publicadas (escape hatch do L1)
        CfnFunction cfn = (CfnFunction) funcao.getNode().getDefaultChild();
        cfn.addPropertyOverride("SnapStart", Map.of("ApplyOn", "PublishedVersions"));

        Alias alias = Alias.Builder.create(this, "Alias" + sufixo)
                .aliasName("atual")
                .version(funcao.getCurrentVersion())
                .build();
        return new LambdaCriada(funcao, alias, role);
    }

    /** Barramento {@code sisgares-bus}, usado pela Lambda reservas (PutEvents). */
    public EventBus getBarramento() {
        return barramento;
    }

    /** DLQ do Fluxo_Pós_Reserva. */
    public Queue getDlq() {
        return dlq;
    }

    /** Máquina de estados do Fluxo_Pós_Reserva. */
    public StateMachine getFluxoPosReserva() {
        return fluxoPosReserva;
    }
}
