---
inclusion: always
---

# Idioma (complemento)

Complementa `language.md` (regra base: tudo em português do Brasil). Não substitui nem altera aquele arquivo.

## Convenções de nomes

- Classes, métodos, variáveis e rotas em português: `Reserva`, `validar`, `periodoOcupado`, `/api/reservas`.
- Sem acentos ou cedilha em identificadores: `Configuracao`, `Solicitacao`, `ambienteId`.
- Termos técnicos consagrados ficam em inglês: `handler`, `record`, `stream`, `outbox`, `DTO`.
- Enums em MAIÚSCULAS com sublinhado: `SEM_ANTECEDENCIA`, `ICONE_AMBIGUO`.

## Mensagens de erro

- Mensagens ao usuário em português claro, com o código da regra quando houver: `RN5: o ambiente já está reservado neste período.`
- Indique o que fazer para corrigir; não exponha stack trace, IDs internos ou dados pessoais.

## Glossário

- Reserva: solicitação de uso de ambiente e/ou recursos em um período.
- Ambiente: sala, auditório ou espaço reservável (organizado em árvore).
- Recurso: item de quantidade limitada (projetor, notebook etc.).
- Local_Proprio: local do solicitante, sem verificação de conflito.
- Margem: intervalo de 30 minutos entre reservas do mesmo ambiente ou de ambientes relacionados.
- Painel: visão de reservas por setor ou ambiente.
