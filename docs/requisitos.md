# Requisitos (RF01–RF17)

Baseado na especificação detalhada do caso "Solicitação de Ambientes e Recursos".

## Módulo 1 — Tabelas básicas

| RF | Descrição | Situação |
|----|-----------|----------|
| RF01 | Cadastro de Setor Envolvido (caixa postal padrão + lista de e-mails arbitrária) | Modelo + API de leitura; campo `ENVO_EMAILS_EXTRA` |
| RF02 | Cadastro de Ambiente por unidade macro, com hierarquia pai/filho | Modelo + hierarquia usada no conflito |
| RF03 | Ambiente ↔ Setor (com código de serviço SNP opcional) | `EnvolvidoAmbiente.EAMB_SNP_SERVICO` |
| RF04 | Disposições de ambiente com imagem ilustrativa | API + preview na tela de reserva |
| RF05 | Grupos de recurso com ordem de exibição | API ordenada por `GREC_ORDEM` |
| RF06 | Recurso (grupo + ícone + unidade opcional + disponibilidade limitada) | Modelo + filtro por unidade |
| RF07 | Recurso ↔ Setor (com código de serviço SNP opcional) | `EnvolvidoRecurso.EREC_SNP_SERVICO` |
| RF08 | Recurso ↔ Ambiente (restringe o recurso a ambientes) | Filtro em `/recursos?ambienteId=` |
| RF09 | Configurações do admin (antecedência, faixa de horário, endpoint SNP) | Entidade `Configuracao` + tela de config |

## Módulo 2 — Reservas

| RF | Descrição | Situação |
|----|-----------|----------|
| RF10 | Cadastro de reserva (finalidade, participantes, ambiente opcional + complemento, disposição, períodos, recursos; cabeçalho com solicitante, status por horário, última alteração, SNPs) | Tela de reserva + status derivado |
| RF11 | Crítica de períodos conflitantes com hierarquia + margem de 30 min; verificação antecipada e no salvamento | `conflitaComMargem` + `/verificar-conflito` |
| RF12 | Crítica de recursos insuficientes considerando interseções de período | `usoRecursoNoPeriodo` |
| RF13 | Notificações automáticas para setores envolvidos | `notificarSetores` (simulado) |
| RF14 | Registro automático de pedidos no SNP quando há serviço vinculado | `registrarPedidosSNP` (simulado) |
| RF15 | Alteração/cancelamento com revalidação e notificação destacando o diff (HTML) | `alterarReserva`/`cancelarReserva` + `montarDiff` |

## Módulo 3 — Painéis

| RF | Descrição | Situação |
|----|-----------|----------|
| RF16 | Painel-grade do solicitante (colunas=datas, linhas=30min; margem de tolerância, horário ultrapassado, sem antecedência; célula livre vira link "Reservar às XX:XX") | `PainelGrade` + `/paineis/grade/:id` |
| RF17 | Painel do atendente (colunas por data com cards: horário, finalidade, solicitante, recursos, SNP; link para a reserva) | `PainelAtendente` + `/paineis/atendente` |

### Extra — Visão geral de todas as salas

O painel-grade tem um alternador **Por sala / Visão geral**. No modo "Visão geral",
uma tabela mostra todas as salas (linhas) por data (colunas), com as reservas de cada
sala em cada dia. Endpoint: `/api/paineis/visao-geral`.

## Status da reserva (RF10)

Derivado do horário dos períodos (+ cancelamento): **PREVISTA**, **EM_ANDAMENTO**,
**TRANSCORRIDA**, **CANCELADA**. Calculado em `statusDaReserva`.

## Requisitos não-funcionais

- Responsivo e acessível (meta WCAG 2.1 AA) — ver `docs/acessibilidade.md`.
- Entregáveis: documentação, modelo de dados e testes.
- Autenticação AWS Cognito no ambiente final; mock por header em desenvolvimento.

## Fora do escopo

- Integração real com sistemas do MPF; emissão de documento oficial.
- Envio real de e-mail e pedido real no SNP (ambos **simulados**).
