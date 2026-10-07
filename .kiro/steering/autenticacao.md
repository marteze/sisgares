# Autenticação na AWS

## Profile obrigatório

- Todo provisionamento e operação de infraestrutura do projeto SISGARES na AWS deve usar o profile `hackaton`.
- Região padrão: `us-east-1` (já configurada no profile).
- Nunca use o profile `default`, `workshop` ou qualquer outro para recursos deste projeto.

## Como aplicar o profile

- AWS CLI: inclua `--profile hackaton` em todos os comandos.
  ```bash
  aws s3 ls --profile hackaton
  ```
- Ferramentas de IaC e SDKs (CDK, SAM, Terraform, boto3 etc.): exporte a variável antes de rodar.
  ```bash
  export AWS_PROFILE=hackaton
  ```
  - CDK: `cdk deploy --profile hackaton`
  - SAM: `sam deploy --profile hackaton`
  - Terraform:
    ```hcl
    provider "aws" {
      profile = "hackaton"
      region  = "us-east-1"
    }
    ```

## Verificação antes de provisionar

- Antes de qualquer criação, alteração ou remoção de recursos, confirme a conta e a identidade:
  ```bash
  aws sts get-caller-identity --profile hackaton
  ```
- Se o comando falhar (credenciais ausentes ou expiradas), pare e peça ao usuário para renovar as credenciais. Não tente usar outro profile como alternativa.

## Segurança

- Não grave access keys, secret keys ou session tokens em código, arquivos de configuração do projeto ou mensagens de commit.
- Não exiba o conteúdo de `~/.aws/credentials` nas respostas; referencie apenas o nome do profile.
- Operações destrutivas (`destroy`, `delete-stack`, remoção de buckets ou bancos) exigem confirmação explícita do usuário.
