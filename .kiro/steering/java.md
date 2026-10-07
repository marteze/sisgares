---
inclusion: fileMatch
fileMatchPattern: "backend/**/*.java"
---

# Java (backend)

- Módulo `dominio` é puro: nenhuma dependência AWS (SDK, Lambda, DynamoDB). Verificado por enforcer; integrações ficam em `comum-aws` e nos contextos.
- Use `record` para valores imutáveis (`Periodo`, `Violacao`, `Conflito`, DTOs). Evite setters e estado mutável compartilhado.
- Nunca chame `LocalDateTime.now()`/`Instant.now()` diretamente: receba `java.time.Clock` por construtor. Fuso padrão `America/Fortaleza`.
- Regras de negócio identificadas pelos códigos `RN1`…`RN13`; cite o código em violações, mensagens e nomes de teste.
- Validações acumulam todas as violações em vez de parar na primeira.
- Testes: JUnit 5 para exemplos e jqwik (`@Property`) para propriedades, com `**Validates: Requirements X.Y**` no Javadoc do teste. Geradores restritos ao domínio válido.
- Comentários e Javadoc em português.
