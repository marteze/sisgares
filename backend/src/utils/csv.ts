// Parser de CSV simples e sem dependências externas.
// Suporta campos entre aspas, aspas escapadas ("") e separador configurável.

export function parseCsv(content: string, separator = ','): string[][] {
  const rows: string[][] = [];
  let field = '';
  let row: string[] = [];
  let inQuotes = false;

  // Remove BOM se presente.
  if (content.charCodeAt(0) === 0xfeff) content = content.slice(1);

  for (let i = 0; i < content.length; i++) {
    const ch = content[i];

    if (inQuotes) {
      if (ch === '"') {
        if (content[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
      continue;
    }

    if (ch === '"') {
      inQuotes = true;
    } else if (ch === separator) {
      row.push(field);
      field = '';
    } else if (ch === '\n') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else if (ch === '\r') {
      // ignora; o \n cuida da quebra
    } else {
      field += ch;
    }
  }

  // Último campo/linha, se houver conteúdo.
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }

  // Descarta linhas totalmente vazias.
  return rows.filter((r) => !(r.length === 1 && r[0].trim() === ''));
}

// Converte CSV com cabeçalho em lista de objetos chave→valor (string).
export function parseCsvToObjects(
  content: string,
  separator = ','
): Record<string, string>[] {
  const rows = parseCsv(content, separator);
  if (rows.length === 0) return [];
  const header = rows[0];
  return rows.slice(1).map((r) => {
    const obj: Record<string, string> = {};
    header.forEach((h, idx) => {
      obj[h] = r[idx] ?? '';
    });
    return obj;
  });
}

// Normaliza IDs que vêm com separador de milhar (ex: "14.207" -> 14207).
export function parseIdMilhar(value: string): number {
  return Number(value.replace(/\./g, ''));
}

// Converte "DD/MM/AAAA HH:MM:SS" para ISO 8601.
export function parseDataHora(value: string): string {
  const m = value.trim().match(
    /^(\d{2})\/(\d{2})\/(\d{4})\s+(\d{2}):(\d{2}):(\d{2})$/
  );
  if (!m) return value;
  const [, dd, mm, yyyy, hh, min, ss] = m;
  return `${yyyy}-${mm}-${dd}T${hh}:${min}:${ss}`;
}

export function toNumberOrNull(value: string): number | null {
  const v = value.trim();
  if (v === '') return null;
  return Number(v);
}
