// RF09: configurações do administrador.
import type { Request, Response } from 'express';
import { getDb } from '../services/dataStore.js';

export function obterConfig(_req: Request, res: Response) {
  res.json(getDb().config);
}

export function atualizarConfig(req: Request, res: Response) {
  const db = getDb();
  const body = req.body ?? {};
  const c = db.config;

  if (typeof body.antecedenciaMinimaMin === 'number')
    c.antecedenciaMinimaMin = body.antecedenciaMinimaMin;
  if (typeof body.horarioMinGlobal === 'string')
    c.horarioMinGlobal = body.horarioMinGlobal;
  if (typeof body.horarioMaxGlobal === 'string')
    c.horarioMaxGlobal = body.horarioMaxGlobal;
  if (typeof body.margemToleranciaMin === 'number')
    c.margemToleranciaMin = body.margemToleranciaMin;
  if (typeof body.snpEndpoint === 'string') c.snpEndpoint = body.snpEndpoint;
  if (body.porUnidade && typeof body.porUnidade === 'object')
    c.porUnidade = body.porUnidade;

  res.json(c);
}
