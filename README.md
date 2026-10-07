# SISGARES — Sistema de Gestão de Reservas de Ambientes e Recursos

O SISGARES permite que as unidades do MPF solicitem e gerenciem reservas de ambientes (salas, auditórios), recursos (projetores, notebooks) e serviços. O sistema bloqueia conflitos de horário, inclusive entre ambiente pai e filho com Margem de 30 minutos, impede o uso de recursos limitados além do disponível, notifica os setores envolvidos por e-mail, gera pedidos no SNP simulado e oferece painéis por perfil.

Arquitetura serverless e orientada a eventos na AWS: Angular em S3 + CloudFront, API Gateway com Cognito, Lambdas Java 21, DynamoDB single-table, EventBridge + Step Functions, Verified Permissions (Cedar), Bedrock (assistente) e IaC em AWS CDK. Detalhes em `.kiro/specs/sisgares-reservas/design.md`.

## Situação atual

| Parte | Estado |
|---|---|
| Frontend (Angular) | Roda localmente com `npm start`. Sem backend, usa dados de demonstração em memória e login simulado. |
| Backend — `dominio` | Regras de negócio em Java puro (conflito, disponibilidade, status, grade, versões). |
| Backend — `comum-aws` | Adaptadores DynamoDB (chaves, repositórios) com os primeiros testes. |
| Backend — contextos (`catalogo`, `reservas`, `paineis`, `configuracao`, `eventos`, `seed`, `assistente`, `exportacao`) | Módulos criados; handlers ainda em implementação. |
| Infra (CDK) | `SisgaresSegurancaStack` (KMS, CloudTrail) e `SisgaresDadosStack` (tabela DynamoDB, buckets) definidas. `SisgaresEventosStack`, `SisgaresApiStack` e `SisgaresFrontStack` ainda vazias. |

Backend e infra exigem JDK 21 e Maven, que não estão instalados na máquina de desenvolvimento atual. Por isso os comandos Maven e CDK abaixo ainda não foram executados nela.

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
- JDK 21 e Maven 3.9+ (backend e infra).
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

Os dados vêm dos CSVs sintéticos de `data/` (Unidade_Macro "PR/CE"). A coluna "Local" diz se o passo já roda com `npm start` sem backend.

| # | Passo | Como fazer | Local |
|---|---|---|---|
| 1 | Login | Em `/login`, escolha Carla Solicitante. Na nuvem, entre pelo Hosted UI. | Sim |
| 2 | Painel | Veja suas reservas em `/painel/solicitante`. Saia e entre como Bruno Atendente para ver `/painel/atendente`. | Sim |
| 3 | Conflito bloqueado | Em `/reservas/nova`, escolha um ambiente já reservado e um período sobreposto ou a menos de 30 min. Aparece `RN5: o ambiente já está reservado neste período...` e o envio fica bloqueado. | Sim (verificação simplificada) |
| 4 | Reserva válida | Ajuste o período para um horário livre (30 min ou mais de folga) e salve. A reserva aparece na lista e no painel. | Sim (em memória) |
| 5 | E-mail na Caixa_Simulada | Como Ana Administradora, abra `/caixa-simulada` e veja o e-mail enviado aos setores envolvidos. | Não (tela em construção) |
| 6 | Pedido_SNP | No detalhe da reserva, confira o número e o link do Pedido_SNP para ambiente ou recurso com código de serviço. | Não (depende de `eventos`) |
| 7 | Alteração com destaque | Altere período ou recursos da reserva. O e-mail de alteração e o histórico destacam os campos alterados. | Parcial (alteração em memória; destaque depende do backend) |
| 8 | Cancelamento | Cancele a reserva. O status vira `CANCELADA`, o horário volta a ficar livre e os setores recebem a notificação. | Não (depende do backend) |
| 9 | Exportação | No painel do atendente, exporte as reservas filtradas. | Não (depende de `exportacao`) |
| 10 | Assistente | Em nova reserva, descreva o evento em texto livre. O assistente (Bedrock) propõe ambiente, disposição e recursos só dos catálogos enviados, para revisão antes de salvar. | Não (depende de `assistente`) |

## Deploy na AWS

Veja `infra/README.md`. Ele traz bootstrap, deploy, envio do seed e destroy, sempre com `--profile hackaton` e região `us-east-1`.

## Segurança e LGPD

- Só dados fictícios no repositório (domínio `exemplo.gov.br`).
- Nenhum segredo versionado: use SSM Parameter Store ou Secrets Manager.
- Logs sem dados pessoais (`MascaradorDados.mascarar`) e textos do usuário escapados em HTML e e-mail (`EscapadorHtml.escapar`).
- Dados cifrados em repouso com KMS e em trânsito com TLS.
