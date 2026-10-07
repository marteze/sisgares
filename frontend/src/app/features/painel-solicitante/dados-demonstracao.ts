import { AmbienteResumo, CelulaPainel, ColunaPainel, FiltroPainel, RespostaPainelSolicitante } from './painel-solicitante.service';

/**
 * Dados fictícios usados quando a API não responde (modo demonstração).
 * Reproduz de forma simplificada as regras de Margem, horário ultrapassado
 * e antecedência mínima apenas para ilustrar a grade.
 */
export const AMBIENTES_DEMONSTRACAO: AmbienteResumo[] = [
  { id: 'AMB-1', nome: 'Auditório principal (demonstração)' },
  { id: 'AMB-2', nome: 'Sala de reuniões 101 (demonstração)' },
  { id: 'AMB-3', nome: 'Sala de treinamento (demonstração)' },
];

const INICIO_FAIXA_MIN = 8 * 60;
const FIM_FAIXA_MIN = 18 * 60;
const ANTECEDENCIA_MIN = 120;

/** Formata uma data local como `yyyy-MM-dd`. */
export function formatarDataIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Converte `yyyy-MM-dd` em Date local (meia-noite). */
export function lerDataIso(valor: string): Date {
  const [a, m, d] = valor.split('-').map(Number);
  return new Date(a, (m ?? 1) - 1, d ?? 1);
}

/** Datas consecutivas a partir da referência, omitindo fins de semana se `fds` for falso (Req. 15.2). */
export function datasDaGrade(referencia: string, colunas: number, fds: boolean): string[] {
  const datas: string[] = [];
  const atual = lerDataIso(referencia);
  while (datas.length < colunas) {
    const dia = atual.getDay();
    if (fds || (dia !== 0 && dia !== 6)) datas.push(formatarDataIso(atual));
    atual.setDate(atual.getDate() + 1);
  }
  return datas;
}

function hhmm(min: number): string {
  return `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;
}

export function gerarPainelDemonstracao(filtro: FiltroPainel, agora = new Date()): RespostaPainelSolicitante {
  const semente = filtro.ambiente.length;
  const colunas: ColunaPainel[] = datasDaGrade(filtro.data, filtro.colunas, filtro.fds).map((data, i) => {
    // Um período ocupado fictício por dia, em horário variável.
    const ocupInicio = INICIO_FAIXA_MIN + (((i + semente) * 3) % 14) * 30;
    const ocupFim = ocupInicio + 60 + (i % 2) * 30;
    const base = lerDataIso(data).getTime();
    const celulas: CelulaPainel[] = [];
    for (let m = INICIO_FAIXA_MIN; m < FIM_FAIXA_MIN; m += 30) {
      const instante = base + m * 60_000;
      let estado: CelulaPainel['estado'] = 'LIVRE';
      let reservaId: string | undefined;
      if (m >= ocupInicio && m < ocupFim) {
        estado = 'OCUPADO';
        reservaId = i % 2 === 0 ? `DEMO-${i + 1}` : undefined;
      } else if (m === ocupInicio - 30 || m === ocupFim) {
        estado = 'MARGEM';
      } else if (instante <= agora.getTime()) {
        estado = 'ULTRAPASSADO';
      } else if (instante - agora.getTime() < ANTECEDENCIA_MIN * 60_000) {
        estado = 'SEM_ANTECEDENCIA';
      }
      celulas.push({ inicio: hhmm(m), estado, reservaId });
    }
    return { data, celulas };
  });
  return { ambienteId: filtro.ambiente, colunas };
}
