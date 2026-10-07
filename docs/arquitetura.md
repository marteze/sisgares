# Arquitetura e visão técnica

Entrega única e completa do módulo de Reservas (sem divisão por frentes).

## Componentes

```
┌─────────────────────────┐        HTTP /api        ┌──────────────────────────┐
│  Frontend (React + Vite)│ ───────────────────────▶│  Backend (Express + TS)  │
│  - Páginas por perfil    │   proxy /api → :3001    │  - Rotas /api/*          │
│  - Ícones em /public     │◀─────────────────────── │  - Regras de negócio     │
└─────────────────────────┘        JSON              │  - Store em memória      │
                                                      │     (carrega CSVs)       │
                                                      └──────────┬───────────────┘
                                                                 │ lê na inicialização
                                                                 ▼
                                                        backend/data/*.csv
```

## Backend (`backend/`)

- `src/server.ts` — sobe a API e pré-carrega os dados.
- `src/app.ts` — Express, CORS, JSON, middleware de autenticação, rotas.
- `src/services/dataStore.ts` — lê os CSVs e monta o "banco" em memória; reconstrói
  as reservas importadas agrupando períodos e solicitações por `RESE_ID`.
- `src/services/reservaService.ts` — regras RN1 (conflito de ambiente), RN2
  (ambiente opcional), RN4 (disponibilidade) e criação/aprovação/recusa.
- `src/services/notificacaoService.ts` — RN3: identifica setores responsáveis e
  gera notificação por e-mail (simulada); histórico em memória.
- `src/middleware/auth.ts` — autenticação. Em dev, lê o perfil de headers
  (`x-user-perfil`); ponto de extensão para o AWS Cognito.
- `src/controllers`, `src/routes` — endpoints REST.

### Endpoints

| Método | Rota | Descrição | RF |
|--------|------|-----------|----|
| GET | `/api/health` | Status | — |
| GET | `/api/unidades` | Unidades macro | RF02 |
| GET | `/api/ambientes?unidadeId=` | Ambientes ativos (por unidade) | RF02 |
| GET | `/api/recursos?unidadeId=&ambienteId=` | Recursos (filtra por unidade/ambiente) | RF06/RF08 |
| GET | `/api/grupos-recurso` | Grupos ordenados | RF05 |
| GET | `/api/disposicoes` | Disposições | RF04 |
| GET | `/api/envolvidos` | Setores | RF01 |
| GET | `/api/config` | Configurações | RF09 |
| PUT | `/api/config` | Atualiza config (ADMIN) | RF09 |
| GET | `/api/reservas` | Lista (solicitante vê as próprias) | RF10 |
| GET | `/api/reservas/verificar-conflito` | Verificação antecipada | RF11 |
| GET | `/api/reservas/:id` | Detalhe | RF10 |
| POST | `/api/reservas` | Cria reserva (RF11/RF12/RF13/RF14) | RF10 |
| PUT | `/api/reservas/:id` | Altera (revalida + diff) | RF15 |
| POST | `/api/reservas/:id/cancelar` | Cancela (requer `confirmar`) | RF15 |
| GET | `/api/paineis/visao-geral` | Ocupações de todas as salas | extra |
| GET | `/api/paineis/grade/:ambienteId` | Ocupações para a grade | RF16 |
| GET | `/api/paineis/atendente?setorId=` | Cards do atendente | RF17 |
| GET | `/api/notificacoes?setorId=` | Histórico de notificações | RF13 |

## Frontend (`frontend/`)

- `src/App.tsx` — rotas; `src/components/Layout.tsx` — cabeçalho, navegação e seletor
  de perfil (demo).
- Páginas: `Home`, `NovaReserva` (fluxo de criação), `MinhasReservas`,
  `PainelGestor` (aprovar/recusar + resumo), `PainelArea` (notificações do setor).
- `src/services/api.ts` — cliente HTTP; injeta o usuário atual via headers.
- `src/hooks/useSessao.tsx` — sessão mock com troca de perfil.
- Ícones dos recursos/disposições em `frontend/public/icones-*` (vindos do pacote
  de imagens do workshop; nomes batem com `RECU_ICONE_ARQUIVO` / `DISP_ICONE_ARQUIVO`).

## Autenticação (Cognito)

Em dev o perfil vem por header. Para o ambiente AWS, trocar `AUTH_MODE=cognito` e
implementar a validação do JWT em `middleware/auth.ts` (ponto já demarcado com TODO).

## Decisões

- **Sem banco de dados**: o ambiente do hackathon não acessa bancos; usamos store em
  memória populado pelos CSVs. A interface do `dataStore` isola essa escolha.
- **Notificação e pedido nacional simulados**: apenas log + histórico, conforme escopo.
- **Reserva inferida**: não há CSV de reserva; ela é reconstruída a partir dos
  `RESE_ID` em `periodo-reserva` e `solicitacao`.
