---
inclusion: always
---

# Arquitetura

- Serverless na AWS: API Gateway + Lambda (Java), DynamoDB, Step Functions, SNS/SQS, SES, Cognito; infraestrutura em CDK. Sem servidores dedicados.
- Orientada a eventos: alterações de reserva gravam evento no outbox na mesma `TransactWriteItems`; consumidores (notificação, integração SNP) são assíncronos, idempotentes e com DLQ.
- DynamoDB single-table: chaves `PK`/`SK` e GSIs documentados em `docs/`; novos padrões de acesso exigem atualizar o modelo antes do código.
- Módulos Maven em `backend/`: `dominio` (regras puras), `comum-aws` (adaptadores AWS) e contextos (`catalogo`, `configuracao`, `eventos`, `exportacao`, `assistente`, …). Dependências: contextos → `comum-aws` → `dominio`, nunca o inverso.
- Regras de negócio só no `dominio`; handlers apenas traduzem HTTP/eventos e persistem.
- API REST sob `/api`, JSON, datas ISO-8601; contrato em `docs/openapi.yaml`.
