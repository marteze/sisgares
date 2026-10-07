---
inclusion: always
---

# Segurança e LGPD

- IAM de privilégio mínimo: cada Lambda só com as ações e recursos (ARN) que usa; sem `*` em ações ou recursos.
- Nenhum segredo em código, configuração versionada ou commit: use Secrets Manager/SSM e variáveis de ambiente. Autenticação AWS segue `autenticacao.md`.
- Logs estruturados sem dados pessoais: CPF, e-mail, telefone e nomes passam por `MascaradorDados.mascarar`. Nunca registre tokens.
- Todo texto vindo do usuário exibido em HTML ou e-mail passa por `EscapadorHtml.escapar`.
- Somente dados fictícios em seeds, testes e exemplos (domínios como `exemplo.gov.br`, CPFs gerados). Dados reais são proibidos no repositório.
- Autorização verificada no backend (Cognito + Verified Permissions); o frontend apenas esconde opções.
- Dados em repouso criptografados (KMS) e em trânsito via TLS.
