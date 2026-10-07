import { Conflito, DisposicaoCatalogo, PeriodoReserva, RecursoCatalogo, Reserva } from './reservas.service';
import { AMBIENTES_DEMONSTRACAO } from '../painel-solicitante/dados-demonstracao';

/**
 * Dados fictícios usados quando a API não responde (modo demonstração).
 * Nenhum dado real; os nomes são genéricos.
 */
export const AMBIENTES_RESERVA_DEMO = AMBIENTES_DEMONSTRACAO;

export const DISPOSICOES_DEMO: DisposicaoCatalogo[] = [
  { id: 'DISP-1', descricao: 'Auditório', imagem: 'disp_001_auditorio.jpg', alt: 'Cadeiras enfileiradas voltadas para a frente, em formato de auditório' },
  { id: 'DISP-2', descricao: 'Auditório com mesas', imagem: 'disp_002_auditorio-com-mesas.jpg', alt: 'Fileiras de mesas com cadeiras voltadas para a frente' },
  { id: 'DISP-3', descricao: 'Espinha de peixe', imagem: 'disp_003_espinha-de-peixe.jpg' },
  { id: 'DISP-5', descricao: 'Mesa única', imagem: 'disp_005_mesa-unica.jpg', alt: 'Uma mesa central com cadeiras ao redor' },
];

export const RECURSOS_DEMO: RecursoCatalogo[] = [
  { id: 'REC-1', descricao: 'Projetor', limitado: true, disponibilidade: 3, grupoId: 'G1', grupoNome: 'Equipamentos', grupoOrdem: 1 },
  { id: 'REC-2', descricao: 'Notebook', limitado: true, disponibilidade: 5, grupoId: 'G1', grupoNome: 'Equipamentos', grupoOrdem: 1 },
  { id: 'REC-3', descricao: 'Videoconferência', limitado: false, grupoId: 'G1', grupoNome: 'Equipamentos', grupoOrdem: 1, ambientesPermitidos: ['AMB-2'] },
  { id: 'REC-4', descricao: 'Água', limitado: false, grupoId: 'G2', grupoNome: 'Copa', grupoOrdem: 2 },
  { id: 'REC-5', descricao: 'Café', limitado: false, grupoId: 'G2', grupoNome: 'Copa', grupoOrdem: 2 },
];

/** Reservas em memória do modo demonstração. */
const reservasDemo: Reserva[] = [
  {
    id: 'DEMO-1',
    ambienteId: 'AMB-1',
    finalidade: 'Reunião de planejamento (demonstração)',
    participantes: 20,
    disposicaoId: 'DISP-1',
    complemento: '',
    periodos: [{ inicio: amanha('09:00'), termino: amanha('11:00') }],
    recursos: [{ recursoId: 'REC-1', quantidade: 1 }, { recursoId: 'REC-4' }],
    solicitanteNome: 'Solicitante de demonstração',
    status: 'PREVISTA',
    ultimaAlteracao: new Date().toISOString(),
    pedidosSnp: [{ numero: 'SNP-0001', link: 'https://snp.exemplo.gov.br/pedidos/SNP-0001' }],
  },
];

function amanha(hora: string): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${hora}`;
}

export function listarDemo(): Reserva[] {
  return reservasDemo.map((r) => structuredClone(r));
}

export function obterDemo(id: string): Reserva | undefined {
  const r = reservasDemo.find((x) => x.id === id);
  return r ? structuredClone(r) : undefined;
}

/** Grava (inclui ou substitui) uma Reserva em memória. */
export function salvarDemo(r: Reserva, nomeSolicitante: string): Reserva {
  const copia: Reserva = {
    ...structuredClone(r),
    id: r.id ?? `DEMO-${reservasDemo.length + 1}`,
    solicitanteNome: r.solicitanteNome ?? nomeSolicitante,
    status: 'PREVISTA',
    ultimaAlteracao: new Date().toISOString(),
    pedidosSnp: r.pedidosSnp ?? [],
  };
  const i = reservasDemo.findIndex((x) => x.id === copia.id);
  if (i >= 0) reservasDemo[i] = copia;
  else reservasDemo.push(copia);
  return structuredClone(copia);
}

/** Cancela uma Reserva em memória (Req. 12.5); retorna `undefined` se não existir. */
export function cancelarDemo(id: string): Reserva | undefined {
  const r = reservasDemo.find((x) => x.id === id);
  if (!r) return undefined;
  r.status = 'CANCELADA';
  r.ultimaAlteracao = new Date().toISOString();
  return structuredClone(r);
}

/** Verificação simplificada de conflito com Margem de 30 min (RN5) sobre as reservas em memória. */
export function verificarDemo(ambienteId: string, periodo: PeriodoReserva, reservaId?: string): Conflito[] {
  const margem = 30 * 60_000;
  const ini = new Date(periodo.inicio).getTime();
  const fim = new Date(periodo.termino).getTime();
  const conflitos: Conflito[] = [];
  for (const r of reservasDemo) {
    if (r.id === reservaId || r.ambienteId !== ambienteId || r.status === 'CANCELADA') continue;
    for (const p of r.periodos) {
      const bi = new Date(p.inicio).getTime();
      const bf = new Date(p.termino).getTime();
      if (ini < bf + margem && bi < fim + margem) {
        conflitos.push({
          codigo: 'RN5_CONFLITO_HORARIO',
          mensagem: `RN5: o ambiente já está reservado neste período (reserva ${r.id}), considerando a margem de 30 minutos.`,
          reservaId: r.id,
        });
      }
    }
  }
  return conflitos;
}
