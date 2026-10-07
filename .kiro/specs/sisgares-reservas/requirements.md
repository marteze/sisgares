# Requirements Document

## Introduction

O SISGARES permite que as unidades do MPF solicitem e gerenciem reservas de ambientes físicos, recursos e serviços para reuniões, audiências, treinamentos e outros eventos. O sistema impede conflitos de horário (inclusive entre ambiente pai e filho, com margem de 30 minutos) e o uso de recursos limitados além do disponível, notifica por e-mail os setores envolvidos, gera pedidos simulados no SNP e oferece painéis por perfil (Solicitante; Administrador/Setor Atendente).

Fontes: caso de uso `documentacao/caso-de-uso-hackathon-SISGARES.docx` (F1–F10, RN1–RN13), especificação `documentacao/Solare-Solicitação de Ambientes e Recursos.pdf` (RF01–RF17) e CSVs em `dados/`.

Prioridades: Essencial — F1 (RF10), F2 (RF02, RF11), F3 (RF12), F4 (RF15), F5 (RF13, RF15), F7 (RF16), F8 (RF17). Desejável — F6 (RF14), F9 (RF01–RF08), F10 (RF09).

Fora do escopo: integração real com o SNP e com as bases de dados do MPF.

## Glossary

- **SISGARES**: sistema completo (Backend + Frontend).
- **Backend**: API REST em Java 21, Spring Boot e Maven.
- **Frontend**: aplicação Angular.
- **Validador_Reserva**: componente do Backend que aplica RN1–RN9 e RN12.
- **Notificador**: componente do Backend que gera e envia e-mails aos setores.
- **Cliente_SNP**: componente do Backend que registra pedidos no SNP simulado.
- **SNP**: Sistema Nacional de Pedidos; neste projeto existe apenas um simulador (mock).
- **Administrador**: perfil que mantém tabelas básicas, configurações e todas as reservas da unidade.
- **Setor_Atendente**: perfil de usuário vinculado a um Setor_Envolvido; consulta reservas que envolvem o setor.
- **Solicitante**: perfil que cria, altera e cancela as próprias reservas.
- **Unidade_Macro**: unidade do MPF (ex.: "PR/CE") à qual pertencem ambientes, setores, recursos e solicitantes.
- **Setor_Envolvido**: setor responsável por ambientes/recursos (tabela ENVO).
- **Ambiente**: espaço físico reservável (AMBI), com hierarquia opcional via AMBI_ID_PAI.
- **Ambiente_Relacionado**: para um Ambiente X, o próprio X, todos os ancestrais de X e todos os descendentes de X.
- **Local_Proprio**: opção "Não solicitado / local próprio", que representa reserva sem Ambiente.
- **Disposição**: arrumação de mesas e cadeiras com imagem ilustrativa (DISP).
- **Grupo_Recurso**: classificação de recursos com ordem de listagem (GREC).
- **Recurso**: serviço, estrutura ou equipamento solicitável (RECU).
- **Recurso_Limitado**: Recurso com RECU_ST_LIMITADO = "S" e quantidade RECU_DISPONIBILIDADE.
- **Reserva**: pedido de um Solicitante com Ambiente ou Local_Proprio, finalidade, participantes, períodos e solicitações de recursos.
- **Período**: intervalo [início, término) de uma Reserva (PRES).
- **Margem**: 30 minutos exigidos entre o término de um Período e o início de outro no mesmo Ambiente_Relacionado.
- **Configuração**: parâmetros de antecedência mínima, faixa de horário global e por Unidade_Macro e endpoint do SNP.
- **Antecedência_Mínima**: número inteiro de minutos entre o instante atual e o início de um Período.
- **Faixa_Horária**: horário mínimo e máximo (HH:mm) aceito para início e término de Períodos.
- **Versão_Reserva**: snapshot imutável do estado de uma Reserva após cada inclusão, alteração ou cancelamento.
- **Pedido_SNP**: pedido registrado no SNP simulado, com número e link.
- **Notificação**: registro de e-mail gerado para um Setor_Envolvido.
- **Seed**: carga inicial a partir dos CSVs de `dados/`.

## Requirements

### Requisito 1 — Estrutura do projeto e stack

**User Story:** Como equipe de desenvolvimento, quero um monorepo com stack padronizada, para atender às restrições técnicas do caso de uso.

#### Critérios de Aceitação

1. THE SISGARES SHALL ser organizado em monorepo com os diretórios `frontend/`, `backend/`, `docs/`, `data/` e `infra/`.
2. THE Backend SHALL ser implementado em Java 21 com Spring Boot e build Maven.
3. THE Frontend SHALL ser implementado em Angular.
4. WHERE o perfil de execução `local` estiver ativo, THE Backend SHALL usar banco H2 ou PostgreSQL local.
5. WHERE o perfil de execução `aws` estiver ativo, THE Backend SHALL usar PostgreSQL hospedado na AWS.
6. THE Backend SHALL expor a API REST sob o prefixo `/api` com corpo JSON e datas no formato ISO-8601 com fuso `America/Fortaleza`.
7. IF uma requisição falhar por validação, THEN THE Backend SHALL responder HTTP 422 com corpo JSON contendo `codigo` (ex.: `RN5_CONFLITO_HORARIO`), `mensagem` em português e `campo` quando aplicável.

### Requisito 2 — Modelo de dados

**User Story:** Como equipe, quero um modelo de dados que preserve os CSVs e acrescente o que falta, para suportar todas as regras de negócio.

#### Critérios de Aceitação

1. THE Backend SHALL preservar as tabelas e colunas dos CSVs: AMBI (AMBI_ID, AMBI_DESC, AMBI_ST_ATIVO, AMBI_ID_PAI), DISP (DISP_ID, DISP_DESC, DISP_ST_ATIVO, DISP_ICONE_ARQUIVO), GREC (GREC_ID, GREC_DESC, GREC_ORDEM, GREC_ST_ATIVO), RECU (RECU_ID, RECU_DESC, RECU_GREC_ID, RECU_ST_LIMITADO, RECU_DISPONIBILIDADE, RECU_ST_ATIVO, RECU_ICONE_ARQUIVO), VREC (VREC_ID, VREC_RECU_ID, VREC_AMBI_ID), ENVO (ENVO_ID, ENVO_DESC, ENVO_EMAIL, ENVO_ST_ATIVO), EAMB (EAMB_ID, EAMB_ENVO_ID, EAMB_AMBI_ID), EREC (EREC_ID, EREC_ENVO_ID, EREC_RECU_ID), PRES (PRES_ID, PRES_RESE_ID, PRES_DTHR_INICIO, PRES_DTHR_TERMINO) e SOLI (SOLI_ID, SOLI_RESE_ID, SOLI_RECU_ID, SOLI_QTD).
2. THE Backend SHALL incluir a entidade Unidade_Macro e as colunas de Unidade_Macro em AMBI e ENVO (obrigatória) e em RECU (opcional; nula indica todas as unidades).
3. THE Backend SHALL incluir a coluna opcional de código de serviço do SNP em EAMB e EREC.
4. THE Backend SHALL incluir em ENVO uma coluna opcional de lista de e-mails alternativa.
5. THE Backend SHALL incluir a entidade Reserva (RESE) com RESE_ID, Unidade_Macro, Solicitante, Ambiente (nulo para Local_Proprio), complemento do ambiente, Disposição (opcional), finalidade (texto multilinha), quantidade estimada de participantes, indicador de cancelamento, data/hora de cancelamento e data/hora da última alteração.
6. THE Backend SHALL incluir as entidades Configuração, Pedido_SNP, Notificação, Versão_Reserva e Usuário (com perfil, Unidade_Macro e Setor_Envolvido opcional).
7. THE Backend SHALL armazenar as colunas `*_ST_*` com os valores "S" ou "N".
8. THE SISGARES SHALL disponibilizar em `docs/` o diagrama ER (Mermaid ou imagem) com todas as entidades e relacionamentos dos critérios 1 a 6.

### Requisito 3 — Seed a partir dos CSVs

**User Story:** Como equipe, quero carregar os CSVs fictícios, para demonstrar o sistema com dados realistas.

#### Critérios de Aceitação

1. WHEN o Backend iniciar com o banco vazio, THE Backend SHALL carregar os 10 CSVs de `data/` preservando todos os IDs originais.
2. THE Backend SHALL interpretar IDs com ponto de milhar removendo o ponto (ex.: "14.207" → 14207).
3. THE Backend SHALL interpretar datas no formato `dd/MM/yyyy HH:mm:ss` (ex.: "20/10/2026 11:00:00").
4. WHEN SOLI_QTD estiver vazio, THE Backend SHALL gravar a quantidade como nula para Recurso não limitado e como 1 para Recurso_Limitado.
5. THE Backend SHALL criar a Unidade_Macro "PR/CE" e associar a ela todos os ambientes, setores e usuários do Seed, mantendo os recursos sem Unidade_Macro.
6. THE Backend SHALL criar uma Reserva para cada RESE_ID distinto referenciado em PRES ou SOLI, preenchendo valores fictícios: finalidade "Reserva importada (seed)", 10 participantes, Solicitante fictício distribuído entre pelo menos 3 usuários Solicitantes e Ambiente ativo atribuído de forma determinística sem gerar conflito (RN5/RN6), ou Local_Proprio com complemento "Local próprio (seed)" quando não houver Ambiente livre.
7. THE Backend SHALL criar a Configuração padrão: Antecedência_Mínima 120 minutos, Faixa_Horária global 07:00–20:00 e endpoint do SNP simulado local.
8. THE Backend SHALL atribuir código de serviço fictício a pelo menos um vínculo EREC e deixar pelo menos um vínculo sem código, para demonstrar RN11.
9. IF uma linha de CSV referenciar ID inexistente ou tiver formato inválido, THEN THE Backend SHALL registrar no log o arquivo, a linha e o motivo, ignorar a linha e continuar a carga.
10. WHEN a carga for executada duas vezes sobre o mesmo banco, THE Backend SHALL manter o mesmo número de registros da primeira carga (idempotência).

### Requisito 4 — Autenticação e perfis

**User Story:** Como usuário, quero entrar com meu perfil, para acessar somente as funções permitidas.

#### Critérios de Aceitação

1. WHERE o perfil `aws` estiver ativo, THE SISGARES SHALL autenticar usuários via AWS Cognito com tokens JWT.
2. WHERE o perfil `local` estiver ativo, THE SISGARES SHALL autenticar via mock local com usuários fictícios de cada perfil (Administrador, Setor_Atendente, Solicitante).
3. THE Backend SHALL suportar exatamente os perfis Administrador, Setor_Atendente e Solicitante.
4. IF uma requisição à API chegar sem token válido, THEN THE Backend SHALL responder HTTP 401.
5. IF um usuário acessar operação não permitida ao perfil, THEN THE Backend SHALL responder HTTP 403.
6. THE Backend SHALL permitir ao Solicitante alterar e cancelar apenas reservas em que ele é o Solicitante.
7. THE Backend SHALL permitir ao Administrador consultar, alterar e cancelar todas as reservas da própria Unidade_Macro.
8. THE Backend SHALL permitir ao Setor_Atendente consultar as reservas cujo Ambiente ou Recursos estejam vinculados ao Setor_Envolvido do usuário.

### Requisito 5 — Cadastros de tabelas básicas (F9, Desejável)

**User Story:** Como Administrador, quero manter setores, ambientes, disposições, grupos e recursos com seus vínculos, para manter as tabelas da unidade.

#### Critérios de Aceitação

1. THE Backend SHALL oferecer ao Administrador inclusão, alteração, consulta e inativação (ST_ATIVO = "N") de Setor_Envolvido, Ambiente, Disposição, Grupo_Recurso e Recurso.
2. THE Backend SHALL exigir em Setor_Envolvido descrição, Unidade_Macro e e-mail padrão, aceitando opcionalmente uma lista de e-mails alternativa.
3. IF algum e-mail informado em Setor_Envolvido tiver formato inválido, THEN THE Backend SHALL responder HTTP 422.
4. THE Backend SHALL exigir em Ambiente descrição e Unidade_Macro, aceitando opcionalmente um ambiente pai.
5. IF o ambiente pai pertencer a outra Unidade_Macro ou for o próprio Ambiente ou um de seus descendentes, THEN THE Backend SHALL responder HTTP 422.
6. THE Backend SHALL permitir vincular um Ambiente a um ou mais Setor_Envolvido da mesma Unidade_Macro (EAMB), com código de serviço do SNP opcional.
7. THE Backend SHALL exigir em Disposição descrição e uma imagem única.
8. THE Backend SHALL exigir em Grupo_Recurso descrição e aceitar ordem numérica (GREC_ORDEM).
9. THE Frontend SHALL listar recursos agrupados por Grupo_Recurso em ordem crescente de GREC_ORDEM (seed: Serviço 1, Estrutura 2, Equipamento 3).
10. THE Backend SHALL exigir em Recurso descrição, exatamente um Grupo_Recurso e um ícone, aceitando Unidade_Macro opcional.
11. IF um Recurso for marcado como limitado com disponibilidade menor que 1, THEN THE Backend SHALL responder HTTP 422.
12. THE Backend SHALL permitir vincular um Recurso a um ou mais Setor_Envolvido (EREC), com código de serviço do SNP opcional, e a um ou mais Ambientes (VREC).
13. THE Backend SHALL omitir registros com ST_ATIVO = "N" das opções oferecidas no cadastro de reservas, mantendo-os nas reservas existentes.

### Requisito 6 — Configurações (F10, Desejável)

**User Story:** Como Administrador, quero configurar antecedência, faixa de horário e endpoint do SNP, para ajustar regras sem alterar código.

#### Critérios de Aceitação

1. THE Backend SHALL permitir ao Administrador definir a Antecedência_Mínima como inteiro entre 0 e 10080 minutos.
2. THE Backend SHALL permitir ao Administrador definir a Faixa_Horária global e, opcionalmente, uma Faixa_Horária por Unidade_Macro.
3. IF o horário mínimo de uma Faixa_Horária for igual ou posterior ao horário máximo, THEN THE Backend SHALL responder HTTP 422.
4. THE Backend SHALL permitir ao Administrador definir a URL do endpoint do SNP.
5. IF a URL do endpoint do SNP não iniciar com `http://` ou `https://`, THEN THE Backend SHALL responder HTTP 422.
6. WHEN uma Configuração for salva, THE Validador_Reserva SHALL aplicar os novos valores às validações seguintes sem reinício do Backend.

### Requisito 7 — Cadastro de reserva (F1, Essencial)

**User Story:** Como Solicitante, quero cadastrar uma reserva de ambiente e/ou recursos, para garantir a estrutura do meu evento.

#### Critérios de Aceitação

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
14. WHEN uma Reserva válida for salva, THE Backend SHALL responder HTTP 201 com a Reserva, gravar a primeira Versão_Reserva e acionar o Notificador e o Cliente_SNP.
15. WHEN a validação encontrar mais de uma violação, THE Backend SHALL retornar todas as violações na mesma resposta HTTP 422.
16. THE Backend SHALL calcular o status da Reserva como "cancelada" se cancelada; senão "prevista" se o instante atual for anterior ao primeiro início; "transcorrida" se o instante atual for igual ou posterior ao último término; e "em andamento" nos demais casos (exemplo: agora 10:00, período 09:00–11:00 → em andamento).

### Requisito 8 — Conflitos de horário (F2, Essencial)

**User Story:** Como Solicitante, quero ser avisado de conflitos ao escolher cada período e ao salvar, para não reservar um espaço ocupado.

#### Critérios de Aceitação

1. THE Validador_Reserva SHALL considerar conflitantes dois Períodos A e B quando A.início < B.término + 30 min e B.início < A.término + 30 min.
2. IF um Período da Reserva conflitar com um Período de outra Reserva não cancelada do mesmo Ambiente, THEN THE Validador_Reserva SHALL bloquear com código `RN5_CONFLITO_HORARIO`, informando o Período e a Reserva conflitante (exemplo: Auditório 09:00–11:00 reservado; nova 11:20–12:00 → bloqueado; nova 11:30–12:00 → aceito).
3. IF um Período da Reserva conflitar com um Período de outra Reserva não cancelada de um Ambiente_Relacionado, THEN THE Validador_Reserva SHALL bloquear com código `RN6_CONFLITO_PAI_FILHO` (exemplo: Auditório (Parte A), filho do Auditório (Completo), reservado 14:00–16:00; nova reserva do Auditório (Completo) 15:00–17:00 → bloqueado).
4. THE Validador_Reserva SHALL desconsiderar conflitos para Reservas com Local_Proprio.
5. WHEN o Solicitante concluir o preenchimento de um Período no Frontend, THE Frontend SHALL consultar o Backend e exibir o aviso de conflito junto ao Período em até 2 segundos.
6. WHEN uma Reserva for salva, THE Validador_Reserva SHALL refazer a verificação completa de conflitos dentro da mesma transação da gravação (exemplo: dois usuários preenchem o Auditório 09:00–10:00; o primeiro salva; o segundo, ao salvar → bloqueado).
7. WHEN duas gravações concorrentes disputarem o mesmo Ambiente_Relacionado, THE Backend SHALL gravar no máximo uma delas e responder HTTP 409 com código `RN7_CONFLITO_AO_SALVAR` à outra.
8. WHEN uma Reserva existente for alterada, THE Validador_Reserva SHALL desconsiderar os Períodos da própria Reserva na verificação.

### Requisito 9 — Recursos (F3, Essencial)

**User Story:** Como Solicitante, quero ser impedido de pedir mais unidades de um recurso limitado do que as disponíveis, para que o pedido possa ser atendido.

#### Critérios de Aceitação

1. THE Frontend SHALL exibir o campo de quantidade somente para Recurso_Limitado.
2. IF a quantidade solicitada de um Recurso_Limitado for menor que 1, THEN THE Validador_Reserva SHALL bloquear com código `RN8_QUANTIDADE_INVALIDA`.
3. IF, para algum Período da Reserva, a soma da quantidade solicitada com as quantidades do mesmo Recurso_Limitado em outras Reservas não canceladas com Períodos que se cruzam (sem Margem) exceder RECU_DISPONIBILIDADE, THEN THE Validador_Reserva SHALL bloquear com código `RN8_RECURSO_INSUFICIENTE` informando a quantidade disponível (exemplo: 3 projetores, 2 reservados 14:00–16:00; nova pede 2 para 15:00–17:00 → bloqueado; pede 1 → aceito).
4. THE Backend SHALL oferecer em uma Reserva somente Recursos ativos sem Unidade_Macro ou com a Unidade_Macro da Reserva.
5. WHERE um Recurso tiver vínculos VREC, THE Backend SHALL oferecer o Recurso somente em Reservas de Ambientes vinculados (exemplo: Videoconferência (CODEC 01), vinculado à Sala de Reuniões - 9º andar, não aparece ao reservar o Auditório (Completo)).
6. IF a Reserva solicitar Recurso fora da Unidade_Macro ou de Ambiente não vinculado, THEN THE Validador_Reserva SHALL bloquear com código `RN9_RECURSO_INDISPONIVEL`.
7. WHEN o Ambiente da Reserva for alterado, THE Frontend SHALL remover as solicitações de Recursos não permitidos no novo Ambiente e avisar o Solicitante.

### Requisito 10 — Alteração e cancelamento (F4, Essencial)

**User Story:** Como Solicitante, quero alterar ou cancelar uma reserva não transcorrida, para manter o pedido atualizado.

#### Critérios de Aceitação

1. IF uma alteração for solicitada para Reserva com status "transcorrida" ou "cancelada", THEN THE Backend SHALL bloquear com HTTP 422 e código `RN12_RESERVA_NAO_EDITAVEL`.
2. WHEN uma Reserva for alterada, THE Validador_Reserva SHALL aplicar as mesmas validações do cadastro (RN1–RN9).
3. WHILE uma Reserva estiver "em andamento", THE Validador_Reserva SHALL aplicar RN4 apenas aos Períodos novos ou com início alterado.
4. WHEN uma alteração válida for salva, THE Backend SHALL gravar nova Versão_Reserva, atualizar a data/hora da última alteração e acionar o Notificador.
5. WHEN o Solicitante pedir o cancelamento, THE Frontend SHALL exibir diálogo de confirmação acessível antes de enviar o pedido.
6. IF o cancelamento ocorrer sem confirmação explícita no pedido, THEN THE Backend SHALL responder HTTP 422 com código `RN12_CONFIRMACAO_OBRIGATORIA`.
7. IF o instante atual somado à Antecedência_Mínima for posterior ao primeiro início da Reserva, THEN THE Backend SHALL bloquear o cancelamento com código `RN12_CANCELAMENTO_SEM_ANTECEDENCIA`.
8. WHEN um cancelamento válido for confirmado, THE Backend SHALL marcar a Reserva como cancelada, gravar nova Versão_Reserva, liberar os Períodos para novas reservas e acionar o Notificador.

### Requisito 11 — Notificações por e-mail (F5, Essencial)

**User Story:** Como Setor_Envolvido, quero receber e-mail de cada reserva nova, alterada ou cancelada que me envolva, com alterações destacadas, para preparar o atendimento.

#### Critérios de Aceitação

1. WHEN uma Reserva for incluída, alterada ou cancelada, THE Notificador SHALL gerar uma Notificação para cada Setor_Envolvido ativo vinculado ao Ambiente (EAMB) ou a algum Recurso solicitado (EREC), sem duplicar setores.
2. WHERE o Setor_Envolvido tiver lista de e-mails alternativa, THE Notificador SHALL enviar para a lista; nos demais casos, THE Notificador SHALL enviar para ENVO_EMAIL.
3. THE Notificador SHALL incluir no e-mail o tipo do evento (inclusão, alteração ou cancelamento), Ambiente ou complemento, finalidade, participantes, Disposição, Períodos, Recursos com quantidades, Solicitante e números de Pedido_SNP.
4. WHEN uma Reserva for alterada, THE Notificador SHALL gerar e-mail em HTML que exibe, para cada campo alterado em relação à Versão_Reserva anterior, o valor antigo e o novo com estilo de destaque (exemplo: horário de 14:00 para 15:00 → e-mail mostra 14:00 e 15:00 em destaque).
5. WHERE o perfil `local` estiver ativo, THE Notificador SHALL registrar os e-mails em caixa simulada consultável pelo Administrador, sem envio externo.
6. IF o envio de um e-mail falhar, THEN THE Notificador SHALL registrar a falha na Notificação e manter a Reserva gravada.
7. THE Notificador SHALL usar somente endereços de e-mail fictícios provenientes do Seed ou de cadastro.

### Requisito 12 — Pedido no SNP simulado (F6, Desejável)

**User Story:** Como Setor_Envolvido, quero que a reserva gere pedido no SNP quando o vínculo tiver código de serviço, para formalizar a demanda.

#### Critérios de Aceitação

1. WHEN uma Reserva for incluída, THE Cliente_SNP SHALL registrar um Pedido_SNP para cada vínculo EAMB ou EREC envolvido que tenha código de serviço (exemplo: projetor vinculado à TI com código → pedido gerado).
2. THE Cliente_SNP SHALL ignorar vínculos sem código de serviço, que recebem apenas e-mail (exemplo: água vinculada à copa sem código → só e-mail).
3. THE Cliente_SNP SHALL enviar os pedidos ao endpoint configurado em Configuração, atendido por um simulador interno que retorna número do pedido e link.
4. WHEN uma Reserva for alterada e um novo vínculo com código de serviço passar a ser envolvido, THE Cliente_SNP SHALL registrar Pedido_SNP apenas para o novo vínculo.
5. IF o SNP simulado estiver indisponível ou responder erro, THEN THE Cliente_SNP SHALL registrar a falha no Pedido_SNP e manter a Reserva gravada.
6. THE Frontend SHALL exibir o número de cada Pedido_SNP como link para o pedido no simulador.

### Requisito 13 — Painel do Solicitante (F7, Essencial)

**User Story:** Como Solicitante, quero uma grade de datas por horários de 30 minutos para o ambiente escolhido, para encontrar rapidamente um horário livre.

#### Critérios de Aceitação

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
11. THE Backend SHALL calcular o estado de cada célula com as mesmas regras de Margem, Antecedência_Mínima e Faixa_Horária do Validador_Reserva.

### Requisito 14 — Painel do Atendente (F8, Essencial)

**User Story:** Como Setor_Atendente, quero cards das reservas por data, para planejar o atendimento dos próximos dias.

#### Critérios de Aceitação

1. THE Frontend SHALL exibir no topo do painel a data de referência (inicial: data corrente), o número de colunas (1 a 14, padrão 7) e a opção de exibir fins de semana (padrão: não).
2. THE Frontend SHALL exibir em cada coluna de data um card para cada Reserva com algum Período naquela data, ordenado por horário de início.
3. THE Frontend SHALL exibir no card horário, finalidade, nome do Solicitante, Recursos com quantidades, números de Pedido_SNP como links e link para a tela da Reserva.
4. WHILE o usuário tiver perfil Setor_Atendente, THE Backend SHALL retornar ao painel somente Reservas que envolvam o Setor_Envolvido do usuário.
5. WHILE o usuário tiver perfil Administrador, THE Backend SHALL retornar ao painel todas as Reservas da Unidade_Macro.
6. THE Frontend SHALL diferenciar cards de Reservas canceladas com texto "Cancelada", além de cor.

### Requisito 15 — Responsividade, acessibilidade e LGPD

**User Story:** Como usuário, quero uma interface acessível em computador e celular, com meus dados protegidos.

#### Critérios de Aceitação

1. THE Frontend SHALL ser utilizável sem rolagem horizontal da página em larguras de 360 px a 1920 px, permitindo rolagem interna apenas nas grades dos painéis.
2. THE Frontend SHALL seguir o eMAG e as WCAG 2.1 nível AA.
3. THE Frontend SHALL permitir operar todas as funções por teclado, com foco visível.
4. THE Frontend SHALL apresentar contraste mínimo de 4,5:1 para texto normal e 3:1 para texto grande e componentes de interface.
5. THE Frontend SHALL associar rótulo visível a todos os campos de formulário e anunciar mensagens de erro de validação a leitores de tela.
6. THE Frontend SHALL transmitir estados da grade (livre, ocupado, margem, ultrapassado, sem antecedência) por texto, além de cor.
7. THE SISGARES SHALL usar somente dados fictícios.
8. THE Backend SHALL retornar o nome do Solicitante somente ao próprio Solicitante, ao Administrador e ao Setor_Atendente envolvido na Reserva, e retornar o Período ocupado sem dados pessoais aos demais usuários.

### Requisito 16 — Testes e documentação

**User Story:** Como equipe, quero testes automatizados e documentação, para comprovar as regras e permitir a execução da solução.

#### Critérios de Aceitação

1. THE Backend SHALL conter testes JUnit 5 que reproduzem cada exemplo da tabela de RN1 a RN13 como caso de teste.
2. THE Backend SHALL conter testes de propriedade com jqwik para: simetria da detecção de conflito (A conflita com B se e somente se B conflita com A); conflito pela Margem para intervalos menores que 30 minutos e ausência de conflito para intervalos iguais ou maiores; propagação de conflito entre pai e filho; soma de quantidades nunca acima de RECU_DISPONIBILIDADE para Reservas aceitas; e exclusividade e totalidade do status (RN13).
3. THE Backend SHALL conter teste de round-trip do leitor de CSV: ler, formatar e ler novamente produz registros equivalentes, incluindo IDs com ponto de milhar e datas `dd/MM/yyyy HH:mm:ss`.
4. THE Backend SHALL conter testes de integração para RN7 (gravação concorrente), RN10 (setores notificados) e RN11 (Pedido_SNP somente com código de serviço).
5. THE Backend SHALL usar relógio injetável nos testes para fixar o instante atual.
6. WHEN `mvn test` for executado em `backend/`, THE Backend SHALL executar todos os testes sem dependência de serviços externos.
7. THE SISGARES SHALL conter README na raiz com descrição da solução, pré-requisitos, comandos de execução local do Backend e do Frontend, usuários de teste e comandos de teste.
8. THE SISGARES SHALL conter em `docs/` a descrição da arquitetura e o diagrama ER.

### Requisito 17 — Infraestrutura AWS

**User Story:** Como equipe, quero a infraestrutura preparada para AWS, para publicar a solução no ambiente do hackathon.

#### Critérios de Aceitação

1. THE SISGARES SHALL conter em `infra/` definição de infraestrutura como código para PostgreSQL gerenciado, Cognito (user pool com grupos dos 3 perfis), hospedagem do Backend e hospedagem estática do Frontend.
2. THE SISGARES SHALL conter Dockerfile do Backend.
3. THE Backend SHALL ler credenciais e URLs de variáveis de ambiente ou de serviço de segredos, sem segredos versionados no repositório.
4. THE SISGARES SHALL documentar em `infra/README.md` os passos de implantação e destruição dos recursos.
