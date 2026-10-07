# Proteção de dados pessoais (LGPD)

## Dados pessoais tratados

| Dado | Onde fica | Origem | Finalidade |
|---|---|---|---|
| Identificador do usuário (`sub` do Cognito) | `USER#<sub>`, atributo `solicitante` da reserva, GSI4 `SOLIC#<sub>` | Cognito | Vincular a reserva ao Solicitante e autorizar alterações/cancelamentos |
| Nome do Solicitante | Cognito (claims) e resposta `solicitanteNome` | Cognito | Identificar o responsável pela reserva para o atendimento |
| E-mail do usuário | Cognito | Cognito | Login e comunicação institucional |
| Perfil, Unidade_Macro e Setor_Envolvido | `USER#<sub>` e grupos do Cognito | Cognito / Administrador | Controle de acesso (Verified Permissions) |
| E-mail funcional do setor (`ENVO_EMAIL`) | `ENVO#<id>` | `dados-envolvido.csv` | Envio das notificações de reserva ao setor |
| Texto livre (finalidade, complemento) | `RESE#<id>/META` e versões | Solicitante | Descrever o evento; pode conter dados pessoais digitados pelo usuário |
| Notificações enviadas | `RESE#<id>/NOTI#...` | Notificador | Comprovar o envio aos setores envolvidos |

Não há CPF, telefone ou dado sensível no modelo. O repositório contém apenas dados fictícios (domínio
`exemplo.gov.br`).

## Exibição por perfil

| Perfil | O que vê |
|---|---|
| Solicitante | Suas reservas completas, com nome. Reservas de terceiros apenas como período ocupado, sem nome, finalidade ou outros dados pessoais |
| Setor_Atendente | Reservas que envolvem o seu Setor_Envolvido, com nome do Solicitante |
| Administrador | Todas as reservas da sua Unidade_Macro, com nome do Solicitante |

A regra é aplicada no backend (Verified Permissions e serviços de reserva e painel); o frontend só esconde
opções de menu. Eventos do EventBridge carregam somente ID do evento, `RESE_ID`, versão e tipo. O assistente
com Bedrock recebe apenas a descrição, a data atual e os catálogos, sem dados pessoais.

## Retenção

- Notificações: 90 dias, via TTL do DynamoDB (atributo `expiraEm`).
- Logs do CloudWatch: 90 dias (`RetentionDays.THREE_MONTHS` em cada Lambda).
- Reservas e versões: mantidas enquanto necessárias ao histórico de uso dos ambientes; recuperação por PITR.

## Mascaramento e segurança

- Logs estruturados em JSON com `correlationId` e IDs; textos passam por `MascaradorDados.mascarar`
  (`usuario@exemplo.gov.br` → `u***@exemplo.gov.br`, CPF → `***.***.***-**`, nome → `U*** E***`). Tokens nunca são registrados.
- Respostas de erro (`{erros, correlationId}`) não expõem stack trace, IDs internos ou dados pessoais.
- Todo texto livre inserido em HTML ou e-mail passa por `EscapadorHtml.escapar`.
- Dados em repouso cifrados com CMK KMS (DynamoDB, S3, SQS, CloudTrail) e em trânsito via TLS; buckets sem acesso público.
- IAM de privilégio mínimo por Lambda; CloudTrail registra o uso da chave e da API.
- O hook `verifica-segredos-pii` revisa cada arquivo salvo em busca de segredos e dados pessoais reais.
