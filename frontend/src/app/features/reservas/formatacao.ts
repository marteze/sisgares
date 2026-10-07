/** Formata data-hora ISO como `dd/MM/yyyy HH:mm` no fuso America/Fortaleza. */
export function formatarDataHora(iso?: string): string {
  if (!iso) return '';
  // Sem fuso explícito, o valor já está em horário local de Fortaleza.
  if (!/[zZ]|[+-]\d{2}:\d{2}$/.test(iso)) {
    const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
    return m ? `${m[3]}/${m[2]}/${m[1]} ${m[4]}:${m[5]}` : iso;
  }
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  const partes = new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Fortaleza',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const v = (t: string) => partes.find((p) => p.type === t)?.value ?? '';
  return `${v('day')}/${v('month')}/${v('year')} ${v('hour')}:${v('minute')}`;
}
