# SISGARES — Sistema de Gestão de Reservas de Ambientes e Recursos

O SISGARES permite que as unidades do MPF solicitem e gerenciem reservas de ambientes (salas, auditórios), recursos (projetores, notebooks) e serviços. O sistema bloqueia conflitos de horário, inclusive entre ambiente pai e filho com Margem de 30 minutos, impede o uso de recursos limitados além do disponível, notifica os setores envolvidos por e-mail, gera pedidos no SNP simulado e oferece painéis por perfil.

Arquitetura serverless e orientada a eventos na AWS: Angular em S3 + CloudFront, API Gateway com Cognito, Lambdas Java 21, DynamoDB single-table, EventBridge + Step Functions, Verified Permissions (Cedar), Bedrock (assistente) e IaC em AWS CDK. Detalhes em `.kiro/specs/sisgares-reservas/design.md`.

## Situação atual

| Parte | Estado |
|---|---|
| Frontend (Angular) | Roda localmente com `npm start`. Sem backend, usa dados de demonstração em memória e login simulado. `npm run build`, `npm run lint` e `npm test -- --watch=false` passam (Vitest: 1 arquivo, 2 testes). |
| Backend (10 módulos Maven) | Todos implementados: `dominio`, `comum-aws`, `catalogo`, `reservas`, `paineis`, `configuracao`, `eventos`, `seed`, `assistente`, `exportacao`. `mvn -q verify` passa com 110 testes (JUnit 5 + jqwik), 0 falhas. Os módulos `dominio` e `seed` ainda não têm testes próprios; o `dominio` é exercitado pelos testes dos demais módulos. |
| Infra (CDK) | As 5 stacks (`SisgaresSegurancaStack`, `SisgaresDadosStack`, `SisgaresEventosStack`, `SisgaresApiStack`, `SisgaresFrontStack`) estão definidas e `cdk synth` gera os 5 templates. Nenhum deploy foi feito. |

Testes por módulo do backend (relatórios do surefire): `comum-aws` 29, `reservas` 23, `catalogo` 12, `eventos` 12, `assistente` 11, `paineis` 11, `exportacao` 10, `configuracao` 2.

### JDK 21 e Maven

JDK 21 (`openjdk@21`) e Maven estão instalados via Homebrew. Antes dos comandos Maven e CDK, exporte:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH
```

## Estrutura do repositório

```
backend/        Maven multimódulo (Java 21): dominio, comum-aws e contextos de Lambda
frontend/       Angular 22 + Angular Material
infra/          AWS CDK em Java e políticas Cedar (infra/cedar/)
data/           10 CSVs sintéticos usados no seed
imagens/        Ícones de disposição e de recurso (imagens/descricoes.md)
docs/           Arquitetura, fluxo de eventos e modelo DynamoDB
.kiro/          Specs, steering e hooks do Kiro
```

## Pré-requisitos

- Node.js 22 ou superior e npm (testado com Node 26).
- JDK 21 e Maven 3.9+ (backend e infra), via Homebrew com o `export` de `JAVA_HOME` acima.
- Docker e AWS SAM CLI, só para a execução local com SAM e DynamoDB Local. Não estão instalados na máquina de desenvolvimento atual.
- AWS CLI v2 com o profile `hackaton` configurado (região `us-east-1`).
- AWS CDK CLI (`npm install -g aws-cdk` ou `npx aws-cdk`).

## Execução local

### Frontend

```bash
cd frontend
npm ci
npm start
```

Acesse `http://localhost:4200`. O `npm start` usa a configuração padrão (`environment.ts`): login simulado (`modoAutenticacao: 'mock'`) e chamadas a `/api` encaminhadas para `http://localhost:3000` pelo `proxy.conf.json`. Se não houver backend nessa porta, as telas de painel e reservas usam os dados de demonstração.

Build para a nuvem (Cognito Hosted UI com PKCE): `npm run build -- --configuration nuvem`. Antes, troque os placeholders de `src/environments/environment.nuvem.ts` pelas saídas do deploy. O arquivo não guarda segredos (cliente público).

### Backend

```bash
cd backend
mvn -q verify
```

### Backend local com SAM e DynamoDB Local

Exige Docker e AWS SAM CLI, que não estão instalados na máquina atual; por isso esse fluxo ainda não foi executado. Passo a passo em `infra/local/README.md` (DynamoDB Local via `docker compose`, `criar-tabela.sh`, `seed-local.sh` e `sam local start-api` na porta 3000).

## Testes

```bash
# Frontend (Vitest)
cd frontend && npm test

# Lint do frontend
cd frontend && npm run lint

# Backend: JUnit 5 + jqwik, sem acesso a contas AWS
cd backend && mvn test
```

## Usuários de teste

No modo simulado, a tela de login lista um usuário fictício por perfil (`frontend/src/app/core/auth/perfis.ts`):

| Usuário | E-mail | Grupo | Painel inicial |
|---|---|---|---|
| Ana Administradora | `admin@exemplo.gov.br` | `Administrador` | `/painel/atendente` |
| Bruno Atendente | `atendente@exemplo.gov.br` | `Setor_Atendente` | `/painel/atendente` |
| Carla Solicitante | `solicitante@exemplo.gov.br` | `Solicitante` | `/painel/solicitante` |

Na nuvem, os mesmos perfis viram grupos do Cognito User Pool. Os usuários de teste da nuvem são criados no User Pool com senha temporária definida fora do repositório (nenhuma senha é versionada). O frontend só esconde opções: a autorização vale no backend (Cognito + Verified Permissions).

## Roteiro da demo

Na nuvem, os dados vêm dos CSVs sintéticos de `data/` (Unidade_Macro "PR/CE"). Localmente, só com `npm start` e sem backend, as telas usam os dados de demonstração em memória do frontend. A coluna "Local" diz se o passo roda assim; "Deploy" indica que o passo exige o backend implantado na AWS (ou a execução local com SAM, que exige Docker e SAM CLI).

| # | Passo | Como fazer | Local (dados de demonstração) |
|---|---|---|---|
| 1 | Login | Em `/login`, escolha Carla Solicitante. Na nuvem, entre pelo Hosted UI. | Sim (login simulado) |
| 2 | Painel | Veja suas reservas em `/painel/solicitante`. Saia e entre como Bruno Atendente para ver `/painel/atendente`. | Sim |
| 3 | Conflito bloqueado | Em `/reservas/nova`, escolha um ambiente já reservado e um período sobreposto ou a menos de 30 min. Aparece `RN5: o ambiente já está reservado neste período...` e o envio fica bloqueado. | Sim (verificação simplificada no frontend) |
| 4 | Reserva válida | Ajuste o período para um horário livre (30 min ou mais de folga) e salve. A reserva aparece na lista e no painel. | Sim (em memória) |
| 5 | E-mail na Caixa_Simulada | Como Ana Administradora, abra `/caixa-simulada` e veja o e-mail enviado aos setores envolvidos. | Parcial (mostra e-mails de exemplo fixos; e-mails reais da reserva exigem deploy) |
| 6 | Pedido_SNP | No detalhe da reserva, confira o número e o link do Pedido_SNP para ambiente ou recurso com código de serviço. | Parcial (a reserva `DEMO-1` traz um pedido fictício; geração real exige deploy) |
| 7 | Alteração com destaque | Altere período ou recursos da reserva. O e-mail de alteração e o histórico destacam os campos alterados. | Parcial (alteração em memória; destaque exige deploy) |
| 8 | Cancelamento | Cancele a reserva. O status vira `CANCELADA`, o horário volta a ficar livre e os setores recebem a notificação. | Parcial (cancelamento em memória; notificação exige deploy) |
| 9 | Exportação | No painel do atendente, exporte as reservas filtradas. | Parcial (CSV gerado no navegador com os dados de demonstração; PDF exige deploy) |
| 10 | Assistente | Em nova reserva, descreva o evento em texto livre. O assistente (Bedrock) propõe ambiente, disposição e recursos só dos catálogos enviados, para revisão antes de salvar. | Não (exige deploy: Bedrock) |

## Deploy na AWS

Antes de qualquer deploy, confirme a conta:

```bash
aws sts get-caller-identity --profile hackaton
```

Se falhar, renove as credenciais do profile `hackaton`; não use outro profile. Depois siga `infra/README.md` (bootstrap, deploy, envio do seed e destroy, sempre com `--profile hackaton` e região `us-east-1`). Nenhum deploy foi feito até agora.

## Segurança e LGPD

- Só dados fictícios no repositório (domínio `exemplo.gov.br`).
- Nenhum segredo versionado: use SSM Parameter Store ou Secrets Manager.
- Logs sem dados pessoais (`MascaradorDados.mascarar`) e textos do usuário escapados em HTML e e-mail (`EscapadorHtml.escapar`).
- Dados cifrados em repouso com KMS e em trânsito com TLS.
