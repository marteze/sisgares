package br.mp.mpf.sisgares.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;

/**
 * Ponto de entrada do app CDK do SISGARES (Req. 23.1, 23.2).
 *
 * <p>Autenticação: toda operação AWS deve usar o profile {@code hackaton}
 * (configurado em {@code cdk.json} via {@code "profile": "hackaton"}).
 * A região padrão é {@code us-east-1}; a conta vem de {@code CDK_DEFAULT_ACCOUNT}
 * resolvida pelo CLI a partir do profile.
 */
public final class SisgaresInfraApp {

    /** Região padrão da solução. */
    static final String REGIAO_PADRAO = "us-east-1";

    private SisgaresInfraApp() {
    }

    public static void main(final String[] args) {
        App app = new App();

        // Região: contexto "sisgares:region" > padrão us-east-1
        Object regiaoCtx = app.getNode().tryGetContext("sisgares:region");
        String regiao = regiaoCtx != null ? regiaoCtx.toString() : REGIAO_PADRAO;

        Environment ambiente = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(regiao)
                .build();
        StackProps props = StackProps.builder().env(ambiente).build();

        // A CMK criada na SegurancaStack é repassada à DadosStack pelo construtor
        SegurancaStack seguranca = new SegurancaStack(app, "SisgaresSegurancaStack", props);
        DadosStack dados = new DadosStack(app, "SisgaresDadosStack", props, seguranca.getChave());
        // A EventosStack recebe a CMK e a tabela; expõe o barramento via getBarramento()
        EventosStack eventos = new EventosStack(app, "SisgaresEventosStack", props,
                seguranca.getChave(), dados.getTabela());
        // A ApiStack recebe a CMK, a tabela, os buckets e o Cognito das stacks anteriores
        ApiStack api = new ApiStack(app, "SisgaresApiStack", props,
                seguranca.getChave(), dados.getTabela(), dados.getBucketImagens(),
                dados.getBucketExportacoes(), seguranca.getAutenticacao());
        // A FrontStack recebe a CMK, os buckets (frontend, seed, imagens), a tabela e a HTTP API
        FrontStack front = new FrontStack(app, "SisgaresFrontStack", props,
                seguranca.getChave(), dados.getBucketFrontend(), dados.getBucketSeed(),
                dados.getBucketImagens(), dados.getTabela(), api.getApi());

        // Ordem de implantação: segurança -> dados -> eventos -> api -> front
        dados.addDependency(seguranca);
        eventos.addDependency(seguranca);
        eventos.addDependency(dados);
        api.addDependency(seguranca);
        api.addDependency(dados);
        api.addDependency(eventos);
        front.addDependency(seguranca);
        front.addDependency(dados);
        front.addDependency(api);

        Tags.of(app).add("Projeto", "SISGARES");

        app.synth();
    }
}
