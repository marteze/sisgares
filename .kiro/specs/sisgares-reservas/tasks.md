# Implementation Plan: SISGARES Reservas

## Overview

Implementação incremental em Java 21 (Maven multimódulo), Angular e AWS CDK (Java). Primeiro o esqueleto do monorepo, depois o Núcleo_Domínio puro com testes jqwik/JUnit, persistência DynamoDB com TransactWrite e outbox, Lambdas, seed, eventos, autenticação/autorização, frontend, infraestrutura segura, itens Desejáveis, Kiro e documentação. Cada bloco termina com checkpoint (`mvn test` e build).

## Tasks

- [x] 1. Criar o esqueleto do monorepo
  - [x] 1.1 Criar a estrutura de pastas e copiar os dados
    - Criar `frontend/`, `backend/`, `infra/`, `infra/cedar/`, `docs/`, `data/`; copiar os 10 CSVs de `dados/` para `data/` (manter `imagens/`)
    - Criar `.gitignore` (target, node_modules, cdk.out, .env)
    - _Requirements: 1.1, 4.1_
  - [x] 1.2 Criar o pom pai multimódulo em `backend/`
    - Módulos `dominio`, `comum-aws`, `catalogo`, `reservas`, `paineis`, `configuracao`, `eventos`, `seed`, `assistente`, `exportacao`
    - Java 21, JUnit 5, AssertJ, jqwik, Mockito, Testcontainers; versões fixas em `dependencyManagement`
    - maven-enforcer proibindo `software.amazon.*` no módulo `dominio`; dependências: contextos → `comum-aws` → `dominio`
    - _Requirements: 1.2, 1.3, 22.8_
  - [x] 1.3 Criar o projeto Angular em `frontend/`
    - Standalone components, Angular Material/CDK, roteamento, proxy `/api` para execução local, ESLint
    - _Requirements: 1.4_
  - [x] 1.4 Criar o projeto AWS CDK (Java) em `infra/`
    - App com stacks vazias `SegurancaStack`, `DadosStack`, `ApiStack`, `EventosStack`, `FrontStack`; `cdk.json` e pom próprio
    - _Requirements: 23.1, 23.2_

- [x] 2. Implementar modelos do Núcleo_Domínio e utilitários
  - [x] 2.1 Criar records de domínio e `Clock` injetável
    - `Reserva`, `Periodo` ([início, término)), `PeriodoOcupado`, `Solicitacao`, `SolicitacaoOcupada`, `Recurso`, `Ambiente`, `Configuracao` (antecedência, faixa global/por unidade), `Violacao`, `Conflito`, `Status`, `ContextoValidacao`
    - Constantes de códigos `RN*` e de fuso `America/Fortaleza`
    - _Requirements: 1.3, 1.5, 22.7_
  - [x] 2.2 Implementar `ArvoreAmbientes`
    - `relacionados(id)`, `raiz(id)`, `descendentes(id)`; validação de pai rejeitando o próprio Ambiente, descendentes e outra Unidade_Macro
    - _Requirements: 7.5, 10.3_
  - [ ]* 2.3 Escrever teste de propriedade da hierarquia
    - **Property 18: Hierarquia sem ciclo**
    - **Validates: Requirements 7.5**
  - [x] 2.4 Implementar `EscapadorHtml` (OWASP Encoder via abstração pura) e `MascaradorDados`
    - _Requirements: 6.6, 6.7_
  - [ ]* 2.5 Escrever teste de propriedade do escape HTML
    - **Property 14: Escape HTML**
    - **Validates: Requirements 6.6**
  - [ ]* 2.6 Escrever teste de propriedade do mascaramento (parte de log)
    - **Property 15: Mascaramento de logs e evento sem dados pessoais** (linha de log; o evento é coberto em 10.4)
    - **Validates: Requirements 2.3, 6.7**

- [x] 3. Implementar o DetectorConflito
  - [x] 3.1 Implementar `conflita(a, b)` e `conflitos(r, ocupados, arv)`
    - Regra `a.ini < b.fim+30m && b.ini < a.fim+30m`; RN5 no mesmo Ambiente, RN6 em Ambiente_Relacionado; ignora Local_Proprio, reservas canceladas e a própria Reserva
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.8_
  - [ ]* 3.2 Escrever teste de propriedade de simetria
    - **Property 1: Simetria do conflito**
    - **Validates: Requirements 10.1, 22.2**
  - [ ]* 3.3 Escrever teste de propriedade do limiar de 30 minutos
    - **Property 2: Limiar da margem de 30 minutos**
    - **Validates: Requirements 10.1, 10.2, 22.2**
  - [ ]* 3.4 Escrever teste de propriedade de propagação pai/filho
    - **Property 3: Propagação pai/filho**
    - **Validates: Requirements 10.3, 10.4, 22.2**

- [x] 4. Implementar CalculadoraDisponibilidade e CalculadoraStatus
  - [x] 4.1 Implementar `CalculadoraDisponibilidade.disponivel`
    - Pico de uso em intervalos que se cruzam, sem Margem
    - _Requirements: 11.3_
  - [x] 4.2 Implementar `CalculadoraStatus.status` (cancelada > prevista > transcorrida > em andamento)
    - _Requirements: 9.16_
  - [ ]* 4.3 Escrever teste de propriedade do status
    - **Property 6: Status exclusivo e total**
    - **Validates: Requirements 9.16, 22.2**

- [x] 5. Implementar o Validador_Reserva
  - [x] 5.1 Implementar validações RN1–RN4 acumulando violações
    - `RN1_SEM_PERIODO`, `RN1_TERMINO_INVALIDO` (aceita virada de dia), `RN2_*`, `RN3_FORA_FAIXA` (faixa da unidade substitui a global), `RN4_SEM_ANTECEDENCIA`
    - _Requirements: 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11, 9.12, 9.15_
  - [x] 5.2 Integrar RN5/RN6 (inclusive entre Períodos próprios), RN8, RN9 e RN12
    - Usa DetectorConflito e CalculadoraDisponibilidade; RN4 só para Períodos novos/alterados em reserva em andamento; `RN12_RESERVA_NAO_EDITAVEL`, `RN12_CONFIRMACAO_OBRIGATORIA`, `RN12_CANCELAMENTO_SEM_ANTECEDENCIA`
    - _Requirements: 9.13, 10.2, 10.3, 10.8, 11.2, 11.3, 11.5, 11.6, 11.7, 12.1, 12.2, 12.3, 12.6, 12.7_
  - [ ]* 5.3 Escrever teste de propriedade RN1–RN4
    - **Property 7: Validação RN1–RN4**
    - **Validates: Requirements 9.5, 9.7, 9.10, 9.11, 9.12, 9.15**
  - [ ]* 5.4 Escrever teste de propriedade da própria reserva ignorada
    - **Property 4: Própria reserva ignorada na alteração**
    - **Validates: Requirements 10.8**
  - [ ]* 5.5 Escrever teste de propriedade de soma ≤ disponibilidade
    - **Property 5: Soma ≤ disponibilidade**
    - **Validates: Requirements 11.3, 22.2**
  - [ ]* 5.6 Escrever testes de exemplo JUnit de RN1–RN13
    - Um caso por exemplo da tabela (14:00→13:00, 18:00→09:00 do dia seguinte, água e café sem complemento, faixa 18:00–21:00, antecedência 11:00/12:00, Auditório 11:20/11:30, Parte A × Completo, projetores 2/1, videoconferência VREC, status 10:00), com `Clock.fixed`
    - _Requirements: 22.1, 22.7_

- [x] 6. Implementar CalculadoraGrade e ComparadorVersoes
  - [x] 6.1 Implementar `CalculadoraGrade.calcular`
    - Linhas de 30 min na faixa aplicável, colunas 1–14 com/sem fins de semana; estados LIVRE, OCUPADO, MARGEM, ULTRAPASSADO, SEM_ANTECEDENCIA reutilizando DetectorConflito
    - _Requirements: 15.2, 15.3, 15.4, 15.6, 15.7, 15.8, 15.11_
  - [ ]* 6.2 Escrever teste de propriedade grade ⇔ validador
    - **Property 13: Grade ⇔ validador**
    - **Validates: Requirements 15.4, 15.6, 15.7, 15.8, 15.11**
  - [x] 6.3 Implementar `ComparadorVersoes.comparar` (campo, valor antigo, valor novo)
    - _Requirements: 13.4_
  - [ ]* 6.4 Escrever teste de propriedade das diferenças
    - **Property 12: Diferenças entre versões**
    - **Validates: Requirements 13.4**

- [x] 7. Implementar ResolvedorIcone e LeitorCsv
  - [x] 7.1 Implementar `ResolvedorIcone.resolver`
    - Correspondência por prefixo `<ref>_<desc>.<ext>` na categoria; 0 → `ICONE_NAO_ENCONTRADO`, >1 → `ICONE_AMBIGUO`, ambos com `indefinido_*`; preserva referência original
    - _Requirements: 4.14, 4.15, 4.16_
  - [ ]* 7.2 Escrever teste de propriedade da resolução de ícone
    - **Property 16: Resolução de ícone**
    - **Validates: Requirements 4.14, 4.15, 4.16, 22.11**
  - [ ]* 7.3 Escrever testes unitários do ResolvedorIcone (1, 0 e >1 correspondentes)
    - _Requirements: 22.11_
  - [x] 7.4 Implementar `LeitorCsv.ler`/`formatar` e mapeadores das 10 entidades
    - ID "14.207" → 14207; datas `dd/MM/yyyy HH:mm:ss`; rejeições com linha e motivo (`FORMATO_INVALIDO`); `*_ST_*` como "S"/"N"
    - _Requirements: 3.1, 3.6, 4.3, 4.4, 4.10_
  - [ ]* 7.5 Escrever teste de propriedade do round-trip do CSV
    - **Property 8: Round-trip do CSV**
    - **Validates: Requirements 4.3, 4.4, 17.4, 22.3**

- [ ] 8. Checkpoint - Núcleo_Domínio
  - Rodar `mvn -pl dominio test` em `backend/` sem acesso à AWS. Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implementar repositórios DynamoDB em `comum-aws`
  - [x] 9.1 Configurar cliente DynamoDB (SDK v2 enhanced) e convenções de chaves
    - Tabela `sisgares` single-table, PK/SK por entidade, GSI1–GSI5, nome da tabela por variável de ambiente
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8, 23.3_
  - [x] 9.2 Implementar repositórios de catálogo, configuração e usuário
    - Ambientes, EAMB/VREC, Disposições, Grupos, Recursos, EREC, Setores, Unidade_Macro, Configuração (cache de 60 s), Usuário
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 8.6_
  - [x] 9.3 Implementar repositório de reservas somente com Query
    - Reserva completa por `PK=RESE#id`; conflitos/grade por GSI1 com janela; disponibilidade por GSI5; painéis por GSI2/GSI3; minhas reservas por GSI4; expressões sempre parametrizadas
    - _Requirements: 3.7, 3.8, 3.9, 6.5_
  - [x] 9.4 Implementar gravação atômica com TransactWriteItems e outbox
    - META com condição de versão, PRES/SOLI/VERS, incremento condicional de `CTRL#AMBI#<raiz>` e `CTRL#RECU#<id>`, item `OUTBOX`; `TransactionCanceledException` → `RN7_CONFLITO_AO_SALVAR`
    - _Requirements: 2.8, 10.6, 10.7, 11.4, 12.4, 12.8_
  - [x] 9.5 Implementar logs JSON (Powertools) com mascaramento, `correlationId` e mapeamento de erros HTTP
    - Formato `{"erros":[...],"correlationId"}` para 400/401/403/404/409/422/500, sem stack trace
    - _Requirements: 1.6, 6.7, 6.8_
  - [ ]* 9.6 Escrever testes de integração com DynamoDB Local (Testcontainers)
    - RN7: duas gravações concorrentes, uma recebe 409; outbox gravado na mesma transação; verificação de ausência de Scan
    - _Requirements: 3.9, 10.6, 10.7, 22.5_

- [x] 10. Implementar a Lambda de reservas
  - [x] 10.1 Criar DTOs com Bean Validation e whitelist
    - `FAIL_ON_UNKNOWN_PROPERTIES` → 422 `CAMPO_NAO_PERMITIDO`; finalidade ≤ 2000, complemento ≤ 200, participantes 1–10000
    - _Requirements: 6.3, 6.4_
  - [x] 10.2 Implementar handler das rotas de `/api/reservas`
    - POST (201 + primeira versão), GET lista/detalhe, PUT (nova versão), cancelamento com `confirmado`, `verificar-periodo`, `versoes`; nome do Solicitante só para quem pode ver
    - _Requirements: 1.5, 9.14, 9.15, 10.5, 12.1, 12.4, 12.6, 12.8, 6.17_
  - [x] 10.3 Implementar publicação de Evento_Reserva e republicador de outbox
    - `PutEvents` após commit e remoção do `OUTBOX`; Lambda agendada (1 min) republica pendentes; evento só com IDs, versão e tipo
    - _Requirements: 2.3, 2.8_
  - [ ]* 10.4 Escrever teste de propriedade do evento sem dados pessoais
    - **Property 15: Mascaramento de logs e evento sem dados pessoais** (Evento_Reserva serializado)
    - **Validates: Requirements 2.3, 6.7**
  - [ ]* 10.5 Escrever testes do handler com dublês (EventBridge, repositórios)
    - Códigos 201/409/422, todas as violações na resposta, formato de erro, falha de PutEvents → outbox
    - _Requirements: 1.6, 2.8, 22.6_

- [x] 11. Implementar a Lambda de painéis
  - [x] 11.1 Implementar `GET /paineis/solicitante` usando CalculadoraGrade
    - Períodos de Ambientes_Relacionados; link só para dono/Admin; sem Dados_Pessoais aos demais
    - _Requirements: 15.1, 15.4, 15.5, 15.11, 6.17_
  - [x] 11.2 Implementar `GET /paineis/atendente` (GSI2 para Atendente, GSI3 para Admin)
    - Cards por data ordenados por início, Recursos, Pedidos_SNP, indicador de cancelada
    - _Requirements: 16.2, 16.3, 16.4, 16.5, 16.6_
  - [ ]* 11.3 Escrever testes dos handlers de painéis com repositórios em memória
    - _Requirements: 16.4, 16.5, 22.6_

- [x] 12. Implementar o Importador_Seed
  - [x] 12.1 Implementar a Lambda de seed
    - Lê CSVs do S3 com LeitorCsv; cria Unidade_Macro "PR/CE", Configuração padrão (120 min, 07:00–20:00, endpoint SNP), usuários fictícios (≥ 3 Solicitantes), códigos SNP em ao menos um EREC; SOLI_QTD vazio conforme limitado; reservas determinísticas sem conflito ou Local_Proprio; ícones via ResolvedorIcone e `alt` de `imagens/descricoes.md`; gravação idempotente; relatório JSON no S3
    - _Requirements: 4.2, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11, 4.12, 4.14, 4.15, 4.16, 4.17_
  - [ ]* 12.2 Escrever teste de propriedade da idempotência do seed
    - **Property 9: Idempotência do seed**
    - **Validates: Requirements 4.7, 4.12**
  - [ ]* 12.3 Escrever testes unitários do seed com os CSVs de `data/`
    - IDs com ponto de milhar, linhas rejeitadas no relatório, ícones ambíguos/não encontrados
    - _Requirements: 4.10, 4.11, 22.11_

- [ ] 13. Checkpoint - Backend de reservas, painéis e seed
  - Rodar `mvn test` e `mvn package` em `backend/`. Ensure all tests pass, ask the user if questions arise.

- [x] 14. Implementar o fluxo de eventos
  - [x] 14.1 Implementar o Notificador
    - Destinatários: união sem duplicatas de setores ativos de EAMB/EREC (lista alternativa ou ENVO_EMAIL); HTML com escape e destaque das diferenças via ComparadorVersoes; SES ou somente Caixa_Simulada (sandbox/local); TTL 90 dias; idempotência via `EVT#` com `attribute_not_exists`; registro de falha
    - _Requirements: 2.6, 6.6, 6.18, 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8_
  - [x] 14.2 Implementar o Cliente_SNP e a Lambda mock do SNP
    - Pedido por vínculo com código (na alteração, só vínculos novos); endpoint da Configuração; idempotência; registro de falha
    - _Requirements: 2.6, 14.1, 14.2, 14.3, 14.4, 14.5_
  - [ ]* 14.3 Escrever teste de propriedade da idempotência dos eventos
    - **Property 10: Idempotência dos eventos**
    - **Validates: Requirements 2.6**
  - [ ]* 14.4 Escrever teste de propriedade de destinatários e pedidos SNP
    - **Property 11: Destinatários e pedidos SNP**
    - **Validates: Requirements 13.1, 14.1, 14.2, 14.4**
  - [ ]* 14.5 Escrever testes de integração com DynamoDB Local
    - RN10 setores notificados, RN11 Pedido_SNP só com código, entrega duplicada de evento; SES como dublê
    - _Requirements: 22.5, 22.6_
  - [x] 14.6 Definir no CDK `EventosStack`
    - Barramento `sisgares-bus`, regra `Reserva*`, Step Functions com ramos paralelos (retry 3x, 2 s, ×2), SQS DLQ com KMS, agendamento do republicador
    - _Requirements: 2.4, 2.5, 2.7, 23.1_

- [ ] 15. Implementar autenticação Cognito e autorização Verified Permissions/Cedar
  - [x] 15.1 Escrever as políticas Cedar em `infra/cedar/` e o schema
    - Dono altera/cancela; Atendente consulta reservas do seu setor; Admin restrito à própria Unidade_Macro; cadastros e Configuração só Admin
    - _Requirements: 5.5, 5.7, 5.8, 5.9, 5.10_
  - [ ] 15.2 Implementar o Autorizador em `comum-aws` e aplicá-lo nos handlers de reservas e painéis
    - Chamada ao AVP `IsAuthorized` com principal (grupo, unidade, setor) e recurso; negação → 403 `ACESSO_NEGADO`
    - _Requirements: 5.5, 5.6_
  - [x] 15.3 Definir Cognito no CDK e mock local de JWT
    - User Pool com grupos, senha ≥ 12 com complexidade, MFA TOTP opcional; authorizer no API Gateway (401); emissor JWT fictício por perfil para modo local
    - _Requirements: 1.7, 2.2, 5.1, 5.2, 5.3, 5.4_
  - [ ]* 15.4 Escrever testes das políticas Cedar com cedar-java
    - Matriz perfil × dono/setor/unidade
    - _Requirements: 5.7, 5.8, 5.9, 5.10_

- [ ] 16. Checkpoint - Eventos e autorização
  - Rodar `mvn test` em `backend/` e `mvn package` + `cdk synth` em `infra/`. Ensure all tests pass, ask the user if questions arise.

- [x] 17. Implementar a base do frontend e o login
  - [x] 17.1 Implementar login, serviço de autenticação, interceptor JWT, guards por grupo e menu por perfil
    - Rota `/` redireciona ao painel do perfil; Cognito em nuvem e mock local
    - _Requirements: 5.1, 5.3, 20.7_
  - [ ]* 17.2 Escrever testes unitários dos guards e do redirecionamento
    - _Requirements: 20.7_

- [x] 18. Implementar o painel do Solicitante em grade
  - [x] 18.1 Criar `/painel/solicitante`
    - Filtros (Ambiente, data, colunas 1–14, fins de semana); estados por texto ("Margem de tolerância", "Horário ultrapassado", "Sem antecedência mínima", "Reservar às XX:XX"); link ao cadastro com Ambiente/data/início
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8, 15.9, 15.10, 20.6_
  - [ ]* 18.2 Escrever testes de componente da grade
    - _Requirements: 15.9, 15.10, 20.6_

- [x] 19. Implementar o formulário de reserva
  - [x] 19.1 Criar `/reservas`, `/reservas/nova` e `/reservas/:id`
    - Local_Proprio por padrão; Disposição com imagem e `alt`; Períodos com verificação de conflito ao concluir cada um; quantidade só para limitados; recursos agrupados por GREC_ORDEM e filtrados ao trocar Ambiente; cabeçalho com Solicitante, status, última alteração e links SNP; erros 422 anunciados (aria-live)
    - _Requirements: 7.10, 9.1, 9.2, 9.3, 10.5, 11.1, 11.8, 14.6, 20.5, 20.8, 20.9_
  - [x] 19.2 Implementar cancelamento com diálogo de confirmação acessível
    - _Requirements: 12.5_

- [x] 20. Implementar o painel do Atendente e a acessibilidade
  - [x] 20.1 Criar `/painel/atendente` com cards por data
    - Filtros, ordenação por início, texto "Cancelada", links para Reserva e Pedidos_SNP
    - _Requirements: 16.1, 16.2, 16.3, 16.6_
  - [x] 20.2 Aplicar responsividade (360–1920 px) e WCAG 2.1 AA/eMAG
    - Teclado e foco visível, contraste, rótulos visíveis, rolagem interna só nas grades
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5_
  - [ ]* 20.3 Escrever testes Playwright com axe-core em 360 e 1920 px
    - _Requirements: 20.1, 20.2, 20.3, 20.4_

- [ ] 21. Implementar a infraestrutura CDK com segurança
  - [x] 21.1 Implementar `SegurancaStack` e `DadosStack`
    - KMS CMK; tabela `sisgares` com GSI1–GSI5, PITR, TTL `expiraEm`; buckets (frontend, seed, imagens, exportações) com SSE-KMS e Block Public Access; CloudTrail; parâmetros SSM
    - _Requirements: 3.7, 3.8, 6.9, 6.10, 6.12, 6.15, 6.18, 23.1_
  - [x] 21.2 Implementar `ApiStack`
    - Lambdas Java 21 com SnapStart, X-Ray, logs com retenção de 90 dias, role IAM mínima por Lambda (sem `*` em escrita); API Gateway `/api` com modelos JSON Schema (400), throttling 50/100, CORS só do CloudFront, TLS 1.2+; Verified Permissions com as políticas de `infra/cedar/`
    - _Requirements: 2.1, 2.2, 6.1, 6.2, 6.11, 6.14, 6.18, 21.1_
  - [x] 21.3 Implementar `FrontStack` e envio de seed
    - CloudFront com OAC e TLS 1.2+; WAF com regras gerenciadas e 1000 req/5 min por IP no CloudFront e no API Gateway; BucketDeployment do frontend, de `data/` e de `imagens/`; custom resource que aciona o Importador_Seed; identidade SES
    - _Requirements: 4.1, 4.2, 4.13, 6.10, 6.11, 6.13, 23.1_
  - [ ]* 21.4 Escrever testes CDK assertions + cdk-nag
    - KMS em tabelas/buckets/filas, OAC, WAF, ausência de `*` em escrita, TLS
    - _Requirements: 6.1, 6.9, 6.10, 6.11, 6.13_
  - [ ] 21.5 Criar template SAM local e `docker-compose` com DynamoDB Local
    - _Requirements: 1.7_

- [ ] 22. Checkpoint - Frontend e infraestrutura
  - Rodar `mvn test` em `backend/`, `npm run build` e `npm test -- --watch=false` em `frontend/`, `cdk synth` em `infra/`. Ensure all tests pass, ask the user if questions arise.

- [ ] 23. Implementar cadastros e configuração (Desejável)
  - [ ] 23.1 Implementar a Lambda de catálogo
    - CRUD e inativação de Setor, Ambiente (pai via ArvoreAmbientes), Disposição (upload PNG/JPEG/SVG sanitizado ≤ 2 MB), Grupo e Recurso (limitado ≥ 1); vínculos EAMB/EREC/VREC; e-mails válidos; inativos fora das opções de reserva
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.11, 7.12, 7.13, 7.14_
  - [ ] 23.2 Implementar a Lambda de configuração
    - Antecedência 0–10080, faixas global/por unidade (mín < máx), URL do SNP `https://` (`http://` só local)
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_
  - [ ]* 23.3 Escrever testes dos handlers de catálogo e configuração
    - _Requirements: 7.3, 7.5, 7.8, 7.12, 8.3, 8.5_
  - [ ] 23.4 Criar telas `/cadastros/*` e `/configuracao` no frontend
    - _Requirements: 7.1, 7.10, 8.1, 8.2, 8.4_

- [ ] 24. Implementar assistente Bedrock, exportações e observabilidade (Desejável)
  - [ ] 24.1 Implementar a Lambda do Assistente_Reserva
    - Prompt só com descrição, data e catálogos permitidos; Guardrails; modelo por variável de ambiente; saneamento por JSON Schema e catálogo com `camposNaoPreenchidos`; timeout 10 s → 503
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.6, 18.7, 18.8_
  - [ ]* 24.2 Escrever teste de propriedade da saída do assistente
    - **Property 17: Saída do Assistente restrita ao catálogo**
    - **Validates: Requirements 18.4, 22.4**
  - [ ] 24.3 Implementar a Lambda de exportação
    - CSV UTF-8 com ";" (uma linha por Período) e PDF agrupado por data; mesmas regras de visibilidade do painel; S3 privado e URL pré-assinada de 5 min
    - _Requirements: 17.1, 17.2, 17.3_
  - [ ]* 24.4 Escrever testes da exportação com dublê de S3 (round-trip do CSV exportado)
    - _Requirements: 17.4, 22.6_
  - [ ] 24.5 Implementar métricas e alarmes
    - Métricas de reservas criadas, conflitos RN5/RN6/RN7 e falhas de Notificação/SNP; alarme da DLQ ≥ 1 com SNS; X-Ray no API Gateway e no Step Functions
    - _Requirements: 21.1, 21.2, 21.3_
  - [ ] 24.6 Integrar no frontend o assistente, a exportação e a `/caixa-simulada`
    - Proposta pré-preenche e exige confirmação; aviso de indisponibilidade mantém o formulário manual
    - _Requirements: 13.6, 17.1, 18.5, 18.8_

- [x] 25. Configurar steering e hooks do Kiro
  - [x] 25.1 Criar `.kiro/steering/` com `idioma.md`, `java.md`, `angular.md`, `seguranca-lgpd.md` e `arquitetura.md`
    - _Requirements: 19.1, 19.2_
  - [ ] 25.2 Criar `.kiro/hooks/` com `testes-dominio`, `verifica-segredos-pii` e `atualiza-openapi-docs`
    - _Requirements: 19.3, 19.4, 19.5_

- [x] 26. Escrever a documentação
  - [x] 26.1 Escrever o `README.md` da raiz e o `infra/README.md`
    - Pré-requisitos, execução local, testes, usuários de teste, roteiro da demo (login, painel, conflito bloqueado, reserva válida, Caixa_Simulada, Pedido_SNP, alteração com destaque, cancelamento, exportação, assistente); deploy, envio do seed e destroy
    - _Requirements: 22.9, 23.4, 23.5_
  - [x] 26.2 Escrever `docs/`
    - Arquitetura e fluxo de eventos em Mermaid, modelo DynamoDB e ER, `openapi.yaml` (OpenAPI 3), `lgpd.md`, `kiro.md`
    - _Requirements: 3.10, 6.19, 17.5, 19.6, 22.10_

- [ ] 27. Checkpoint final
  - Rodar `mvn test` em `backend/`, build e testes do `frontend/` e `cdk synth` em `infra/`. Ensure all tests pass, ask the user if questions arise.

## Notes

- Tarefas marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada tarefa referencia requisitos específicos para rastreabilidade
- Testes de propriedade usam jqwik com `@Property(tries = 100)` e o comentário `// Feature: sisgares-reservas, Property N: <texto>`
- Testes de domínio usam `Clock.fixed`; `mvn test` roda sem conta AWS
- Checkpoints garantem validação incremental ao fim de cada bloco

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4"] },
    { "id": 2, "tasks": ["2.1", "17.1", "25.1"] },
    { "id": 3, "tasks": ["2.2", "2.4", "4.1", "4.2", "6.3", "7.1", "7.4", "17.2", "25.2"] },
    { "id": 4, "tasks": ["2.3", "2.5", "2.6", "3.1", "4.3", "6.4", "7.2", "7.3", "7.5", "18.1"] },
    { "id": 5, "tasks": ["3.2", "3.3", "3.4", "5.1", "9.1", "18.2"] },
    { "id": 6, "tasks": ["5.2", "9.2", "9.3", "9.5", "19.1"] },
    { "id": 7, "tasks": ["5.3", "5.4", "5.5", "5.6", "6.1", "9.4", "15.1", "19.2"] },
    { "id": 8, "tasks": ["6.2", "9.6", "10.1", "12.1", "14.1", "14.2"] },
    { "id": 9, "tasks": ["10.2", "11.1", "12.2", "12.3", "14.3", "14.4", "14.5"] },
    { "id": 10, "tasks": ["10.3", "11.2", "14.6", "20.1"] },
    { "id": 11, "tasks": ["10.4", "10.5", "11.3", "15.3", "20.2"] },
    { "id": 12, "tasks": ["15.2", "21.1", "20.3"] },
    { "id": 13, "tasks": ["15.4", "21.2", "23.1", "23.2"] },
    { "id": 14, "tasks": ["21.3", "23.3", "24.1", "24.3"] },
    { "id": 15, "tasks": ["21.4", "21.5", "23.4", "24.2", "24.4", "24.5"] },
    { "id": 16, "tasks": ["24.6", "26.1"] },
    { "id": 17, "tasks": ["26.2"] }
  ]
}
```
