// Regras de negócio do módulo de Reservas (RF10-RF15).
import { getDb } from './dataStore.js';
import { ambientesRelacionados } from './ambienteHierarquia.js';
import {
  notificarSetores,
  registrarPedidosSNP,
} from './notificacaoService.js';
import type {
  PeriodoReserva,
  Reserva,
  Solicitacao,
} from '../types.js';

const MIN = 60 * 1000;

export interface PeriodoInput {
  inicio: string; // ISO ou datetime-local
  termino: string;
}

export interface ReservaInput {
  finalidade: string;
  participantes: number;
  ambienteId: number | null;
  complemento: string | null;
  disposicaoId: number | null;
  unidadeId: number | null;
  periodos: PeriodoInput[];
  recursos: { recursoId: number; quantidade: number | null }[];
}

export class RegraNegocioError extends Error {
  constructor(
    public code: string,
    message: string
  ) {
    super(message);
  }
}

function ms(iso: string): number {
  return new Date(iso).getTime();
}

// Sobreposição considerando a margem de tolerância (RF11: 30 min).
// Dois períodos conflitam se [aIni-margem, aFim+margem) intercepta [bIni, bFim).
export function conflitaComMargem(
  aIni: string,
  aFim: string,
  bIni: string,
  bFim: string,
  margemMin: number
): boolean {
  const margem = margemMin * MIN;
  return ms(aIni) - margem < ms(bFim) && ms(bIni) < ms(aFim) + margem;
}

// RF11: conflito considerando o ambiente, seus pais e filhos.
export function conflitosDeAmbiente(
  ambienteId: number,
  periodos: PeriodoInput[],
  ignorarReservaId?: number
): { reservaId: number; periodo: PeriodoReserva }[] {
  const db = getDb();
  const relacionados = ambientesRelacionados(ambienteId);
  const margem = db.config.margemToleranciaMin;
  const achados: { reservaId: number; periodo: PeriodoReserva }[] = [];

  for (const r of db.reservas) {
    if (r.RESE_CANCELADA) continue;
    if (r.RESE_ID === ignorarReservaId) continue;
    if (r.RESE_AMBI_ID === null || !relacionados.has(r.RESE_AMBI_ID)) continue;

    for (const p of r.periodos) {
      for (const novo of periodos) {
        if (
          conflitaComMargem(
            novo.inicio,
            novo.termino,
            p.PRES_DTHR_INICIO,
            p.PRES_DTHR_TERMINO,
            margem
          )
        ) {
          achados.push({ reservaId: r.RESE_ID, periodo: p });
        }
      }
    }
  }
  return achados;
}

// RF12: quantidade de um recurso limitado já reservada que intercepta os períodos.
export function usoRecursoNoPeriodo(
  recursoId: number,
  periodos: PeriodoInput[],
  ignorarReservaId?: number
): number {
  const db = getDb();
  let usado = 0;
  for (const r of db.reservas) {
    if (r.RESE_CANCELADA) continue;
    if (r.RESE_ID === ignorarReservaId) continue;

    const intercepta = r.periodos.some((p) =>
      periodos.some(
        (novo) =>
          ms(novo.inicio) < ms(p.PRES_DTHR_TERMINO) &&
          ms(p.PRES_DTHR_INICIO) < ms(novo.termino)
      )
    );
    if (!intercepta) continue;

    usado += r.solicitacoes
      .filter((s) => s.SOLI_RECU_ID === recursoId)
      .reduce((sum, s) => sum + (s.SOLI_QTD ?? 1), 0);
  }
  return usado;
}

// RF09/RF10: valida a faixa de horário e a antecedência mínima.
function validarPeriodos(input: ReservaInput): void {
  const db = getDb();
  const cfg = db.config;
  const unidade = input.unidadeId ?? null;
  const faixa = (unidade && cfg.porUnidade[unidade]) || {};
  const hMin = faixa.horarioMin ?? cfg.horarioMinGlobal;
  const hMax = faixa.horarioMax ?? cfg.horarioMaxGlobal;

  if (input.periodos.length === 0) {
    throw new RegraNegocioError('SEM_PERIODO', 'Informe ao menos um período.');
  }

  const agora = Date.now();
  for (const p of input.periodos) {
    const ini = new Date(p.inicio);
    const fim = new Date(p.termino);
    if (Number.isNaN(ini.getTime()) || Number.isNaN(fim.getTime())) {
      throw new RegraNegocioError('PERIODO_INVALIDO', 'Período com data inválida.');
    }
    if (ini.getTime() >= fim.getTime()) {
      throw new RegraNegocioError(
        'PERIODO_INVALIDO',
        'O início deve ser anterior ao término.'
      );
    }
    // Antecedência mínima (RF09)
    if (ini.getTime() - agora < cfg.antecedenciaMinimaMin * MIN) {
      throw new RegraNegocioError(
        'SEM_ANTECEDENCIA',
        `A reserva deve ter antecedência mínima de ${cfg.antecedenciaMinimaMin} minutos.`
      );
    }
    // Faixa de horário (RF10) — compara HH:MM de início e término.
    const hhmm = (d: Date) =>
      `${String(d.getHours()).padStart(2, '0')}:${String(
        d.getMinutes()
      ).padStart(2, '0')}`;
    if (hhmm(ini) < hMin || hhmm(fim) > hMax) {
      throw new RegraNegocioError(
        'FORA_FAIXA_HORARIO',
        `Horário fora da faixa permitida (${hMin}–${hMax}).`
      );
    }
  }
}

function validarRegras(input: ReservaInput, ignorarReservaId?: number): void {
  const db = getDb();

  // RF10: campos obrigatórios
  if (!input.finalidade.trim()) {
    throw new RegraNegocioError('SEM_FINALIDADE', 'A finalidade é obrigatória.');
  }
  if (!input.participantes || input.participantes < 1) {
    throw new RegraNegocioError(
      'SEM_PARTICIPANTES',
      'Informe a quantidade de participantes.'
    );
  }
  // RF10: sem ambiente -> complemento obrigatório
  if (input.ambienteId === null && !(input.complemento ?? '').trim()) {
    throw new RegraNegocioError(
      'SEM_COMPLEMENTO',
      'Sem ambiente físico, o complemento do ambiente é obrigatório.'
    );
  }

  validarPeriodos(input);

  // RF11: conflito de ambiente (hierarquia + margem)
  if (input.ambienteId !== null) {
    const conflitos = conflitosDeAmbiente(
      input.ambienteId,
      input.periodos,
      ignorarReservaId
    );
    if (conflitos.length > 0) {
      throw new RegraNegocioError(
        'CONFLITO_AMBIENTE',
        `Conflito de horário com a reserva #${conflitos[0].reservaId} (considerando margem de ${db.config.margemToleranciaMin} min e ambientes relacionados).`
      );
    }
  }

  // RF12: disponibilidade de recursos limitados
  for (const rec of input.recursos) {
    const recurso = db.recursos.find((r) => r.RECU_ID === rec.recursoId);
    if (!recurso || recurso.RECU_ST_LIMITADO !== 'S') continue;
    const qtd = rec.quantidade ?? 1;
    const usado = usoRecursoNoPeriodo(
      rec.recursoId,
      input.periodos,
      ignorarReservaId
    );
    if (usado + qtd > recurso.RECU_DISPONIBILIDADE) {
      throw new RegraNegocioError(
        'SEM_DISPONIBILIDADE',
        `Recurso "${recurso.RECU_DESC}" indisponível: pedido ${qtd}, já reservado ${usado}, disponível ${recurso.RECU_DISPONIBILIDADE}.`
      );
    }
  }
}

function construirPeriodos(reseId: number, input: ReservaInput): PeriodoReserva[] {
  const db = getDb();
  return input.periodos.map((p) => ({
    PRES_ID: db.seq.periodo++,
    PRES_RESE_ID: reseId,
    PRES_DTHR_INICIO: new Date(p.inicio).toISOString(),
    PRES_DTHR_TERMINO: new Date(p.termino).toISOString(),
  }));
}

function construirSolicitacoes(
  reseId: number,
  input: ReservaInput
): Solicitacao[] {
  const db = getDb();
  return input.recursos.map((rec) => ({
    SOLI_ID: db.seq.solicitacao++,
    SOLI_RESE_ID: reseId,
    SOLI_RECU_ID: rec.recursoId,
    SOLI_QTD: rec.quantidade,
  }));
}

// RF10: cria a reserva.
export function criarReserva(
  input: ReservaInput,
  solicitanteId: string,
  solicitanteNome: string
): Reserva {
  const db = getDb();
  validarRegras(input);

  const reseId = db.seq.reserva++;
  const reserva: Reserva = {
    RESE_ID: reseId,
    RESE_FINALIDADE: input.finalidade.trim(),
    RESE_PARTICIPANTES: input.participantes,
    RESE_AMBI_ID: input.ambienteId,
    RESE_COMPLEMENTO: input.ambienteId === null ? input.complemento : null,
    RESE_DISP_ID: input.disposicaoId,
    RESE_UNID_ID: input.unidadeId,
    RESE_SOLICITANTE: solicitanteId,
    RESE_SOLICITANTE_NOME: solicitanteNome,
    RESE_CANCELADA: false,
    RESE_DTHR_ALTERACAO: new Date().toISOString(),
    periodos: construirPeriodos(reseId, input),
    solicitacoes: construirSolicitacoes(reseId, input),
    snps: [],
  };

  db.reservas.push(reserva);

  // RF13 + RF14
  notificarSetores(reserva, 'NOVA');
  registrarPedidosSNP(reserva);

  return reserva;
}

// RF15: altera a reserva (mesmas validações) e notifica o diff.
export function alterarReserva(
  id: number,
  input: ReservaInput,
  usuarioId: string,
  perfil: string
): Reserva {
  const db = getDb();
  const r = db.reservas.find((x) => x.RESE_ID === id);
  if (!r) throw new RegraNegocioError('NAO_ENCONTRADA', 'Reserva não encontrada.');
  if (r.RESE_CANCELADA) {
    throw new RegraNegocioError('CANCELADA', 'Reserva cancelada não pode ser alterada.');
  }
  if (r.RESE_SOLICITANTE !== usuarioId && perfil !== 'GESTOR') {
    throw new RegraNegocioError('SEM_PERMISSAO', 'Sem permissão para alterar.');
  }
  // Não permite alterar reserva já transcorrida (RF15).
  const ultimoTermino = Math.max(
    0,
    ...r.periodos.map((p) => ms(p.PRES_DTHR_TERMINO))
  );
  if (ultimoTermino && ultimoTermino < Date.now()) {
    throw new RegraNegocioError(
      'TRANSCORRIDA',
      'Reserva já transcorrida não pode ser alterada.'
    );
  }

  validarRegras(input, id);

  const diffHtml = montarDiff(r, input);

  r.RESE_FINALIDADE = input.finalidade.trim();
  r.RESE_PARTICIPANTES = input.participantes;
  r.RESE_AMBI_ID = input.ambienteId;
  r.RESE_COMPLEMENTO = input.ambienteId === null ? input.complemento : null;
  r.RESE_DISP_ID = input.disposicaoId;
  r.RESE_UNID_ID = input.unidadeId;
  r.RESE_DTHR_ALTERACAO = new Date().toISOString();
  r.periodos = construirPeriodos(id, input);
  r.solicitacoes = construirSolicitacoes(id, input);

  // RF13/RF15
  notificarSetores(r, 'ALTERACAO', diffHtml);
  // RF14: novos pedidos para novos vínculos
  registrarPedidosSNP(r);

  return r;
}

// RF15: cancela a reserva com confirmação (feita no controller) e notifica.
export function cancelarReserva(
  id: number,
  usuarioId: string,
  perfil: string
): Reserva {
  const db = getDb();
  const r = db.reservas.find((x) => x.RESE_ID === id);
  if (!r) throw new RegraNegocioError('NAO_ENCONTRADA', 'Reserva não encontrada.');
  if (r.RESE_SOLICITANTE !== usuarioId && perfil !== 'GESTOR') {
    throw new RegraNegocioError('SEM_PERMISSAO', 'Sem permissão para cancelar.');
  }
  const ultimoTermino = Math.max(
    0,
    ...r.periodos.map((p) => ms(p.PRES_DTHR_TERMINO))
  );
  if (ultimoTermino && ultimoTermino < Date.now()) {
    throw new RegraNegocioError(
      'TRANSCORRIDA',
      'Reserva já transcorrida não pode ser cancelada.'
    );
  }

  r.RESE_CANCELADA = true;
  r.RESE_DTHR_ALTERACAO = new Date().toISOString();
  notificarSetores(r, 'CANCELAMENTO');
  return r;
}

// RF15: HTML destacando o que mudou.
function montarDiff(r: Reserva, input: ReservaInput): string {
  const linhas: string[] = [];
  const cmp = (rotulo: string, de: unknown, para: unknown) => {
    if (String(de ?? '') !== String(para ?? '')) {
      linhas.push(
        `<li>${rotulo}: <s>${de ?? '—'}</s> → <strong>${para ?? '—'}</strong></li>`
      );
    }
  };
  cmp('Finalidade', r.RESE_FINALIDADE, input.finalidade.trim());
  cmp('Participantes', r.RESE_PARTICIPANTES, input.participantes);
  cmp('Ambiente', r.RESE_AMBI_ID, input.ambienteId);
  cmp('Nº de períodos', r.periodos.length, input.periodos.length);
  cmp('Nº de recursos', r.solicitacoes.length, input.recursos.length);
  return linhas.length ? `<ul>${linhas.join('')}</ul>` : '<p>(sem mudanças relevantes)</p>';
}
