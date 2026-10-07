// Endpoints de reservas (RF10-RF17).
import type { Request, Response } from 'express';
import { getDb } from '../services/dataStore.js';
import {
  alterarReserva,
  cancelarReserva,
  conflitosDeAmbiente,
  criarReserva,
  RegraNegocioError,
  type ReservaInput,
} from '../services/reservaService.js';
import { setoresResponsaveis } from '../services/notificacaoService.js';
import { statusDaReserva } from '../services/statusReserva.js';
import type { Reserva } from '../types.js';

function parseInput(body: Record<string, unknown>): ReservaInput {
  return {
    finalidade: String(body.finalidade ?? '').trim(),
    participantes: Number(body.participantes ?? 0),
    ambienteId:
      body.ambienteId === null || body.ambienteId === undefined || body.ambienteId === ''
        ? null
        : Number(body.ambienteId),
    complemento: body.complemento ? String(body.complemento) : null,
    disposicaoId: body.disposicaoId ? Number(body.disposicaoId) : null,
    unidadeId: body.unidadeId ? Number(body.unidadeId) : null,
    periodos: Array.isArray(body.periodos)
      ? (body.periodos as { inicio: string; termino: string }[])
      : [],
    recursos: Array.isArray(body.recursos)
      ? (body.recursos as { recursoId: number; quantidade: number | null }[])
      : [],
  };
}

function tratar(e: unknown, res: Response): void {
  if (e instanceof RegraNegocioError) {
    const status = e.code === 'NAO_ENCONTRADA' ? 404 : e.code === 'SEM_PERMISSAO' ? 403 : 409;
    res.status(status).json({ erro: e.message, code: e.code });
    return;
  }
  throw e;
}

export function listarReservas(req: Request, res: Response) {
  const db = getDb();
  const usuario = req.usuario!;
  let reservas = db.reservas;

  // Solicitante só vê as próprias; demais perfis veem todas.
  if (usuario.perfil === 'SOLICITANTE') {
    reservas = reservas.filter((r) => r.RESE_SOLICITANTE === usuario.id);
  }
  res.json(reservas.map(enriquecer));
}

export function obterReserva(req: Request, res: Response) {
  const db = getDb();
  const r = db.reservas.find((x) => x.RESE_ID === Number(req.params.id));
  if (!r) return res.status(404).json({ erro: 'Reserva não encontrada.' });
  res.json(enriquecer(r));
}

export function postReserva(req: Request, res: Response) {
  const usuario = req.usuario!;
  try {
    const reserva = criarReserva(
      parseInput(req.body ?? {}),
      usuario.id,
      usuario.nome
    );
    res.status(201).json(enriquecer(reserva));
  } catch (e) {
    tratar(e, res);
  }
}

export function putReserva(req: Request, res: Response) {
  const usuario = req.usuario!;
  try {
    const reserva = alterarReserva(
      Number(req.params.id),
      parseInput(req.body ?? {}),
      usuario.id,
      usuario.perfil
    );
    res.json(enriquecer(reserva));
  } catch (e) {
    tratar(e, res);
  }
}

export function cancelar(req: Request, res: Response) {
  const usuario = req.usuario!;
  // RF15: cancelamento mediante confirmação (o cliente envia confirmar=true).
  if (!req.body?.confirmar) {
    return res
      .status(400)
      .json({ erro: 'Cancelamento requer confirmação.', code: 'CONFIRMAR' });
  }
  try {
    const reserva = cancelarReserva(
      Number(req.params.id),
      usuario.id,
      usuario.perfil
    );
    res.json(enriquecer(reserva));
  } catch (e) {
    tratar(e, res);
  }
}

// RF11: verificação antecipada de conflitos (usada pela tela ao escolher período).
export function verificarConflito(req: Request, res: Response) {
  const ambienteId = Number(req.query.ambienteId);
  const inicio = String(req.query.inicio ?? '');
  const termino = String(req.query.termino ?? '');
  const ignorar = req.query.ignorar ? Number(req.query.ignorar) : undefined;
  if (!ambienteId || !inicio || !termino) {
    return res.json({ conflito: false, conflitos: [] });
  }
  const conflitos = conflitosDeAmbiente(
    ambienteId,
    [{ inicio, termino }],
    ignorar
  );
  res.json({
    conflito: conflitos.length > 0,
    conflitos: conflitos.map((c) => ({
      reservaId: c.reservaId,
      inicio: c.periodo.PRES_DTHR_INICIO,
      termino: c.periodo.PRES_DTHR_TERMINO,
    })),
  });
}

// Acrescenta dados legíveis + status (RF10) + links de SNP (RF14).
export function enriquecer(r: Reserva) {
  const db = getDb();
  const ambiente = db.ambientes.find((a) => a.AMBI_ID === r.RESE_AMBI_ID) ?? null;
  const disposicao =
    db.disposicoes.find((d) => d.DISP_ID === r.RESE_DISP_ID) ?? null;
  const recursos = r.solicitacoes.map((s) => {
    const rec = db.recursos.find((x) => x.RECU_ID === s.SOLI_RECU_ID);
    return {
      recursoId: s.SOLI_RECU_ID,
      descricao: rec?.RECU_DESC ?? `Recurso ${s.SOLI_RECU_ID}`,
      icone: rec?.RECU_ICONE_ARQUIVO ?? 'indefinido.png',
      quantidade: s.SOLI_QTD,
    };
  });
  return {
    ...r,
    status: statusDaReserva(r),
    ambienteDescricao: ambiente?.AMBI_DESC ?? null,
    disposicaoDescricao: disposicao?.DISP_DESC ?? null,
    disposicaoIcone: disposicao?.DISP_ICONE_ARQUIVO ?? null,
    recursos,
    setoresEnvolvidos: setoresResponsaveis(r).map((e) => ({
      id: e.ENVO_ID,
      nome: e.ENVO_DESC,
      email: e.ENVO_EMAIL,
    })),
    snps: r.snps,
  };
}
