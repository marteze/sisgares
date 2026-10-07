// Autenticação simplificada para o hackathon.
// AUTH_MODE=mock: lê o usuário de headers (x-user-perfil / x-user-id).
// AUTH_MODE=cognito: ponto de extensão para validar o JWT do Cognito.
import type { NextFunction, Request, Response } from 'express';
import { env } from '../config/env.js';
import type { Perfil, Usuario } from '../types.js';

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      usuario?: Usuario;
    }
  }
}

const perfisValidos: Perfil[] = ['SOLICITANTE', 'GESTOR', 'ATENDENTE', 'ADMIN'];

export function autenticar(req: Request, _res: Response, next: NextFunction) {
  if (env.authMode === 'cognito') {
    // TODO (produção): validar JWT do Cognito e mapear claims -> Usuario.
  }

  const perfilHeader = (req.header('x-user-perfil') ?? 'SOLICITANTE').toUpperCase();
  const perfil = (
    perfisValidos.includes(perfilHeader as Perfil) ? perfilHeader : 'SOLICITANTE'
  ) as Perfil;

  const setorId = req.header('x-user-setor');
  const unidadeId = req.header('x-user-unidade');

  req.usuario = {
    id: req.header('x-user-id') ?? 'u-mock',
    nome: req.header('x-user-nome') ?? 'Usuário Mock',
    email: req.header('x-user-email') ?? 'mock@mpf.mp.br',
    perfil,
    setorId: setorId ? Number(setorId) : undefined,
    unidadeId: unidadeId ? Number(unidadeId) : undefined,
  };
  next();
}

export function exigirPerfil(...perfis: Perfil[]) {
  return (req: Request, res: Response, next: NextFunction) => {
    if (!req.usuario || !perfis.includes(req.usuario.perfil)) {
      return res.status(403).json({ erro: 'Perfil sem permissão para esta ação.' });
    }
    next();
  };
}
