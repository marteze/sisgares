import { describe, it, expect } from 'vitest';
import { statusDaReserva } from './statusReserva.js';
import type { Reserva } from '../types.js';

function reservaBase(parcial: Partial<Reserva>): Reserva {
  return {
    RESE_ID: 1,
    RESE_FINALIDADE: 'x',
    RESE_PARTICIPANTES: 1,
    RESE_AMBI_ID: null,
    RESE_COMPLEMENTO: 'sala',
    RESE_DISP_ID: null,
    RESE_UNID_ID: 1,
    RESE_SOLICITANTE: 'u',
    RESE_SOLICITANTE_NOME: 'U',
    RESE_CANCELADA: false,
    RESE_DTHR_ALTERACAO: '2026-01-01T00:00:00.000Z',
    periodos: [],
    solicitacoes: [],
    snps: [],
    ...parcial,
  };
}

const agora = new Date('2026-10-15T12:00:00');

describe('statusDaReserva (RF10)', () => {
  it('CANCELADA tem prioridade', () => {
    const r = reservaBase({ RESE_CANCELADA: true });
    expect(statusDaReserva(r, agora)).toBe('CANCELADA');
  });

  it('PREVISTA quando ainda não começou', () => {
    const r = reservaBase({
      periodos: [
        {
          PRES_ID: 1,
          PRES_RESE_ID: 1,
          PRES_DTHR_INICIO: '2026-10-15T14:00:00',
          PRES_DTHR_TERMINO: '2026-10-15T16:00:00',
        },
      ],
    });
    expect(statusDaReserva(r, agora)).toBe('PREVISTA');
  });

  it('EM_ANDAMENTO quando o momento está dentro de um período', () => {
    const r = reservaBase({
      periodos: [
        {
          PRES_ID: 1,
          PRES_RESE_ID: 1,
          PRES_DTHR_INICIO: '2026-10-15T11:00:00',
          PRES_DTHR_TERMINO: '2026-10-15T13:00:00',
        },
      ],
    });
    expect(statusDaReserva(r, agora)).toBe('EM_ANDAMENTO');
  });

  it('TRANSCORRIDA quando já terminou', () => {
    const r = reservaBase({
      periodos: [
        {
          PRES_ID: 1,
          PRES_RESE_ID: 1,
          PRES_DTHR_INICIO: '2026-10-15T08:00:00',
          PRES_DTHR_TERMINO: '2026-10-15T10:00:00',
        },
      ],
    });
    expect(statusDaReserva(r, agora)).toBe('TRANSCORRIDA');
  });
});
