import { createApp } from './app.js';
import { env } from './config/env.js';
import { getDb } from './services/dataStore.js';

const app = createApp();

// Pré-carrega os dados (falha cedo se algum CSV estiver ausente).
const db = getDb();
console.log(
  `[dados] ${db.ambientes.length} ambientes, ${db.recursos.length} recursos, ${db.reservas.length} reservas importadas.`
);

app.listen(env.port, () => {
  console.log(`[API] Reservas MPF rodando em http://localhost:${env.port}/api`);
});
