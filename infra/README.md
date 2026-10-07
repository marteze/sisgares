# Infra do SISGARES (AWS CDK em Java)

Todos os recursos AWS da solução são criados por este projeto. Ele usa sempre o profile `hackaton` (`cdk.json` → `"profile": "hackaton"`) e a região `us-east-1` (contexto `sisgares:region`).

## Stacks

Ordem de implantação: segurança → dados → eventos → api → front.

| Stack | Conteúdo | Estado |
|---|---|---|
| `SisgaresSegurancaStack` | CMK KMS, CloudTrail com bucket de logs, Cognito User Pool (cliente, domínio e 3 grupos) | Definida e sintetiza |
| `SisgaresDadosStack` | Tabela DynamoDB single-table (CMK), buckets `BucketFrontend`, `BucketSeed`, `BucketImagens` e `BucketExportacoes`, parâmetros SSM | Definida e sintetiza |
| `SisgaresEventosStack` | EventBridge, Step Functions, SQS (DLQ), SNS, 4 Lambdas, alarmes e dashboard do CloudWatch | Definida e sintetiza |
| `SisgaresApiStack` | HTTP API (API Gateway v2) com autorizador JWT do Cognito, 17 rotas, 6 Lambdas, Verified Permissions (`cedar/`, 7 políticas) | Definida e sintetiza |
| `SisgaresFrontStack` | CloudFront com OAC e WAF, envio do SPA, de `data/` e `imagens/`, recurso customizado do Importador_Seed, identidade SES opcional | Definida e sintetiza |

`npx -y aws-cdk@2.170.0 synth --quiet` gera os 5 templates em `cdk.out/` sem erros. Nenhum deploy foi feito.

## Pré-requisitos

- JDK 21 e Maven 3.9+, instalados via Homebrew. O `cdk.json` roda `mvn -e -q compile exec:java`. Antes dos comandos CDK, exporte:

  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  export PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH
  ```

- O synth empacota artefatos já gerados: os jars das Lambdas (`cd backend && mvn -q package -DskipTests`) e o build do frontend (`cd frontend && npm run build`, saída em `frontend/dist/sisgares/browser`). Gere os dois antes do synth.
- Execução local com SAM e DynamoDB Local: exige Docker e AWS SAM CLI, que não estão instalados na máquina atual. Veja `infra/local/README.md`.
- AWS CLI v2 e AWS CDK CLI (`npm install -g aws-cdk` ou `npx aws-cdk`).
- Profile `hackaton` com credenciais válidas. Nunca grave chaves em arquivos do projeto.

## 1. Conferir identidade (obrigatório antes de qualquer alteração)

```bash
aws sts get-caller-identity --profile hackaton
```

Se o comando falhar (credenciais ausentes ou expiradas), pare e renove as credenciais do profile `hackaton`. Não use outro profile.

## 2. Bootstrap (uma vez por conta e região)

```bash
cd infra
export AWS_PROFILE=hackaton
export AWS_REGION=us-east-1
cdk bootstrap --profile hackaton
```

## 3. Build do backend e deploy

```bash
aws sts get-caller-identity --profile hackaton   # obrigatório antes de qualquer deploy

# Empacota as Lambdas e o frontend
cd backend && mvn -q package -DskipTests && cd ..
cd frontend && npm run build -- --configuration nuvem && cd ..

cd infra
cdk synth --profile hackaton
cdk diff --all --profile hackaton
cdk deploy --all --profile hackaton
```

## 4. Envio do seed (CSVs e ícones)

O `cdk deploy` da `SisgaresFrontStack` já envia `data/` e `imagens/` e aciona o Importador_Seed por recurso customizado a cada deploy. Os comandos abaixo servem para reenviar manualmente.

Envie os 10 CSVs de `data/` para o bucket de seed e os ícones de `imagens/` para o bucket de imagens. Os nomes físicos dos buckets são gerados pelo CloudFormation:

```bash
# Execute a partir da raiz do repositório
BUCKET_SEED=$(aws cloudformation list-stack-resources \
  --stack-name SisgaresDadosStack --region us-east-1 --profile hackaton \
  --query "StackResourceSummaries[?ResourceType=='AWS::S3::Bucket' && starts_with(LogicalResourceId, 'BucketSeed')].PhysicalResourceId" \
  --output text)

BUCKET_IMAGENS=$(aws cloudformation list-stack-resources \
  --stack-name SisgaresDadosStack --region us-east-1 --profile hackaton \
  --query "StackResourceSummaries[?ResourceType=='AWS::S3::Bucket' && starts_with(LogicalResourceId, 'BucketImagens')].PhysicalResourceId" \
  --output text)

aws s3 sync data/ "s3://${BUCKET_SEED}/" --exclude "*" --include "*.csv" \
  --region us-east-1 --profile hackaton

aws s3 sync imagens/ "s3://${BUCKET_IMAGENS}/" --exclude "*.md" \
  --region us-east-1 --profile hackaton
```

Cada CSV gravado no bucket de seed aciona o Importador_Seed. Ele carrega os registros no DynamoDB preservando os IDs originais e é idempotente, então reenviar não duplica dados.

Para conferir o envio:

```bash
aws s3 ls "s3://${BUCKET_SEED}/" --region us-east-1 --profile hackaton
```

## 5. Frontend na nuvem

Troque os placeholders de `frontend/src/environments/environment.nuvem.ts` pelas saídas do deploy (domínio do Hosted UI, client ID público, URLs de retorno). Depois:

```bash
cd frontend
npm ci
npm run build -- --configuration nuvem
```

A publicação no `BucketFrontend` e a invalidação do CloudFront ficam com a `SisgaresFrontStack` (`BucketDeployment` de `frontend/dist/sisgares/browser`), no próximo `cdk deploy`.

## 6. Destroy: operação destrutiva

> ⚠️ Atenção: remove todos os recursos criados por este projeto, inclusive a tabela DynamoDB e todos os buckets com seus objetos (`autoDeleteObjects`). Os dados de reservas, e-mails da Caixa_Simulada e logs do CloudTrail são apagados sem possibilidade de recuperação. A CMK KMS entra em exclusão agendada de 7 dias. Execute só com confirmação explícita do responsável.

```bash
aws sts get-caller-identity --profile hackaton   # confirme a conta antes
cd infra
cdk destroy --all --profile hackaton             # o CDK pede confirmação por stack
```

Não use `--force` sem a confirmação acima. Para conferir que nada restou:

```bash
aws cloudformation list-stacks --region us-east-1 --profile hackaton \
  --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE \
  --query "StackSummaries[?starts_with(StackName, 'Sisgares')].StackName"
```

A stack `CDKToolkit` do bootstrap não é removida pelo `cdk destroy`. Se quiser removê-la, isso também é destrutivo e exige confirmação.
