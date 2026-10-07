import type { StatusReserva } from '../types';
import { ROTULO_STATUS } from '../utils/format';

export function StatusBadge({ status }: { status: StatusReserva }) {
  return (
    <span className={`badge badge--${status.toLowerCase()}`}>
      {ROTULO_STATUS[status] ?? status}
    </span>
  );
}
