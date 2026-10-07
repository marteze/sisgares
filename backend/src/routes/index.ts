import { Router } from 'express';
import {
  listarUnidades,
  listarAmbientes,
  listarRecursos,
  listarGrupos,
  listarDisposicoes,
  listarEnvolvidos,
} from '../controllers/catalogoController.js';
import {
  listarReservas,
  obterReserva,
  postReserva,
  putReserva,
  cancelar,
  verificarConflito,
} from '../controllers/reservaController.js';
import { getNotificacoes } from '../controllers/notificacaoController.js';
import { obterConfig, atualizarConfig } from '../controllers/configController.js';
import {
  gradeDoAmbiente,
  cardsDoAtendente,
  visaoGeral,
} from '../controllers/painelController.js';
import { exigirPerfil } from '../middleware/auth.js';

export const router = Router();

router.get('/health', (_req, res) => res.json({ status: 'ok' }));

// Catálogo (RF01-RF08)
router.get('/unidades', listarUnidades);
router.get('/ambientes', listarAmbientes);
router.get('/recursos', listarRecursos);
router.get('/grupos-recurso', listarGrupos);
router.get('/disposicoes', listarDisposicoes);
router.get('/envolvidos', listarEnvolvidos);

// Configurações (RF09)
router.get('/config', obterConfig);
router.put('/config', exigirPerfil('ADMIN'), atualizarConfig);

// Reservas (RF10-RF15)
router.get('/reservas', listarReservas);
router.get('/reservas/verificar-conflito', verificarConflito);
router.get('/reservas/:id', obterReserva);
router.post('/reservas', postReserva);
router.put('/reservas/:id', putReserva);
router.post('/reservas/:id/cancelar', cancelar);

// Painéis (RF16/RF17)
router.get('/paineis/visao-geral', visaoGeral);
router.get('/paineis/grade/:ambienteId', gradeDoAmbiente);
router.get('/paineis/atendente', cardsDoAtendente);

// Notificações (apoio ao painel do atendente)
router.get('/notificacoes', getNotificacoes);
