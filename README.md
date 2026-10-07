# Sistema de Solicitação de Ambientes e Recursos — Hackathon MPF & AWS 2026

Módulo de **Reservas** para a administração das unidades do MPF: permite reservar
ambientes físicos (opcionais), recursos tecnológicos (notebook, projetor, som) e
serviços (água, café) para eventos, com fluxo de aprovação, delegação para as áreas
responsáveis e painéis com visões por perfil.

> Caso de uso do Hackathon MPF & AWS 2026 — "Solicitação de Ambientes e Recursos".
> Sistema de origem: **Solare**. Ponto focal: Francisco Adriano Nobre Freire.

## Visão geral

| Camada | Stack | Pasta |
|--------|-------|-------|
| Frontend | React + Vite + TypeScript | `frontend/` |
| Backend | Node.js + Express + TypeScript | `backend/` |
| Dados | CSVs fornecidos (fictícios) | `data/`, `backend/data/` |
| Infra | AWS (Cognito, deploy) — a definir | `infra/` |
| Documentação | Modelo de dados, requisitos, divisão | `docs/` |

## Perfis de usuário

- **Solicitante** — cria/edita/cancela as próprias reservas; usa o painel-grade.
- **Gestor** — vê e gerencia todas as reservas; edita qualquer uma pela grade.
- **Atendente** (setor envolvido) — painel por data com os pedidos da sua área.
- **Administrador** — configura antecedência mínima, faixa de horário e endpoint do SNP.

No modo demonstração, troque de perfil pelo seletor no topo da aplicação.

## Como rodar (dev)

```bash
# backend
cd backend
npm install
npm run dev        # API em http://localhost:3001/api

# frontend (outro terminal)
cd frontend
npm install
npm run dev        # app em http://localhost:5173
```

O frontend faz proxy de `/api` para o backend (ver `frontend/vite.config.ts`), então
rode os dois juntos.

> Observação (Windows): rode `npm run dev` manualmente no seu terminal — ele é um
> processo contínuo e não deve ser iniciado dentro de scripts automatizados.

> Verificação: o build (`npm run build`) e os testes (`npm test`) de cada pasta
> devem rodar na sua máquina. No ambiente onde este esqueleto foi gerado, o
> `npm install` não concluiu (sem acesso ao registry), então não foi possível
> executar build/testes aqui.

## Documentação

- [`docs/modelo-de-dados.md`](docs/modelo-de-dados.md) — entidades, campos e relacionamentos (extraídos dos CSVs reais).
- [`docs/requisitos.md`](docs/requisitos.md) — funcionalidades e regras de negócio.
- [`docs/arquitetura.md`](docs/arquitetura.md) — visão técnica, componentes e endpoints.
- [`docs/dados.md`](docs/dados.md) — descrição dos arquivos de dados fornecidos.
- [`docs/acessibilidade.md`](docs/acessibilidade.md) — checklist de acessibilidade.

## Escopo

**Dentro:** módulo de Reservas completo, responsivo e acessível, com documentação,
modelo de dados e testes.

**Fora:** integração com sistemas reais, emissão de documento oficial. Notificações
por e-mail e "pedido no sistema nacional" são **simulados**.
