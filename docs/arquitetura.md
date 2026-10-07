# Arquitetura do SISGARES

Solução serverless na AWS (região `us-east-1`), provisionada em CDK Java (`infra/`), com cinco stacks:
`SegurancaStack` (CMK KMS e CloudTrail), `DadosStack` (tabela DynamoDB `sisgares` e buckets S3),
`ApiStack` (HTTP API, Lambdas de API, Verified Permissions), `EventosStack` (EventBridge, Step Functions,
consumidores e DLQ) e `FrontStack` (CloudFront com Origin Access Control para o Angular).

## Diagrama AWS

```mermaid
flowchart LR
    U[Usuário<br/>navegador] -->|HTTPS| CF[CloudFront + OAC]
    CF --> S3F[(S3 BucketFrontend<br/>Angular)]
    U -->|Hosted UI / PKCE| COG[Cognito User Pool<br/>grupos Solicitante,<br/>Setor_Atendente, Administrador]
    U -->|HTTPS + Bearer JWT| API[API Gateway HTTP API<br/>/api/*<br/>authorizer JWT]

    subgraph ApiStack
      API --> LR[Lambda reservas]
      API --> LP[Lambda paineis]
      API --> LC[Lambda catalogo]
      API --> LCF[Lambda configuracao]
      AVP[Verified Permissions<br/>políticas Cedar]
    end

    LR & LP & LC & LCF -->|IsAuthorized| AVP
    LR & LP & LC & LCF --> DDB[(DynamoDB sisgares<br/>single-table + 5 GSIs<br/>TTL expiraEm, PITR)]
    LC --> S3I[(S3 BucketImagens)]

    subgraph EventosStack
      EB[EventBridge<br/>Barramento] -->|RegraReserva<br/>detail-type Reserva*| SF[Step Functions<br/>FluxoPosReserva]
      SF -->|ramo paralelo| NOT[Lambda Notificador]
      SF -->|ramo paralelo| SNP[Lambda Cliente SNP]
      SNP --> MOCK[Lambda SNP simulado]
      SF -->|falha após retries| DLQ[[SQS DlqFluxoPosReserva<br/>KMS]]
      AG[Regra agendada EventBridge<br/>rate 1 min] --> REP[Lambda Republicador<br/>do OUTBOX]
      REP --> EB
      ALM[CloudWatch Alarms] --> TA[SNS TopicoAlarmes]
    end

    LR -->|PutEvents após commit| EB
    NOT --> SES[Amazon SES]
    NOT & SNP & REP --> DDB
    KMS[KMS CMK] -.cifra.-> DDB & S3F & S3I & DLQ
    CT[CloudTrail] -.auditoria.-> KMS
```

Pontos principais:

- Autenticação no Cognito; autorização no backend por Amazon Verified Permissions com as políticas de
  `infra/cedar/` (`criar-reserva`, `dono-reserva`, `atendente-setor`, `admin-reservas-unidade`,
  `admin-cadastros-unidade`, `admin-configuracao`, `proibir-manutencao-nao-admin`). O frontend apenas esconde opções.
- Cada Lambda tem papel IAM próprio, restrito às ações e ARNs que usa; logs no CloudWatch com retenção de 90 dias.
- Dados em repouso cifrados pela CMK (DynamoDB, S3, SQS, CloudTrail); tráfego somente via TLS.

## Fluxo de eventos

A gravação da reserva e do evento no `OUTBOX` ocorre na mesma `TransactWriteItems`. Após o commit, a Lambda
de reservas publica o Evento_Reserva no EventBridge e remove o item do outbox; se a publicação falhar, o
Republicador (agendado a cada minuto) reenvia os pendentes. O evento contém apenas ID do evento, `RESE_ID`,
número da versão e tipo, sem dados pessoais.

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant A as API Gateway
    participant R as Lambda reservas
    participant P as Verified Permissions
    participant D as DynamoDB
    participant E as EventBridge
    participant S as Step Functions
    participant N as Notificador
    participant C as Cliente SNP
    participant Q as SQS DLQ

    F->>A: POST /api/reservas (Bearer JWT)
    A->>R: evento HTTP v2 (JWT validado)
    R->>P: IsAuthorized(CriarReserva)
    P-->>R: ALLOW
    R->>D: TransactWriteItems (META, PRES/SOLI, VERS, CTRL condicional, OUTBOX)
    alt conflito de versão / Item_Controle
        D-->>R: TransactionCanceled
        R-->>F: 409 {erros, correlationId}
    else sucesso
        D-->>R: OK
        R->>E: PutEvents (ReservaCriada, ReservaAlterada ou ReservaCancelada)
        R->>D: Delete OUTBOX
        R-->>F: 201 Reserva
    end
    E->>S: RegraReserva (detail-type com prefixo Reserva)
    par ramo Notificador
        S->>N: evento
        N->>D: Put EVT#<eventoId>/Notificador (condicional, idempotência)
        N->>N: EscapadorHtml + diferenças da versão
        N->>D: Put NOTI#<eventoId>#<envoId> (TTL 90 dias)
    and ramo Cliente SNP
        S->>C: evento
        C->>D: Put EVT#<eventoId>/ClienteSnp (condicional)
        C->>D: Put SNP#<vinculo> (somente recursos com código de serviço)
    end
    S-->>Q: falha após retries (Catch)
    Note over E,D: Republicador (rate 1 min) reenvia itens restantes no OUTBOX
```

Consumidores são idempotentes: o item `EVT#<eventoId>` com SK igual ao nome do consumidor é gravado com
`attribute_not_exists`; reprocessar o mesmo evento não gera e-mail nem pedido SNP duplicado.
