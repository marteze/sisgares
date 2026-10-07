# Modelo de dados

Extraído dos CSVs reais fornecidos. Prefixos de coluna seguem o padrão do sistema
Solare (ex: `AMBI_` = ambiente). Abaixo, cada entidade com seus campos e relações.

## Entidades

### AMBIENTE (`dados-ambiente.csv`)
Ambiente físico reservável. Pode ter hierarquia (um auditório dividido em partes).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `AMBI_ID` | int (PK) | Identificador |
| `AMBI_DESC` | texto | Nome do ambiente |
| `AMBI_ST_ATIVO` | S/N | Ativo? |
| `AMBI_ID_PAI` | int (FK → AMBIENTE) | Ambiente pai (nulo se raiz) |

### DISPOSICAO (`dados-disposicao.csv`)
Layout possível de um ambiente (auditório, espinha de peixe, mesas em U...).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `DISP_ID` | int (PK) | Identificador |
| `DISP_DESC` | texto | Nome da disposição |
| `DISP_ST_ATIVO` | S/N | Ativo? |
| `DISP_ICONE_ARQUIVO` | texto | Arquivo do ícone |

### GRUPO_RECURSO (`dados-grupo-recurso.csv`)
Categoria de recurso: Serviço, Equipamento, Estrutura.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `GREC_ID` | int (PK) | Identificador |
| `GREC_DESC` | texto | Nome do grupo |
| `GREC_ORDEM` | int | Ordem de exibição |
| `GREC_ST_ATIVO` | S/N | Ativo? |

### RECURSO (`dados-recurso.csv`)
Recurso ou serviço reservável (água, café, projetor, notebook, som...).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `RECU_ID` | int (PK) | Identificador |
| `RECU_DESC` | texto | Nome do recurso |
| `RECU_GREC_ID` | int (FK → GRUPO_RECURSO) | Grupo |
| `RECU_ST_LIMITADO` | S/N | Tem quantidade limitada? |
| `RECU_DISPONIBILIDADE` | int | Quantidade disponível (0 = ilimitado/não controlado) |
| `RECU_ST_ATIVO` | S/N | Ativo? |
| `RECU_ICONE_ARQUIVO` | texto | Arquivo do ícone |

### ENVOLVIDO (`dados-envolvido.csv`)
Setor responsável por fornecer recursos/serviços. Recebe notificação por e-mail.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `ENVO_ID` | int (PK) | Identificador |
| `ENVO_DESC` | texto | Nome do setor |
| `ENVO_EMAIL` | texto | E-mail para notificação |
| `ENVO_ST_ATIVO` | S/N | Ativo? |

### RESERVA (inferida)
Não há arquivo próprio. Agrupa um evento: solicitante, ambiente, status, períodos e
solicitações de recursos. `RESE_ID` aparece em `periodo-reserva` e `solicitacao`.
Campos propostos para a modelagem da aplicação:

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `RESE_ID` | int (PK) | Identificador |
| `RESE_TITULO` | texto | Título/evento |
| `RESE_AMBI_ID` | int (FK → AMBIENTE, opcional) | Ambiente reservado |
| `RESE_DISP_ID` | int (FK → DISPOSICAO, opcional) | Disposição escolhida |
| `RESE_SOLICITANTE` | texto | Usuário solicitante (mock) |
| `RESE_STATUS` | enum | RASCUNHO, SOLICITADA, APROVADA, RECUSADA, CANCELADA |

### PERIODO_RESERVA (`dados-periodo-reserva.csv`)
Um intervalo de data/hora de uma reserva (uma reserva pode ter vários períodos).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `PRES_ID` | int (PK) | Identificador |
| `PRES_RESE_ID` | int (FK → RESERVA) | Reserva |
| `PRES_DTHR_INICIO` | datetime | Início (`DD/MM/AAAA HH:MM:SS`) |
| `PRES_DTHR_TERMINO` | datetime | Término |

### SOLICITACAO (`dados-solicitacao.csv`)
Um recurso/serviço pedido dentro de uma reserva.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `SOLI_ID` | int (PK) | Identificador |
| `SOLI_RESE_ID` | int (FK → RESERVA) | Reserva |
| `SOLI_RECU_ID` | int (FK → RECURSO) | Recurso pedido |
| `SOLI_QTD` | int (opcional) | Quantidade |

## Relacionamentos (associativas)

### ENVOLVIDO_AMBIENTE (`dados-envolvido-ambiente.csv`)
Qual setor é responsável por qual ambiente.

| Campo | Tipo |
|-------|------|
| `EAMB_ID` (PK) | int |
| `EAMB_ENVO_ID` (FK → ENVOLVIDO) | int |
| `EAMB_AMBI_ID` (FK → AMBIENTE) | int |

### ENVOLVIDO_RECURSO (`dados-envolvido-recurso.csv`)
Qual setor é responsável por qual recurso — base da notificação por e-mail.

| Campo | Tipo |
|-------|------|
| `EREC_ID` (PK) | int |
| `EREC_ENVO_ID` (FK → ENVOLVIDO) | int |
| `EREC_RECU_ID` (FK → RECURSO) | int |

### VINCULO_RECURSO (`dados-vinculo-recurso.csv`)
Recurso fixo que já existe em um ambiente (ex: projetor fixo no auditório).

| Campo | Tipo |
|-------|------|
| `VREC_ID` (PK) | int |
| `VREC_RECU_ID` (FK → RECURSO) | int |
| `VREC_AMBI_ID` (FK → AMBIENTE) | int |

## Diagrama (texto)

```
AMBIENTE ──┐ (pai/filho via AMBI_ID_PAI)
   │       │
   │       ├──< VINCULO_RECURSO >── RECURSO ──> GRUPO_RECURSO
   │       └──< ENVOLVIDO_AMBIENTE >── ENVOLVIDO >──< ENVOLVIDO_RECURSO >── RECURSO
   │
RESERVA ──< PERIODO_RESERVA
   │   └──< SOLICITACAO >── RECURSO
   ├──> AMBIENTE (opcional)
   └──> DISPOSICAO (opcional)
```

## Regra de notificação (derivada dos dados)

Ao solicitar um recurso (`SOLICITACAO`), o sistema identifica o(s) setor(es)
responsável(is) via `ENVOLVIDO_RECURSO` e, para ambiente, via `ENVOLVIDO_AMBIENTE`,
e dispara notificação por e-mail (simulada) ao `ENVO_EMAIL` correspondente.

## Entidades acrescentadas pela especificação (RF01–RF17)

Além dos CSVs, a aplicação mantém estas entidades/campos (não vêm nos dados):

### UNIDADE_MACRO (RF02/RF06)
`UNID_ID`, `UNID_DESC`. Ambientes e recursos podem pertencer a uma unidade.
Campos acrescentados: `AMBI_UNID_ID`, `RECU_UNID_ID` (null = qualquer unidade).

### Campos de vínculo com o SNP (RF03/RF07/RF14)
`EAMB_SNP_SERVICO` e `EREC_SNP_SERVICO`: código de serviço no catálogo nacional.
Quando presente, a criação da reserva registra um pedido SNP.

### Setor — e-mails extra (RF01)
`ENVO_EMAILS_EXTRA`: lista de e-mails adicionais (separados por `;`).

### RESERVA (RF10) — campos
`RESE_FINALIDADE`, `RESE_PARTICIPANTES`, `RESE_COMPLEMENTO` (obrigatório sem
ambiente), `RESE_DISP_ID`, `RESE_UNID_ID`, `RESE_SOLICITANTE_NOME`,
`RESE_CANCELADA`, `RESE_DTHR_ALTERACAO`. O status é **derivado do horário**
(PREVISTA / EM_ANDAMENTO / TRANSCORRIDA / CANCELADA).

### PEDIDO_SNP (RF14)
`SNP_ID`, `SNP_RESE_ID`, `SNP_CODIGO`, `SNP_SERVICO`, `SNP_SETOR_ID`, `SNP_LINK`.

### CONFIGURACAO (RF09)
`antecedenciaMinimaMin`, `horarioMinGlobal`, `horarioMaxGlobal`,
`margemToleranciaMin` (30), `snpEndpoint`, `porUnidade` (faixa por unidade).

## Regra de conflito (RF11)

Dois períodos conflitam se seus intervalos se interceptam **considerando uma margem
de tolerância de 30 min** em cada extremidade, e se os ambientes são **relacionados**
(o próprio, ancestrais ou descendentes na hierarquia `AMBI_ID_PAI`).
