// RF16: dados para o painel-grade do solicitante.
// RF17: dados para o painel de cards do atendente.
import type { Request, Response } from 'express';
import { getDb } from '../services/dataStore.js';
import { ambientesRelacionados } from '../services/ambienteHierarquia.js';
import { statusDaReserva } from '../services/statusReserva.js';

// RF16: ocupações do ambiente (e de seus pais/filhos) no intervalo de datas.
export function gradeDoAmbiente(req: Request, res: Response) {
  const db = getDb();
  const ambienteId = Number(req.params.ambienteId);
  if (!ambienteId) return res.status(400).json({ erro: 'ambienteId inválido.' });

  const relacionados = ambientesRelacionados(ambienteId);
  const ocupacoes: {
    reservaId: number;
    ambienteId: number;
    inicio: string;
    termino: string;
    finalidade: string;
    solicitante: string;
    doProprioAmbiente: boolean;
    podeEditar: boolean;
  }[] = [];

  const usuario = req.usuario!;

  for (const r of db.reservas) {
    if (r.RESE_CANCELADA) continue;
    if (r.RESE_AMBI_ID === null || !relacionados.has(r.RESE_AMBI_ID)) continue;
    for (const p of r.periodos) {
      ocupacoes.push({
        reservaId: r.RESE_ID,
        ambienteId: r.RESE_AMBI_ID,
        inicio: p.PRES_DTHR_INICIO,
        termino: p.PRES_DTHR_TERMINO,
        finalidade: r.RESE_FINALIDADE,
        solicitante: r.RESE_SOLICITANTE_NOME,
        doProprioAmbiente: r.RESE_AMBI_ID === ambienteId,
        podeEditar:
          r.RESE_SOLICITANTE === usuario.id || usuario.perfil === 'GESTOR',
      });
    }
  }

  res.json({
    ambienteId,
    config: {
      antecedenciaMinimaMin: db.config.antecedenciaMinimaMin,
      margemToleranciaMin: db.config.margemToleranciaMin,
      horarioMin: db.config.horarioMinGlobal,
      horarioMax: db.config.horarioMaxGlobal,
    },
    ocupacoes,
  });
}

// RF17: reservas do setor atendente agrupadas por data (cards).
export function cardsDoAtendente(req: Request, res: Response) {
  const db = getDb();
  const usuario = req.usuario!;
  const setorId = usuario.setorId ?? (req.query.setorId ? Number(req.query.setorId) : null);

  // Recursos/ambientes sob responsabilidade do setor.
  const recursosDoSetor = new Set(
    db.envolvidoRecurso
      .filter((er) => setorId === null || er.EREC_ENVO_ID === setorId)
      .map((er) => er.EREC_RECU_ID)
  );
  const ambientesDoSetor = new Set(
    db.envolvidoAmbiente
      .filter((ea) => setorId === null || ea.EAMB_ENVO_ID === setorId)
      .map((ea) => ea.EAMB_AMBI_ID)
  );

  const cards = db.reservas
    .filter((r) => !r.RESE_CANCELADA)
    .filter((r) => {
      if (setorId === null) return true;
      const porAmbiente =
        r.RESE_AMBI_ID !== null && ambientesDoSetor.has(r.RESE_AMBI_ID);
      const porRecurso = r.solicitacoes.some((s) =>
        recursosDoSetor.has(s.SOLI_RECU_ID)
      );
      return porAmbiente || porRecurso;
    })
    .flatMap((r) =>
      r.periodos.map((p) => ({
        reservaId: r.RESE_ID,
        inicio: p.PRES_DTHR_INICIO,
        termino: p.PRES_DTHR_TERMINO,
        finalidade: r.RESE_FINALIDADE,
        solicitante: r.RESE_SOLICITANTE_NOME,
        participantes: r.RESE_PARTICIPANTES,
        status: statusDaReserva(r),
        recursos: r.solicitacoes.map((s) => {
          const rec = db.recursos.find((x) => x.RECU_ID === s.SOLI_RECU_ID);
          return rec?.RECU_DESC ?? `Recurso ${s.SOLI_RECU_ID}`;
        }),
        snps: r.snps.map((s) => ({ codigo: s.SNP_CODIGO, link: s.SNP_LINK })),
      }))
    );

  res.json({ setorId, cards });
}

// Visão geral: ocupações de TODAS as salas (todos os ambientes) no período.
// Mantém o modo por sala; este é um modo adicional de visualização.
export function visaoGeral(req: Request, res: Response) {
  const db = getDb();
  const usuario = req.usuario!;

  const ambientes = db.ambientes
    .filter((a) => a.AMBI_ST_ATIVO === 'S')
    .map((a) => ({ ambienteId: a.AMBI_ID, descricao: a.AMBI_DESC }));

  const ocupacoes = db.reservas
    .filter((r) => !r.RESE_CANCELADA && r.RESE_AMBI_ID !== null)
    .flatMap((r) =>
      r.periodos.map((p) => ({
        reservaId: r.RESE_ID,
        ambienteId: r.RESE_AMBI_ID as number,
        inicio: p.PRES_DTHR_INICIO,
        termino: p.PRES_DTHR_TERMINO,
        finalidade: r.RESE_FINALIDADE,
        solicitante: r.RESE_SOLICITANTE_NOME,
        participantes: r.RESE_PARTICIPANTES,
        status: statusDaReserva(r),
        podeEditar:
          r.RESE_SOLICITANTE === usuario.id || usuario.perfil === 'GESTOR',
      }))
    );

  res.json({
    config: {
      antecedenciaMinimaMin: db.config.antecedenciaMinimaMin,
      margemToleranciaMin: db.config.margemToleranciaMin,
      horarioMin: db.config.horarioMinGlobal,
      horarioMax: db.config.horarioMaxGlobal,
    },
    ambientes,
    ocupacoes,
  });
}
