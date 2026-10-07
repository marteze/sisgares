// Endpoints de catálogo: unidades, ambientes, recursos, grupos, disposições, envolvidos.
import type { Request, Response } from 'express';
import { getDb } from '../services/dataStore.js';

export function listarUnidades(_req: Request, res: Response) {
  res.json(getDb().unidades);
}

export function listarAmbientes(req: Request, res: Response) {
  const db = getDb();
  const unidade = req.query.unidadeId ? Number(req.query.unidadeId) : null;
  let ambientes = db.ambientes.filter((a) => a.AMBI_ST_ATIVO === 'S');
  if (unidade !== null) {
    ambientes = ambientes.filter((a) => a.AMBI_UNID_ID === unidade);
  }
  res.json(ambientes);
}

export function listarRecursos(req: Request, res: Response) {
  const db = getDb();
  const unidade = req.query.unidadeId ? Number(req.query.unidadeId) : null;
  const ambienteId = req.query.ambienteId ? Number(req.query.ambienteId) : null;

  let recursos = db.recursos.filter((r) => r.RECU_ST_ATIVO === 'S');

  // RF06: recurso vinculado a uma unidade só aparece naquela unidade.
  if (unidade !== null) {
    recursos = recursos.filter(
      (r) => r.RECU_UNID_ID === null || r.RECU_UNID_ID === unidade
    );
  }

  // RF08: se um recurso está vinculado a ambientes, só é ofertado nesses ambientes.
  if (ambienteId !== null) {
    const restritos = new Set(db.vinculoRecurso.map((v) => v.VREC_RECU_ID));
    recursos = recursos.filter((r) => {
      if (!restritos.has(r.RECU_ID)) return true; // sem restrição -> disponível
      return db.vinculoRecurso.some(
        (v) => v.VREC_RECU_ID === r.RECU_ID && v.VREC_AMBI_ID === ambienteId
      );
    });
  }

  res.json(recursos);
}

export function listarGrupos(_req: Request, res: Response) {
  const db = getDb();
  res.json(
    db.grupos
      .filter((g) => g.GREC_ST_ATIVO === 'S')
      .sort((a, b) => a.GREC_ORDEM - b.GREC_ORDEM)
  );
}

export function listarDisposicoes(_req: Request, res: Response) {
  const db = getDb();
  res.json(db.disposicoes.filter((d) => d.DISP_ST_ATIVO === 'S'));
}

export function listarEnvolvidos(_req: Request, res: Response) {
  res.json(getDb().envolvidos);
}
