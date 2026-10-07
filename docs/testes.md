# Testes

> Observação de ambiente: neste ambiente de desenvolvimento o `npm install` não
> completa (acesso ao registry bloqueado) e, portanto, build/testes não foram
> executados aqui. Rode os comandos abaixo na sua máquina.

## Backend (Vitest)

```bash
cd backend
npm install
npm test
```

- `src/utils/csv.test.ts` — parsing de CSV (aspas escapadas), normalização de IDs
  com separador de milhar e conversão de datas.
- `src/services/reservaService.test.ts` — RF11: `conflitaComMargem` com a margem de
  30 min (sobreposição direta, encosta dentro da margem, exatamente na margem, dias
  diferentes).
- `src/services/statusReserva.test.ts` — RF10: status derivado do horário
  (PREVISTA / EM_ANDAMENTO / TRANSCORRIDA / CANCELADA).

## Frontend (Vitest + Testing Library)

```bash
cd frontend
npm install
npm test
```

- `src/components/StatusBadge.test.tsx` — rótulo em português e classe por status.

## Roteiro de teste manual (demo)

1. **Solicitante** → "Painel-grade": escolher um ambiente, clicar numa célula livre
   "Reservar às XX:XX" (prefill de ambiente/data/hora). Observar células de "Margem
   de tolerância", "Horário ultrapassado" e "Sem antecedência mínima" (RF16).
2. "Nova reserva": preencher finalidade e participantes; deixar "Não solicitado /
   local próprio" e preencher o complemento (RF10). Adicionar período e recursos.
   Ao sair do campo de término, ver a verificação antecipada de conflito (RF11).
3. Criar uma reserva com água/café → ela registra um pedido SNP (link no card) e
   notifica o setor (RF13/RF14).
4. Editar a reserva (RF15): a notificação de alteração destaca o diff.
5. **Atendente** → "Painel do atendente": cards por data com recursos e link do SNP (RF17).
6. **Administrador** → "Configurações": mudar antecedência mínima / faixa de horário
   e ver o efeito nas validações (RF09).
