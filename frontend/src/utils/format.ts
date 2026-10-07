// Formatação de datas e rótulos.
export function formatarDataHora(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatarHora(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

export function iconeRecurso(arquivo: string): string {
  return `/icones-recurso/${arquivo || 'indefinido.png'}`;
}

export function iconeDisposicao(arquivo: string): string {
  return `/icones-disposicao/${arquivo || 'indefinido.jpg'}`;
}

export const ROTULO_STATUS: Record<string, string> = {
  PREVISTA: 'Prevista',
  EM_ANDAMENTO: 'Em andamento',
  TRANSCORRIDA: 'Transcorrida',
  CANCELADA: 'Cancelada',
};
