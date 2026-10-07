// RF10: status da reserva derivado do horário dos períodos (+ cancelamento).
import type { Reserva, StatusReserva } from '../types.js';

export function statusDaReserva(
  reserva: Reserva,
  agora: Date = new Date()
): StatusReserva {
  if (reserva.RESE_CANCELADA) return 'CANCELADA';
  if (reserva.periodos.length === 0) return 'PREVISTA';

  const inicios = reserva.periodos.map((p) => new Date(p.PRES_DTHR_INICIO).getTime());
  const terminos = reserva.periodos.map((p) =>
    new Date(p.PRES_DTHR_TERMINO).getTime()
  );
  const primeiroInicio = Math.min(...inicios);
  const ultimoTermino = Math.max(...terminos);
  const t = agora.getTime();

  if (t < primeiroInicio) return 'PREVISTA';
  if (t > ultimoTermino) return 'TRANSCORRIDA';
  return 'EM_ANDAMENTO';
}
