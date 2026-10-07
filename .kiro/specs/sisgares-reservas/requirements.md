# Requirements Document

## Introduction

O SISGARES permite que as unidades do MPF solicitem e gerenciem reservas de ambientes físicos, recursos e serviços para reuniões, audiências, treinamentos e outros eventos. O sistema impede conflitos de horário (inclusive entre ambiente pai e filho, com margem de 30 minutos) e o uso de recursos limitados além do disponível, notifica por e-mail os setores envolvidos, gera pedidos no SNP simulado e oferece painéis por perfil (Solicitante; Administrador/Setor Atendente).

A solução é serverless e orientada a eventos na AWS: Angular servido por S3 + CloudFront, API Gateway com Cognito, Lambdas em Java 21 com SnapStart, DynamoDB, EventBridge + Step Functions para notificações e SNP, Verified Permissions para autorização fina, Bedrock como assistente de preenchimento e IaC com AWS CDK. O núcleo de domínio é Java puro e testável sem AWS. Specs, steering e hooks do Kiro fazem parte da entrega.

Fontes: caso de uso `documentacao/caso-de-uso-hackathon-SISGARES.docx` (F1–F10, RN1–RN13), especificação `documentacao/Solare-Solicitação de Ambientes e Recursos.pdf` (RF01–RF17) e CSVs sintéticos de `dados/` (copiados para `data/`).

Prioridades: Essencial — F1 (RF10), F2 (RF02, RF11), F3 (RF12), F4 (RF15), F5 (RF13, RF15), F7 (RF16), F8 (RF17), Arquitetura, Segurança. Desejável — F6 (RF14), F9 (RF01–RF08), F10 (RF09), Assistente Bedrock, Exportações, Observabilidade.

Módulos: A — Plataforma (Req. 1–4); B — Segurança (Req. 5–6); C — Cadastros e configurações (Req. 7–8); D — Reservas (Req. 9–12); E — Integrações assíncronas (Req. 13–14); F — Painéis e saídas (Req. 15–17); G — Inovação (Req. 18–19); H — Qualidade e entrega (Req. 20–23).

Fora do escopo: integração real com o SNP e com as bases de dados do MPF.

## Glossary

- **SISGARES**: sistema completo (Backend, Frontend e infraestrutura).
- **Backend**: conjunto de funções AWS Lambda em Java 21 com SnapStart, expostas pelo API_Gateway.
- **Frontend**: aplicação Angular hospedada em S3 privado servido pelo CloudFront.
- **API_Gateway**: Amazon API Gateway que expõe a API REST sob `/api`, com authorizer do Cognito.
- **Núcleo_Domínio**: módulo Java puro, sem dependência de AWS, com Validador_Reserva, cálculo de status, detecção de conflitos e cálculo da grade.
- **Validador_Reserva**: componente do Núcleo_Domínio que aplica RN1–RN9 e RN12.
- **Autorizador**: componente do Backend que consulta o Amazon Verified Permissions com políticas Cedar.
- **Evento_Reserva**: evento `ReservaCriada`, `ReservaAlterada` ou `ReservaCancelada` publicado no Barramento_Eventos, com ID único.
- **Barramento_Eventos**: barramento customizado do Amazon EventBridge do SISGARES.
- **Fluxo_Pós_Reserva**: máquina de estados AWS Step Functions acionada por regra do Barramento_Eventos, que executa o Notificador e o Cliente_SNP.
- **Notificador**: Lambda que gera e envia e-mails aos setores via Amazon SES ou Caixa_Simulada.
- **Caixa_Simulada**: registro dos e-mails gerados, gravado no DynamoDB, consultável pelo Administrador.
- **Cliente_SNP**: Lambda que registra pedidos no SNP simulado.
- **SNP**: Sistema Nacional de Pedidos; neste projeto existe apenas um simulador (Lambda mock).
- **Importador_Seed**: Lambda que carrega os CSVs do bucket S3 de seed no DynamoDB.
- **Assistente_Reserva**: Lambda que usa o Amazon Bedrock para converter texto livre em proposta de preenchimento do formulário de Reserva.
- **Exportador**: Lambda que gera CSV e PDF do painel do Atendente/Administrador.
- **Item_Controle**: item do DynamoDB, um por Ambiente raiz e um por Recurso_Limitado, com atributo de versão usado na condição otimista de gravação.
- **Ambiente_Raiz**: ancestral sem AMBI_ID_PAI de um Ambiente (o próprio Ambiente quando não tem pai).
- **Administrador**: perfil que mantém tabelas básicas, configurações e todas as reservas da própria Unidade_Macro.
- **Setor_Atendente**: perfil de usuário vinculado a um Setor_Envolvido; consulta reservas que envolvem o setor.
- **Solicitante**: perfil que cria, altera e cancela as próprias reservas.
- **Unidade_Macro**: unidade do MPF (ex.: "PR/CE") à qual pertencem ambientes, setores, recursos e solicitantes.
- **Setor_Envolvido**: setor responsável por ambientes/recursos (ENVO).
- **Ambiente**: espaço físico reservável (AMBI), com hierarquia opcional via AMBI_ID_PAI.
- **Ambiente_Relacionado**: para um Ambiente X, o próprio X, todos os ancestrais de X e todos os descendentes de X.
- **Local_Proprio**: opção "Não solicitado / local próprio", que representa Reserva sem Ambiente.
- **Disposição**: arrumação de mesas e cadeiras com imagem ilustrativa (DISP).
- **Grupo_Recurso**: classificação de recursos com ordem de listagem (GREC).
- **Recurso**: serviço, estrutura ou equipamento solicitável (RECU).
- **Recurso_Limitado**: Recurso com RECU_ST_LIMITADO = "S" e quantidade RECU_DISPONIBILIDADE.
- **Reserva**: pedido de um Solicitante com Ambiente ou Local_Proprio, finalidade, participantes, Períodos e solicitações de Recursos (RESE).
- **Período**: intervalo [início, término) de uma Reserva (PRES).
- **Margem**: 30 minutos exigidos entre o término de um Período e o início de outro no mesmo Ambiente_Relacionado.
- **Configuração**: parâmetros de Antecedência_Mínima, Faixa_Horária global e por Unidade_Macro e endpoint do SNP.
- **Antecedência_Mínima**: número inteiro de minutos entre o instante atual e o início de um Período.
- **Faixa_Horária**: horário mínimo e máximo (HH:mm) aceito para início e término de Períodos.
- **Versão_Reserva**: snapshot imutável do estado de uma Reserva após cada inclusão, alteração ou cancelamento.
- **Pedido_SNP**: pedido registrado no SNP simulado, com número e link.
- **Notificação**: registro de e-mail gerado para um Setor_Envolvido.
- **Seed**: carga inicial a partir dos CSVs de `data/` e das Imagens_Ícone de `imagens/`.
- **Imagem_Ícone**: arquivo de `imagens/icones-disposicao/` (JPG) ou `imagens/icones-recurso/` (PNG) nomeado `<referência original>_<descrição-kebab>.<ext>` (ex.: `disp_001_auditorio.jpg`), com texto alternativo e descrição em `imagens/descricoes.md`; `indefinido_disposicao-nao-definida.jpg` e `indefinido_recurso-nao-definido.png` são as imagens padrão de cada categoria.
- **Dado_Pessoal**: nome e e-mail de usuários e e-mails de setores.

## Requirements

### Requirement 1: [A] Estrutura do projeto e stack

**User Story:** Como equipe de desenvolvimento, quero um monorepo com stack padronizada, para atender às restrições técnicas do caso de uso.

#### Acceptance Criteria

1. THE SISGARES SHALL ser organizado em monorepo com os diretórios `frontend/`, `backend/`, `docs/`, `data/`, `imagens/`, `infra/` e `.kiro/`.
2. THE Backend SHALL ser implementado em Java 21 com build Maven multimódulo, contendo o módulo `dominio` (Núcleo_Domínio) e um módulo por contexto de Lambda: catálogo/cadastros, reservas, painéis, configuração, eventos (Notificador e Cliente_SNP), seed, assistente e exportação.
3. THE Núcleo_Domínio SHALL compilar e executar seus testes sem dependências de bibliotecas AWS.
4. THE Frontend SHALL ser implementado em Angular.
5. THE Backend SHALL expor a API REST sob o prefixo `/api` com corpo JSON e datas no formato ISO-8601 com fuso `America/Fortaleza`.
6. IF uma requisição falhar por validação, THEN THE Backend SHALL responder HTTP 422 com corpo JSON `{"erros":[{"codigo","mensagem","campo"}]}`, com `codigo` no formato da regra (ex.: `RN5_CONFLITO_HORARIO`), `mensagem` em português e `campo` quando aplicável.
7. WHERE o modo de execução local estiver ativo, THE SISGARES SHALL executar o Backend com SAM local, DynamoDB Local e mock de Cognito, sem acesso a contas AWS.

### Requirement 2: [A] Arquitetura serverless e orientada a eventos

**User Story:** Como equipe, quero uma arquitetura serverless e desacoplada, para escalar sob demanda e isolar falhas de integrações.

#### Acceptance Criteria

1. THE Backend SHALL executar cada contexto (catálogo/cadastros, reservas, painéis, configuração) em função Lambda própria em Java 21 com SnapStart habilitado.
2. THE API_Gateway SHALL rotear as requisições de `/api` às Lambdas de contexto e validar o JWT do Cognito antes da invocação.
3. WHEN uma Reserva for incluída, alterada ou cancelada com sucesso, THE Backend SHALL publicar no Barramento_Eventos o Evento_Reserva correspondente, contendo ID do evento, RESE_ID, número da Versão_Reserva e tipo, sem Dados_Pessoais.
4. WHEN um Evento_Reserva for publicado, THE Barramento_Eventos SHALL acionar o Fluxo_Pós_Reserva, que executa o Notificador e o Cliente_SNP em ramos paralelos independentes.
5. IF uma etapa do Fluxo_Pós_Reserva falhar, THEN THE Fluxo_Pós_Reserva SHALL repetir a etapa até 3 vezes com backoff exponencial e, persistindo a falha, enviar o evento a uma fila SQS DLQ.
6. WHEN um Evento_Reserva com ID já processado for recebido, THE Notificador e THE Cliente_SNP SHALL concluir sem gerar nova Notificação ou novo Pedido_SNP (idempotência por ID do evento).
7. IF o Notificador ou o Cliente_SNP falhar, THEN THE Backend SHALL manter a Reserva gravada e a resposta HTTP já entregue ao Solicitante.
8. IF a publicação do Evento_Reserva falhar após a gravação da Reserva, THEN THE Backend SHALL registrar o evento pendente no DynamoDB e republicá-lo em até 5 minutos (outbox).

### Requirement 3: [A] Modelo de dados no DynamoDB

**User Story:** Como equipe, quero um modelo DynamoDB que preserve os CSVs e suporte os padrões de acesso, para atender todas as regras com baixa latência.

#### Acceptance Criteria

1. THE Backend SHALL persistir os dados no Amazon DynamoDB preservando os nomes de atributos e IDs dos CSVs: AMBI (AMBI_ID, AMBI_DESC, AMBI_ST_ATIVO, AMBI_ID_PAI), DISP (DISP_ID, DISP_DESC, DISP_ST_ATIVO, DISP_ICONE_ARQUIVO), GREC (GREC_ID, GREC_DESC, GREC_ORDEM, GREC_ST_ATIVO), RECU (RECU_ID, RECU_DESC, RECU_GREC_ID, RECU_ST_LIMITADO, RECU_DISPONIBILIDADE, RECU_ST_ATIVO, RECU_ICONE_ARQUIVO), VREC (VREC_ID, VREC_RECU_ID, VREC_AMBI_ID), ENVO (ENVO_ID, ENVO_DESC, ENVO_EMAIL, ENVO_ST_ATIVO), EAMB (EAMB_ID, EAMB_ENVO_ID, EAMB_AMBI_ID), EREC (EREC_ID, EREC_ENVO_ID, EREC_RECU_ID), PRES (PRES_ID, PRES_RESE_ID, PRES_DTHR_INICIO, PRES_DTHR_TERMINO) e SOLI (SOLI_ID, SOLI_RESE_ID, SOLI_RECU_ID, SOLI_QTD).
2. THE Backend SHALL incluir a entidade Unidade_Macro e o atributo de Unidade_Macro em AMBI e ENVO (obrigatório) e em RECU (opcional; ausente indica todas as unidades).
3. THE Backend SHALL incluir o atributo opcional de código de serviço do SNP em EAMB e EREC e o atributo opcional de lista de e-mails alternativa em ENVO.
4. THE Backend SHALL incluir a entidade Reserva (RESE) com RESE_ID, Unidade_Macro, Solicitante, Ambiente (ausente para Local_Proprio), complemento do ambiente, Disposição (opcional), finalidade, quantidade estimada de participantes, indicador de cancelamento, data/hora de cancelamento, data/hora da última alteração e número de versão.
5. THE Backend SHALL incluir as entidades Configuração, Pedido_SNP, Notificação, Versão_Reserva, Usuário (perfil, Unidade_Macro e Setor_Envolvido opcional), Item_Controle e Evento processado.
6. THE Backend SHALL armazenar os atributos `*_ST_*` com os valores "S" ou "N".
7. THE Backend SHALL oferecer um GSI por Ambiente_Raiz e data do Período e um GSI por Setor_Envolvido e data do Período, para as consultas de conflito, grade e painel do Atendente.
8. THE Backend SHALL oferecer um GSI por Unidade_Macro e data do Período e um GSI por Solicitante, para o painel do Administrador e a lista de reservas do Solicitante.
9. THE Backend SHALL atender as consultas de conflito, grade e painéis por Query em chave ou GSI, sem operação Scan.
10. THE SISGARES SHALL documentar em `docs/` o modelo DynamoDB (entidades, chaves de partição e ordenação, GSIs e padrões de acesso) e o diagrama ER em Mermaid.

### Requirement 4: [A] Seed a partir dos CSVs sintéticos

**User Story:** Como equipe, quero carregar os CSVs sintéticos, para demonstrar o sistema com dados realistas.

#### Acceptance Criteria

1. WHEN o deploy for concluído, THE SISGARES SHALL enviar os 10 CSVs de `data/` ao bucket S3 de seed.
2. WHEN um CSV for gravado no bucket de seed ou o recurso customizado do deploy for executado, THE Importador_Seed SHALL carregar os registros no DynamoDB preservando todos os IDs originais.
3. THE Importador_Seed SHALL interpretar IDs com ponto de milhar removendo o ponto (ex.: "14.207" → 14207).
4. THE Importador_Seed SHALL interpretar datas no formato `dd/MM/yyyy HH:mm:ss` (ex.: "20/10/2026 11:00:00").
5. WHEN SOLI_QTD estiver vazio, THE Importador_Seed SHALL gravar a quantidade como ausente para Recurso não limitado e como 1 para Recurso_Limitado.
6. THE Importador_Seed SHALL criar a Unidade_Macro "PR/CE" e associar a ela todos os ambientes, setores e usuários do Seed, mantendo os recursos sem Unidade_Macro.
7. THE Importador_Seed SHALL criar uma Reserva para cada RESE_ID distinto referenciado em PRES ou SOLI, com finalidade "Reserva importada (seed)", 10 participantes, Solicitante fictício distribuído entre pelo menos 3 usuários Solicitantes e Ambiente ativo atribuído de forma determinística sem gerar conflito (RN5/RN6), ou Local_Proprio com complemento "Local próprio (seed)" quando não houver Ambiente livre.
8. THE Importador_Seed SHALL criar a Configuração padrão: Antecedência_Mínima 120 minutos, Faixa_Horária global 07:00–20:00 e endpoint do SNP simulado.
9. THE Importador_Seed SHALL atribuir código de serviço fictício a pelo menos um vínculo EREC e deixar pelo menos um vínculo sem código, para demonstrar RN11.
10. IF uma linha de CSV referenciar ID inexistente ou tiver formato inválido, THEN THE Importador_Seed SHALL registrar o arquivo, a linha e o motivo, ignorar a linha e continuar a carga.
11. WHEN a carga terminar, THE Importador_Seed SHALL gravar no S3 um relatório JSON com, por arquivo, o total de linhas lidas, gravadas e rejeitadas e a lista de rejeições (linha e motivo).
12. WHEN a carga for executada duas vezes sobre a mesma tabela, THE Importador_Seed SHALL manter o mesmo número de registros da primeira carga (idempotência).
13. WHEN o deploy for concluído, THE SISGARES SHALL enviar as Imagens_Ícone de `imagens/` ao bucket S3 privado de imagens.
14. WHEN um registro de DISP ou RECU for carregado, THE Importador_Seed SHALL resolver DISP_ICONE_ARQUIVO ou RECU_ICONE_ARQUIVO para a única Imagem_Ícone da categoria cujo nome seja o prefixo do valor do CSV antes da extensão, seguido de `_<descrição>` e da mesma extensão (ex.: `disp_001.jpg` → `disp_001_auditorio.jpg`; `equip_005.png` → `equip_005_projetor-multimidia.png`).
15. WHEN a referência de ícone for resolvida, THE Importador_Seed SHALL gravar o nome resolvido em DISP_ICONE_ARQUIVO ou RECU_ICONE_ARQUIVO e preservar o valor original do CSV em atributo próprio de referência original.
16. IF nenhuma Imagem_Ícone corresponder ao prefixo ou houver mais de uma correspondente, THEN THE Importador_Seed SHALL gravar a imagem padrão `indefinido_*` da categoria e registrar o arquivo CSV, a linha e o motivo (`ICONE_NAO_ENCONTRADO` ou `ICONE_AMBIGUO`) no relatório JSON de carga.
17. THE Importador_Seed SHALL carregar o texto alternativo de cada Imagem_Ícone a partir de `imagens/descricoes.md`, ou de arquivo estruturado gerado a partir dele, e gravá-lo associado ao nome da Imagem_Ícone para uso no atributo `alt`.

### Requirement 5: [B] Autenticação e autorização

**User Story:** Como usuário, quero entrar com meu perfil, para acessar somente as funções e dados permitidos.

#### Acceptance Criteria

1. THE SISGARES SHALL autenticar usuários por um Amazon Cognito User Pool com os grupos Administrador, Setor_Atendente e Solicitante, emitindo tokens JWT.
2. THE SISGARES SHALL exigir no Cognito senha com no mínimo 12 caracteres contendo maiúscula, minúscula, número e símbolo, com MFA opcional por TOTP.
3. WHERE o modo de execução local estiver ativo, THE SISGARES SHALL autenticar por mock local que emite JWT para usuários fictícios de cada perfil.
4. IF uma requisição à API chegar sem token válido, THEN THE API_Gateway SHALL responder HTTP 401.
5. WHEN uma operação sobre Reserva, painel ou cadastro for solicitada, THE Autorizador SHALL decidir o acesso pelo Amazon Verified Permissions com políticas Cedar versionadas em `infra/`.
6. IF o Autorizador negar a operação, THEN THE Backend SHALL responder HTTP 403.
7. THE Autorizador SHALL permitir ao Solicitante alterar e cancelar somente Reservas em que ele é o Solicitante.
8. THE Autorizador SHALL permitir ao Setor_Atendente consultar somente Reservas cujo Ambiente ou Recursos estejam vinculados ao Setor_Envolvido do usuário.
9. THE Autorizador SHALL permitir ao Administrador consultar, alterar e cancelar somente Reservas e cadastros da própria Unidade_Macro.
10. THE Autorizador SHALL restringir a manutenção de cadastros e Configuração ao perfil Administrador.

### Requirement 6: [B] Proteção de dados, validação e criptografia

**User Story:** Como responsável pela segurança, quero entradas validadas, menor privilégio e dados criptografados, para proteger o sistema e cumprir a LGPD.

#### Acceptance Criteria

1. THE SISGARES SHALL atribuir a cada Lambda uma role IAM própria com somente as ações e ARNs necessários, sem curinga `*` em ações de escrita.
2. THE API_Gateway SHALL validar o corpo das requisições contra JSON Schema e responder HTTP 400 a corpos fora do schema.
3. THE Backend SHALL validar todos os inputs com Bean Validation, aceitar somente campos da whitelist de cada DTO e limitar finalidade a 2000 caracteres, complemento a 200 caracteres e participantes a 1–10000.
4. IF uma requisição contiver campo fora da whitelist, THEN THE Backend SHALL responder HTTP 422.
5. THE Backend SHALL construir todas as expressões DynamoDB com valores parametrizados (ExpressionAttributeValues).
6. THE Notificador SHALL escapar HTML em todos os valores de texto livre (finalidade, complemento, descrições) inseridos no e-mail.
7. THE Backend SHALL emitir logs estruturados em JSON contendo IDs, com Dados_Pessoais mascarados.
8. IF ocorrer erro interno, THEN THE Backend SHALL responder HTTP 500 com código e ID de correlação, sem stack trace.
9. THE SISGARES SHALL criptografar as tabelas DynamoDB e os buckets S3 com chave KMS gerenciada pelo cliente (SSE-KMS).
10. THE SISGARES SHALL bloquear acesso público em todos os buckets S3 e servir o Frontend somente pelo CloudFront com Origin Access Control.
11. THE SISGARES SHALL exigir HTTPS com TLS 1.2 ou superior no CloudFront e no API_Gateway.
12. THE SISGARES SHALL armazenar segredos e parâmetros sensíveis no Secrets Manager ou SSM Parameter Store, sem segredos versionados no repositório.
13. THE SISGARES SHALL associar AWS WAF com regras gerenciadas da AWS e limite de 1000 requisições por IP a cada 5 minutos ao CloudFront e ao API_Gateway.
14. THE API_Gateway SHALL aplicar throttling de 50 requisições por segundo com burst de 100 e CORS restrito à origem do CloudFront.
15. THE SISGARES SHALL registrar chamadas de API AWS em trilha do CloudTrail.
16. THE SISGARES SHALL usar somente dados fictícios.
17. THE Backend SHALL retornar o nome do Solicitante somente ao próprio Solicitante, ao Administrador e ao Setor_Atendente envolvido na Reserva, e retornar o Período ocupado sem Dados_Pessoais aos demais usuários.
18. THE SISGARES SHALL reter Notificações por 90 dias via TTL do DynamoDB e logs do CloudWatch por 90 dias.
19. THE SISGARES SHALL documentar em `docs/` os Dados_Pessoais tratados, a finalidade, a base de exibição por perfil e o prazo de retenção (LGPD).

### Requirement 7: [C] Cadastros de tabelas básicas (F9, Desejável)

**User Story:** Como Administrador, quero manter setores, ambientes, disposições, grupos e recursos com seus vínculos, para manter as tabelas da unidade.

#### Acceptance Criteria

1. THE Backend SHALL oferecer ao Administrador inclusão, alteração, consulta e inativação (ST_ATIVO = "N") de Setor_Envolvido, Ambiente, Disposição, Grupo_Recurso e Recurso.
2. THE Backend SHALL exigir em Setor_Envolvido descrição, Unidade_Macro e e-mail padrão, aceitando opcionalmente uma lista de e-mails alternativa.
3. IF algum e-mail informado em Setor_Envolvido tiver formato inválido, THEN THE Backend SHALL responder HTTP 422.
4. THE Backend SHALL exigir em Ambiente descrição e Unidade_Macro, aceitando opcionalmente um ambiente pai.
5. IF o ambiente pai pertencer a outra Unidade_Macro ou for o próprio Ambiente ou um de seus descendentes, THEN THE Backend SHALL responder HTTP 422.
6. THE Backend SHALL permitir vincular um Ambiente a um ou mais Setor_Envolvido da mesma Unidade_Macro (EAMB), com código de serviço do SNP opcional.
7. THE Backend SHALL exigir em Disposição descrição e uma imagem única, armazenada em bucket S3 privado.
8. IF a imagem enviada não for PNG, JPEG ou SVG sanitizado ou exceder 2 MB, THEN THE Backend SHALL responder HTTP 422.
9. THE Backend SHALL exigir em Grupo_Recurso descrição e aceitar ordem numérica (GREC_ORDEM).
10. THE Frontend SHALL listar recursos agrupados por Grupo_Recurso em ordem crescente de GREC_ORDEM (seed: Serviço 1, Estrutura 2, Equipamento 3).
11. THE Backend SHALL exigir em Recurso descrição, exatamente um Grupo_Recurso e um ícone, aceitando Unidade_Macro opcional.
12. IF um Recurso for marcado como limitado com disponibilidade menor que 1, THEN THE Backend SHALL responder HTTP 422.
13. THE Backend SHALL permitir vincular um Recurso a um ou mais Setor_Envolvido (EREC), com código de serviço do SNP opcional, e a um ou mais Ambientes (VREC).
14. THE Backend SHALL omitir registros com ST_ATIVO = "N" das opções oferecidas no cadastro de reservas, mantendo-os nas reservas existentes.

### Requirement 8: [C] Configurações (F10, Desejável)

**User Story:** Como Administrador, quero configurar antecedência, faixa de horário e endpoint do SNP, para ajustar regras sem alterar código.

#### Acceptance Criteria

1. THE Backend SHALL permitir ao Administrador definir a Antecedência_Mínima como inteiro entre 0 e 10080 minutos.
2. THE Backend SHALL permitir ao Administrador definir a Faixa_Horária global e, opcionalmente, uma Faixa_Horária por Unidade_Macro.
3. IF o horário mínimo de uma Faixa_Horária for igual ou posterior ao horário máximo, THEN THE Backend SHALL responder HTTP 422.
4. THE Backend SHALL permitir ao Administrador definir a URL do endpoint do SNP.
5. IF a URL do endpoint do SNP não iniciar com `https://` (ou `http://` no modo de execução local), THEN THE Backend SHALL responder HTTP 422.
6. WHEN uma Configuração for salva, THE Validador_Reserva SHALL aplicar os novos valores às validações iniciadas a partir de 60 segundos depois, sem novo deploy.

### Requirement 9: [D] Cadastro de reserva (F1, Essencial)

**User Story:** Como Solicitante, quero cadastrar uma reserva de ambiente e/ou recursos, para garantir a estrutura do meu evento.

#### Acceptance Criteria

1. THE Frontend SHALL oferecer no campo Ambiente os ambientes ativos da Unidade_Macro do Solicitante e a opção Local_Proprio, selecionada por padrão.
2. THE Frontend SHALL exibir campos de finalidade (multilinha), quantidade de participantes, Disposição (opcional, com a imagem da opção selecionada, visível apenas com Ambiente selecionado), complemento do ambiente, Períodos e solicitações de Recursos.
3. THE Frontend SHALL exibir no topo da tela de cadastro/edição o nome do Solicitante, o status (RN13), a data/hora da última alteração e os números dos Pedido_SNP como links.
4. IF a Reserva não tiver nenhum Período, THEN THE Validador_Reserva SHALL bloquear com código `RN1_SEM_PERIODO`.
5. IF o término de um Período for igual ou anterior ao início, THEN THE Validador_Reserva SHALL bloquear com código `RN1_TERMINO_INVALIDO` (exemplo: 10/11 14:00 a 10/11 13:00 → bloqueado).
6. WHEN um Período começar em um dia e terminar em outro com término posterior ao início, THE Validador_Reserva SHALL aceitar o Período quanto a RN1 (exemplo: 10/11 18:00 a 11/11 09:00 → aceito).
7. IF a finalidade estiver vazia ou contiver apenas espaços, THEN THE Validador_Reserva SHALL bloquear com código `RN2_FINALIDADE_OBRIGATORIA`.
8. IF a quantidade de participantes estiver ausente ou for menor que 1, THEN THE Validador_Reserva SHALL bloquear com código `RN2_PARTICIPANTES_OBRIGATORIO`.
9. IF o Ambiente for Local_Proprio e o complemento do ambiente estiver vazio, THEN THE Validador_Reserva SHALL bloquear com código `RN2_COMPLEMENTO_OBRIGATORIO` (exemplo: reserva só de água e café, sem ambiente e sem complemento → bloqueado).
10. IF o início ou o término de um Período estiver fora da Faixa_Horária aplicável, THEN THE Validador_Reserva SHALL bloquear com código `RN3_FORA_FAIXA` (exemplo: faixa 07:00–20:00 e período 18:00–21:00 → bloqueado).
11. WHERE a Unidade_Macro da Reserva tiver Faixa_Horária própria, THE Validador_Reserva SHALL usar a faixa da Unidade_Macro no lugar da faixa global.
12. IF o início de um Período for anterior ao instante atual somado à Antecedência_Mínima, THEN THE Validador_Reserva SHALL bloquear com código `RN4_SEM_ANTECEDENCIA` (exemplo: antecedência 120 min, agora 10:00, início 11:00 → bloqueado; início 12:00 → aceito).
13. IF uma Reserva contiver dois Períodos próprios que conflitem entre si segundo a Margem, THEN THE Validador_Reserva SHALL bloquear com código `RN5_CONFLITO_HORARIO`.
14. WHEN uma Reserva válida for salva, THE Backend SHALL responder HTTP 201 com a Reserva, gravar a primeira Versão_Reserva e publicar o Evento_Reserva `ReservaCriada`.
15. WHEN a validação encontrar mais de uma violação, THE Backend SHALL retornar todas as violações na mesma resposta HTTP 422.
16. THE Núcleo_Domínio SHALL calcular o status da Reserva como "cancelada" se cancelada; senão "prevista" se o instante atual for anterior ao primeiro início; "transcorrida" se o instante atual for igual ou posterior ao último término; e "em andamento" nos demais casos (exemplo: agora 10:00, período 09:00–11:00 → em andamento).

### Requirement 10: [D] Conflitos de horário (F2, Essencial)

**User Story:** Como Solicitante, quero ser avisado de conflitos ao escolher cada período e ao salvar, para não reservar um espaço ocupado.

#### Acceptance Criteria

1. THE Validador_Reserva SHALL considerar conflitantes dois Períodos A e B quando A.início < B.término + 30 min e B.início < A.término + 30 min.
2. IF um Período da Reserva conflitar com um Período de outra Reserva não cancelada do mesmo Ambiente, THEN THE Validador_Reserva SHALL bloquear com código `RN5_CONFLITO_HORARIO`, informando o Período e a Reserva conflitante (exemplo: Auditório 09:00–11:00 reservado; nova 11:20–12:00 → bloqueado; nova 11:30–12:00 → aceito).
3. IF um Período da Reserva conflitar com um Período de outra Reserva não cancelada de um Ambiente_Relacionado, THEN THE Validador_Reserva SHALL bloquear com código `RN6_CONFLITO_PAI_FILHO` (exemplo: Auditório (Parte A), filho do Auditório (Completo), reservado 14:00–16:00; nova reserva do Auditório (Completo) 15:00–17:00 → bloqueado).
4. THE Validador_Reserva SHALL desconsiderar conflitos de horário para Reservas com Local_Proprio.
5. WHEN o Solicitante concluir o preenchimento de um Período no Frontend, THE Frontend SHALL consultar o Backend e exibir o aviso de conflito junto ao Período em até 2 segundos.
6. WHEN uma Reserva for salva, THE Backend SHALL refazer a verificação completa de conflitos e gravar a Reserva, os Períodos e o incremento de versão do Item_Controle do Ambiente_Raiz em uma única operação TransactWriteItems com condição otimista sobre a versão lida (exemplo: dois usuários preenchem o Auditório 09:00–10:00; o primeiro salva; o segundo, ao salvar → bloqueado).
7. IF a condição otimista do Item_Controle falhar por gravação concorrente, THEN THE Backend SHALL descartar a gravação e responder HTTP 409 com código `RN7_CONFLITO_AO_SALVAR`.
8. WHEN uma Reserva existente for alterada, THE Validador_Reserva SHALL desconsiderar os Períodos da própria Reserva na verificação.

### Requirement 11: [D] Recursos (F3, Essencial)

**User Story:** Como Solicitante, quero ser impedido de pedir mais unidades de um recurso limitado do que as disponíveis, para que o pedido possa ser atendido.

#### Acceptance Criteria

1. THE Frontend SHALL exibir o campo de quantidade somente para Recurso_Limitado.
2. IF a quantidade solicitada de um Recurso_Limitado for menor que 1, THEN THE Validador_Reserva SHALL bloquear com código `RN8_QUANTIDADE_INVALIDA`.
3. IF, para algum Período da Reserva, a soma da quantidade solicitada com as quantidades do mesmo Recurso_Limitado em outras Reservas não canceladas com Períodos que se cruzam (sem Margem) exceder RECU_DISPONIBILIDADE, THEN THE Validador_Reserva SHALL bloquear com código `RN8_RECURSO_INSUFICIENTE` informando a quantidade disponível (exemplo: 3 projetores, 2 reservados 14:00–16:00; nova pede 2 para 15:00–17:00 → bloqueado; pede 1 → aceito).
4. WHEN uma Reserva com Recurso_Limitado for salva, THE Backend SHALL incluir na mesma TransactWriteItems o incremento condicional de versão do Item_Controle de cada Recurso_Limitado solicitado.
5. THE Backend SHALL oferecer em uma Reserva somente Recursos ativos sem Unidade_Macro ou com a Unidade_Macro da Reserva.
6. WHERE um Recurso tiver vínculos VREC, THE Backend SHALL oferecer o Recurso somente em Reservas de Ambientes vinculados (exemplo: Videoconferência (CODEC 01), vinculado à Sala de Reuniões - 9º andar, não aparece ao reservar o Auditório (Completo)).
7. IF a Reserva solicitar Recurso fora da Unidade_Macro ou de Ambiente não vinculado, THEN THE Validador_Reserva SHALL bloquear com código `RN9_RECURSO_INDISPONIVEL`.
8. WHEN o Ambiente da Reserva for alterado, THE Frontend SHALL remover as solicitações de Recursos não permitidos no novo Ambiente e avisar o Solicitante.

### Requirement 12: [D] Alteração e cancelamento (F4, Essencial)

**User Story:** Como Solicitante, quero alterar ou cancelar uma reserva não transcorrida, para manter o pedido atualizado.

#### Acceptance Criteria

1. IF uma alteração for solicitada para Reserva com status "transcorrida" ou "cancelada", THEN THE Backend SHALL bloquear com HTTP 422 e código `RN12_RESERVA_NAO_EDITAVEL`.
2. WHEN uma Reserva for alterada, THE Validador_Reserva SHALL aplicar as mesmas validações do cadastro (RN1–RN9).
3. WHILE uma Reserva estiver "em andamento", THE Validador_Reserva SHALL aplicar RN4 apenas aos Períodos novos ou com início alterado.
4. WHEN uma alteração válida for salva, THE Backend SHALL gravar nova Versão_Reserva, atualizar a data/hora da última alteração e publicar o Evento_Reserva `ReservaAlterada`.
5. WHEN o Solicitante pedir o cancelamento, THE Frontend SHALL exibir diálogo de confirmação acessível antes de enviar o pedido.
6. IF o cancelamento ocorrer sem confirmação explícita no pedido, THEN THE Backend SHALL responder HTTP 422 com código `RN12_CONFIRMACAO_OBRIGATORIA`.
7. IF o instante atual somado à Antecedência_Mínima for posterior ao primeiro início da Reserva, THEN THE Backend SHALL bloquear o cancelamento com código `RN12_CANCELAMENTO_SEM_ANTECEDENCIA`.
8. WHEN um cancelamento válido for confirmado, THE Backend SHALL marcar a Reserva como cancelada, gravar nova Versão_Reserva, liberar os Períodos para novas reservas e publicar o Evento_Reserva `ReservaCancelada`.

### Requirement 13: [E] Notificações por e-mail (F5, Essencial)

**User Story:** Como Setor_Envolvido, quero receber e-mail de cada reserva nova, alterada ou cancelada que me envolva, com alterações destacadas, para preparar o atendimento.

#### Acceptance Criteria

1. WHEN o Fluxo_Pós_Reserva processar um Evento_Reserva, THE Notificador SHALL gerar uma Notificação para cada Setor_Envolvido ativo vinculado ao Ambiente (EAMB) ou a algum Recurso solicitado (EREC), sem duplicar setores.
2. WHERE o Setor_Envolvido tiver lista de e-mails alternativa, THE Notificador SHALL enviar para a lista; nos demais casos, THE Notificador SHALL enviar para ENVO_EMAIL.
3. THE Notificador SHALL incluir no e-mail o tipo do evento (inclusão, alteração ou cancelamento), Ambiente ou complemento, finalidade, participantes, Disposição, Períodos, Recursos com quantidades, Solicitante e números de Pedido_SNP.
4. WHEN o evento for `ReservaAlterada`, THE Notificador SHALL gerar e-mail em HTML que exibe, para cada campo alterado em relação à Versão_Reserva anterior, o valor antigo e o novo com estilo de destaque (exemplo: horário de 14:00 para 15:00 → e-mail mostra 14:00 e 15:00 em destaque).
5. THE Notificador SHALL enviar os e-mails pelo Amazon SES com identidade verificada e registrar cada Notificação na Caixa_Simulada.
6. WHERE o SES estiver em sandbox ou o modo de execução local estiver ativo, THE Notificador SHALL registrar os e-mails somente na Caixa_Simulada, consultável pelo Administrador.
7. IF o envio de um e-mail falhar após as tentativas do Fluxo_Pós_Reserva, THEN THE Notificador SHALL registrar a falha na Notificação e manter a Reserva gravada.
8. THE Notificador SHALL usar somente endereços de e-mail fictícios provenientes do Seed ou de cadastro.

### Requirement 14: [E] Pedido no SNP simulado (F6, Desejável)

**User Story:** Como Setor_Envolvido, quero que a reserva gere pedido no SNP quando o vínculo tiver código de serviço, para formalizar a demanda.

#### Acceptance Criteria

1. WHEN o Fluxo_Pós_Reserva processar um `ReservaCriada`, THE Cliente_SNP SHALL registrar um Pedido_SNP para cada vínculo EAMB ou EREC envolvido que tenha código de serviço (exemplo: projetor vinculado à TI com código → pedido gerado).
2. THE Cliente_SNP SHALL ignorar vínculos sem código de serviço, que recebem apenas e-mail (exemplo: água vinculada à copa sem código → só e-mail).
3. THE Cliente_SNP SHALL enviar os pedidos ao endpoint configurado em Configuração, atendido por uma Lambda mock do SNP que retorna número do pedido e link.
4. WHEN o Fluxo_Pós_Reserva processar um `ReservaAlterada` em que um novo vínculo com código de serviço passou a ser envolvido, THE Cliente_SNP SHALL registrar Pedido_SNP apenas para o novo vínculo.
5. IF o SNP simulado estiver indisponível ou responder erro após as tentativas do Fluxo_Pós_Reserva, THEN THE Cliente_SNP SHALL registrar a falha no Pedido_SNP e manter a Reserva gravada.
6. THE Frontend SHALL exibir o número de cada Pedido_SNP como link para o pedido no simulador.

### Requirement 15: [F] Painel do Solicitante (F7, Essencial)

**User Story:** Como Solicitante, quero uma grade de datas por horários de 30 minutos para o ambiente escolhido, para encontrar rapidamente um horário livre.

#### Acceptance Criteria

1. THE Frontend SHALL exibir no topo do painel a seleção de Ambiente, a data de referência (inicial: data corrente), o número de colunas (1 a 14, padrão 7) e a opção de exibir fins de semana (padrão: não).
2. THE Frontend SHALL exibir uma coluna por data consecutiva a partir da data de referência, omitindo sábados e domingos quando a opção de fins de semana estiver desmarcada.
3. THE Frontend SHALL exibir uma linha a cada 30 minutos dentro da Faixa_Horária aplicável.
4. THE Frontend SHALL preencher visualmente todo o intervalo ocupado por cada Período de Reserva não cancelada do Ambiente selecionado ou de Ambiente_Relacionado.
5. WHERE o usuário for o Solicitante da Reserva ou Administrador, THE Frontend SHALL exibir o Período ocupado como link para a tela da Reserva.
6. THE Frontend SHALL exibir a célula imediatamente anterior ao início e a imediatamente posterior ao término de cada Período ocupado como indisponíveis com o texto "Margem de tolerância".
7. THE Frontend SHALL exibir células cujo horário já passou como indisponíveis com o texto "Horário ultrapassado".
8. THE Frontend SHALL exibir células cujo horário não atende à Antecedência_Mínima como indisponíveis com o texto "Sem antecedência mínima".
9. THE Frontend SHALL exibir cada célula livre como link "Reservar às XX:XX" (ex.: "Reservar às 09:30").
10. WHEN o Solicitante acionar "Reservar às XX:XX", THE Frontend SHALL abrir o cadastro de Reserva com Ambiente, data e horário de início preenchidos.
11. THE Núcleo_Domínio SHALL calcular o estado de cada célula com as mesmas regras de Margem, Antecedência_Mínima e Faixa_Horária do Validador_Reserva.

### Requirement 16: [F] Painel do Atendente (F8, Essencial)

**User Story:** Como Setor_Atendente, quero cards das reservas por data, para planejar o atendimento dos próximos dias.

#### Acceptance Criteria

1. THE Frontend SHALL exibir no topo do painel a data de referência (inicial: data corrente), o número de colunas (1 a 14, padrão 7) e a opção de exibir fins de semana (padrão: não).
2. THE Frontend SHALL exibir em cada coluna de data um card para cada Reserva com algum Período naquela data, ordenado por horário de início.
3. THE Frontend SHALL exibir no card horário, finalidade, nome do Solicitante, Recursos com quantidades, números de Pedido_SNP como links e link para a tela da Reserva.
4. WHILE o usuário tiver perfil Setor_Atendente, THE Backend SHALL retornar ao painel somente Reservas que envolvam o Setor_Envolvido do usuário.
5. WHILE o usuário tiver perfil Administrador, THE Backend SHALL retornar ao painel todas as Reservas da Unidade_Macro do usuário.
6. THE Frontend SHALL diferenciar cards de Reservas canceladas com texto "Cancelada", além de cor.

### Requirement 17: [F] Exportações e API documentada (Desejável)

**User Story:** Como Setor_Atendente ou Administrador, quero exportar o painel e consultar a API documentada, para planejar o atendimento fora do sistema e integrar outras ferramentas.

#### Acceptance Criteria

1. WHEN o Setor_Atendente ou o Administrador solicitar exportação, THE Exportador SHALL gerar CSV (UTF-8, separador ";") com uma linha por Período das Reservas do intervalo exibido no painel, com as mesmas regras de visibilidade do Requisito 16.
2. WHEN o Setor_Atendente ou o Administrador solicitar exportação em PDF, THE Exportador SHALL gerar PDF com as Reservas do intervalo exibido agrupadas por data e ordenadas por horário de início.
3. THE Exportador SHALL gravar o arquivo em bucket S3 privado e retornar URL pré-assinada válida por 5 minutos.
4. FOR ALL conjuntos de Reservas exportados em CSV, ler o CSV gerado SHALL produzir os mesmos Períodos, Ambientes e Recursos exportados (round-trip).
5. THE SISGARES SHALL documentar todos os endpoints de `/api` em especificação OpenAPI 3 versionada em `docs/`.

### Requirement 18: [G] Assistente de reserva com Bedrock (Desejável)

**User Story:** Como Solicitante, quero descrever meu evento em linguagem natural, para que o formulário seja pré-preenchido e eu ganhe tempo.

#### Acceptance Criteria

1. WHEN o Solicitante enviar uma descrição de até 1000 caracteres, THE Assistente_Reserva SHALL invocar o Amazon Bedrock e retornar proposta JSON com Ambiente, data, início, término, participantes, finalidade e Recursos com quantidades (exemplo: "reunião amanhã às 14h no auditório para 30 pessoas com café e projetor" → Auditório, amanhã, 14:00, 30 participantes, Café e Projetor).
2. THE Assistente_Reserva SHALL enviar ao modelo somente a descrição, a data atual e os catálogos de Ambientes e Recursos permitidos ao Solicitante, sem Dados_Pessoais.
3. THE Assistente_Reserva SHALL aplicar Bedrock Guardrails na entrada e na saída do modelo.
4. IF a saída do modelo não obedecer ao JSON Schema da proposta ou citar ID fora dos catálogos enviados, THEN THE Assistente_Reserva SHALL descartar os campos inválidos e informar ao Frontend os campos não preenchidos.
5. WHEN uma proposta for recebida, THE Frontend SHALL pré-preencher o formulário e exigir a confirmação do Solicitante antes de enviar a Reserva ao Validador_Reserva.
6. THE Assistente_Reserva SHALL retornar somente propostas, sem gravar Reservas.
7. THE Assistente_Reserva SHALL ler o identificador do modelo Bedrock de variável de ambiente.
8. IF o Bedrock estiver indisponível ou exceder 10 segundos, THEN THE Frontend SHALL informar a indisponibilidade e manter o formulário manual operante.
9. WHERE o resumo por IA estiver habilitado, THE Notificador SHALL acrescentar ao e-mail de alteração um resumo em linguagem natural das diferenças, gerado sem Dados_Pessoais e mantendo o destaque HTML determinístico do Requisito 13.

### Requirement 19: [G] Kiro como parte da solução

**User Story:** Como equipe, quero versionar specs, steering e hooks do Kiro, para demonstrar desenvolvimento orientado a especificação e automatizar verificações.

#### Acceptance Criteria

1. THE SISGARES SHALL versionar em `.kiro/specs/` os documentos de requisitos, design e tarefas desta spec.
2. THE SISGARES SHALL versionar em `.kiro/steering/` arquivos de idioma, padrões de código Java e Angular, convenções de segurança e LGPD e arquitetura.
3. THE SISGARES SHALL versionar em `.kiro/hooks/` um hook que executa os testes do Núcleo_Domínio ao salvar arquivo Java.
4. THE SISGARES SHALL versionar em `.kiro/hooks/` um hook que verifica segredos e Dados_Pessoais reais antes da gravação de arquivos.
5. THE SISGARES SHALL versionar em `.kiro/hooks/` um hook que atualiza a especificação OpenAPI e os documentos de `docs/` quando handlers de Lambda forem alterados.
6. THE SISGARES SHALL documentar em `docs/` como specs, steering e hooks foram usados no desenvolvimento.

### Requirement 20: [H] Responsividade e acessibilidade

**User Story:** Como usuário, quero uma interface acessível em computador e celular, adaptada ao meu perfil.

#### Acceptance Criteria

1. THE Frontend SHALL ser utilizável sem rolagem horizontal da página em larguras de 360 px a 1920 px, permitindo rolagem interna apenas nas grades dos painéis.
2. THE Frontend SHALL seguir o eMAG e as WCAG 2.1 nível AA.
3. THE Frontend SHALL permitir operar todas as funções por teclado, com foco visível.
4. THE Frontend SHALL apresentar contraste mínimo de 4,5:1 para texto normal e 3:1 para texto grande e componentes de interface.
5. THE Frontend SHALL associar rótulo visível a todos os campos de formulário e anunciar mensagens de erro de validação a leitores de tela.
6. THE Frontend SHALL transmitir estados da grade (livre, ocupado, margem, ultrapassado, sem antecedência) por texto, além de cor.
7. WHEN o usuário entrar no SISGARES, THE Frontend SHALL exibir como tela inicial o painel do perfil (Solicitante: Requisito 15; Setor_Atendente e Administrador: Requisito 16) e somente os itens de menu permitidos ao perfil.
8. THE Frontend SHALL exibir cada ícone de Recurso e de Disposição com o texto alternativo da Imagem_Ícone proveniente de `imagens/descricoes.md` no atributo `alt`.
9. IF a Imagem_Ícone não tiver texto alternativo em `imagens/descricoes.md`, THEN THE Frontend SHALL usar RECU_DESC ou DISP_DESC como texto alternativo.

### Requirement 21: [H] Observabilidade (Desejável)

**User Story:** Como equipe de operação, quero métricas, rastreamento e alarmes, para detectar falhas rapidamente.

#### Acceptance Criteria

1. THE SISGARES SHALL enviar logs de todas as Lambdas ao CloudWatch Logs e habilitar AWS X-Ray nas Lambdas, no API_Gateway e no Fluxo_Pós_Reserva.
2. THE Backend SHALL publicar métricas no CloudWatch de reservas criadas, conflitos bloqueados (RN5, RN6, RN7) e falhas de Notificação e Pedido_SNP.
3. WHEN a DLQ contiver ao menos 1 mensagem, THE SISGARES SHALL disparar alarme do CloudWatch com notificação em tópico SNS.

### Requirement 22: [H] Testes e documentação

**User Story:** Como equipe, quero testes automatizados e documentação, para comprovar as regras e permitir a execução e a demo da solução.

#### Acceptance Criteria

1. THE Backend SHALL conter testes JUnit 5 que reproduzem cada exemplo da tabela de RN1 a RN13 como caso de teste.
2. THE Backend SHALL conter testes de propriedade com jqwik para: simetria da detecção de conflito (A conflita com B se e somente se B conflita com A); conflito pela Margem para intervalos menores que 30 minutos e ausência de conflito para intervalos iguais ou maiores; propagação de conflito entre pai e filho; soma de quantidades nunca acima de RECU_DISPONIBILIDADE para Reservas aceitas; e exclusividade e totalidade do status (RN13).
3. THE Backend SHALL conter teste de round-trip do leitor de CSV: ler, formatar e ler novamente produz registros equivalentes, incluindo IDs com ponto de milhar e datas `dd/MM/yyyy HH:mm:ss`.
4. THE Backend SHALL conter teste de propriedade em que, para toda proposta gerada pelo Assistente_Reserva, a proposta validada pelo JSON Schema contém somente IDs dos catálogos enviados.
5. THE Backend SHALL conter testes de integração com DynamoDB Local para RN7 (gravação concorrente com 409), RN10 (setores notificados), RN11 (Pedido_SNP somente com código de serviço) e idempotência por ID do evento.
6. THE Backend SHALL conter testes das Lambdas com Bedrock, SES e EventBridge substituídos por dublês.
7. THE Núcleo_Domínio SHALL receber relógio injetável para fixar o instante atual nos testes.
8. WHEN `mvn test` for executado em `backend/`, THE Backend SHALL executar os testes de domínio e de Lambdas sem acesso a contas AWS.
9. THE SISGARES SHALL conter README na raiz com descrição da solução, pré-requisitos, comandos de execução local, comandos de teste, usuários de teste e roteiro da demo de ponta a ponta com os dados sintéticos (login, painel, reserva com conflito bloqueado, reserva válida, e-mail na Caixa_Simulada, Pedido_SNP, alteração com destaque, cancelamento, exportação e assistente).
10. THE SISGARES SHALL conter em `docs/` o diagrama de arquitetura AWS em Mermaid, o fluxo de eventos (Evento_Reserva → EventBridge → Step Functions → Notificador/Cliente_SNP → DLQ) e o modelo DynamoDB do Requisito 3.
11. THE Backend SHALL conter teste unitário JUnit 5 da resolução de ícone por prefixo do Importador_Seed com os casos: exatamente uma Imagem_Ícone correspondente (nome resolvido gravado e valor original preservado), nenhuma correspondente e mais de uma correspondente (imagem padrão `indefinido_*` gravada e motivo registrado no relatório JSON).

### Requirement 23: [H] Infraestrutura como código

**User Story:** Como equipe, quero toda a infraestrutura descrita como código, para implantar e destruir o ambiente do hackathon por comando.

#### Acceptance Criteria

1. THE SISGARES SHALL conter em `infra/` um projeto AWS CDK que define API_Gateway, Lambdas, roles IAM, tabelas DynamoDB e GSIs, buckets S3, CloudFront com OAC, Cognito User Pool e grupos, Verified Permissions e políticas Cedar, Barramento_Eventos e regras, Fluxo_Pós_Reserva, filas SQS e DLQ, identidade SES, chaves KMS, WAF, CloudTrail, alarmes e parâmetros SSM.
2. THE SISGARES SHALL criar todos os recursos AWS da solução somente pelo projeto de `infra/`.
3. THE Backend SHALL ler URLs, nomes de tabelas, identificador do modelo Bedrock e segredos de variáveis de ambiente, SSM Parameter Store ou Secrets Manager.
4. THE SISGARES SHALL documentar em `infra/README.md` os comandos de implantação (incluindo envio do Seed) e de destruição dos recursos.
5. WHEN o comando de destruição documentado for executado, THE SISGARES SHALL remover todos os recursos criados pelo projeto de `infra/`.
