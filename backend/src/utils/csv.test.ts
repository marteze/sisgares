import { describe, it, expect } from 'vitest';
import {
  parseCsvToObjects,
  parseIdMilhar,
  parseDataHora,
  toNumberOrNull,
} from './csv.js';

describe('parseCsvToObjects', () => {
  it('parseia cabeçalho e campos entre aspas com aspas escapadas', () => {
    const csv = '"A","B"\n1,"Mesas em ""U"""\n2,x';
    const rows = parseCsvToObjects(csv);
    expect(rows).toEqual([
      { A: '1', B: 'Mesas em "U"' },
      { A: '2', B: 'x' },
    ]);
  });
});

describe('parseIdMilhar', () => {
  it('remove separador de milhar', () => {
    expect(parseIdMilhar('14.207')).toBe(14207);
    expect(parseIdMilhar('17.325')).toBe(17325);
  });
});

describe('parseDataHora', () => {
  it('converte DD/MM/AAAA HH:MM:SS para ISO', () => {
    expect(parseDataHora('20/10/2026 11:00:00')).toBe('2026-10-20T11:00:00');
  });
});

describe('toNumberOrNull', () => {
  it('retorna null para vazio', () => {
    expect(toNumberOrNull('')).toBeNull();
    expect(toNumberOrNull('5')).toBe(5);
  });
});
