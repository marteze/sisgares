// Endpoints de notificações (painel do responsável de área).
import type { Request, Response } from 'express';
import {
  listarNotificacoes,
  notificacoesDoSetor,
} from '../services/notificacaoService.js';

export function getNotificacoes(req: Request, res: Response) {
  const setorId = req.query.setorId ? Number(req.query.setorId) : null;
  res.json(setorId ? notificacoesDoSetor(setorId) : listarNotificacoes());
}
