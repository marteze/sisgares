# Uso do Kiro no desenvolvimento

## Spec

`.kiro/specs/sisgares-reservas/` concentra o trabalho:

- `requirements.md`: 23 requisitos em EARS, agrupados de A a H (estrutura, segurança, cadastros, reservas,
  eventos, painéis, IA/Kiro, qualidade), com glossário e regras RN1 a RN13.
- `design.md`: arquitetura, modelo single-table, gravação atômica com outbox, fluxo de eventos e
  propriedades de corretude usadas nos testes baseados em propriedades (jqwik).
- `tasks.md`: plano incremental; cada tarefa referencia os critérios que atende e foi executada uma de cada vez
  pelo agente, com testes e build antes da conclusão.

## Steering (`.kiro/steering/`)

| Arquivo | Uso |
|---|---|
| `language.md`, `idioma.md` | Tudo em português do Brasil; identificadores sem acento, enums em MAIÚSCULAS, mensagens com código da regra (`RN5: ...`) |
| `arquitetura.md` | Serverless, orientada a eventos, single-table, dependências `contextos → comum-aws → dominio`, contrato em `docs/openapi.yaml` |
| `java.md` | Padrões de código Java 21 do backend e do CDK |
| `angular.md` | Padrões do frontend Angular (standalone, signals, acessibilidade) |
| `seguranca-lgpd.md` | IAM mínimo, sem segredos, `MascaradorDados`, `EscapadorHtml`, dados fictícios, autorização no backend |
| `autenticacao.md` | Profile AWS `hackaton`, região `us-east-1` e verificação com `sts get-caller-identity` antes de provisionar |

`java.md` e `angular.md` usam `inclusion: fileMatch` (carregados ao editar `backend/**/*.java` e `frontend/**`); os demais são incluídos sempre. Isso manteve nomes, mensagens e segurança consistentes entre tarefas.

## Hooks (`.kiro/hooks/`)

| Hook | Gatilho | Ação |
|---|---|---|
| `testes-dominio` | Salvar `backend/**/*.java` | Executa `mvn -q -f backend/pom.xml -pl dominio test` (Req. 19.3) |
| `verifica-segredos-pii` | Salvar qualquer arquivo | Agente procura chaves AWS, tokens, senhas, CPFs e e-mails reais e propõe correção (Req. 19.4) |
| `atualiza-openapi-docs` | Salvar handlers de `reservas`, `paineis`, `catalogo`, `configuracao`, `assistente`, `exportacao` | Agente avalia mudança no contrato REST e atualiza `docs/openapi.yaml` e demais documentos de `docs/` (Req. 19.5) |

## Fluxo de trabalho

1. Requisitos escritos e revisados na spec; design derivado deles com as propriedades de corretude.
2. Tarefas executadas por subagentes, que leem requirements, design e tasks antes de implementar.
3. Hooks rodam os testes do domínio e a varredura de segredos a cada gravação; o hook de OpenAPI mantém
   esta pasta `docs/` sincronizada com os handlers.
4. Provisionamento com `cdk deploy --profile hackaton`, conforme a steering de autenticação.
