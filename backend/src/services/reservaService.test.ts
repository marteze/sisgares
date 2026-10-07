import { describe, it, expect } from 'vitest';
import { conflitaComMargem } from './reservaService.js';

describe('conflitaComMargem (RF11)', () => {
  const M = 30; // margem de tolerância em minutos

  it('detecta sobreposição direta', () => {
    expect(
      conflitaComMargem(
        '2026-10-15T09:00:00',
        '2026-10-15T11:00:00',
        '2026-10-15T10:00:00',
        '2026-10-15T12:00:00',
        M
      )
    ).toBe(true);
  });

  it('detecta conflito dentro da margem de 30 min (encosta)', () => {
    // Novo período termina 11:00; existente começa 11:20 (20 min < 30) -> conflito.
    expect(
      conflitaComMargem(
        '2026-10-15T09:00:00',
        '2026-10-15T11:00:00',
        '2026-10-15T11:20:00',
        '2026-10-15T12:00:00',
        M
      )
    ).toBe(true);
  });

  it('não conflita quando o intervalo entre períodos é >= margem', () => {
    // Novo termina 11:00; existente começa 11:30 (exatamente 30) -> sem conflito.
    expect(
      conflitaComMargem(
        '2026-10-15T09:00:00',
        '2026-10-15T11:00:00',
        '2026-10-15T11:30:00',
        '2026-10-15T12:00:00',
        M
      )
    ).toBe(false);
  });

  it('não conflita em dias diferentes', () => {
    expect(
      conflitaComMargem(
        '2026-10-15T09:00:00',
        '2026-10-15T11:00:00',
        '2026-10-16T09:00:00',
        '2026-10-16T11:00:00',
        M
      )
    ).toBe(false);
  });
});
