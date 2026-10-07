// Cliente HTTP da API. Injeta o "usuário" atual via headers (modo mock).
import type {
  Ambiente,
  CardAtendente,
  Configuracao,
  Disposicao,
  Envolvido,
  GradeResposta,
  GrupoRecurso,
  NovaReservaPayload,
  Perfil,
  Recurso,
  Reserva,
  UnidadeMacro,
  VisaoGeralResposta,
} from '../types';

const BASE = '/api';

export interface SessaoUsuario {
  id: string;
  nome: string;
  email: string;
  perfil: Perfil;
  setorId?: number;
  unidadeId?: number;
}

function headers(u: SessaoUsuario): HeadersInit {
  const h: Record<string, string> = {
    'Content-Type': 'application/json',
    'x-user-id': u.id,
    'x-user-nome': u.nome,
    'x-user-email': u.email,
    'x-user-perfil': u.perfil,
  };
  if (u.setorId) h['x-user-setor'] = String(u.setorId);
  if (u.unidadeId) h['x-user-unidade'] = String(u.unidadeId);
  return h;
}

async function req<T>(
  path: string,
  u: SessaoUsuario,
  init?: RequestInit
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { ...headers(u), ...(init?.headers ?? {}) },
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((body as { erro?: string }).erro ?? `Erro ${res.status}`);
  }
  return body as T;
}

export const api = {
  // Catálogo
  unidades: (u: SessaoUsuario) => req<UnidadeMacro[]>('/unidades', u),
  ambientes: (u: SessaoUsuario, unidadeId?: number) =>
    req<Ambiente[]>(`/ambientes${unidadeId ? `?unidadeId=${unidadeId}` : ''}`, u),
  recursos: (u: SessaoUsuario, opts?: { unidadeId?: number; ambienteId?: number }) => {
    const qs = new URLSearchParams();
    if (opts?.unidadeId) qs.set('unidadeId', String(opts.unidadeId));
    if (opts?.ambienteId) qs.set('ambienteId', String(opts.ambienteId));
    const s = qs.toString();
    return req<Recurso[]>(`/recursos${s ? `?${s}` : ''}`, u);
  },
  grupos: (u: SessaoUsuario) => req<GrupoRecurso[]>('/grupos-recurso', u),
  disposicoes: (u: SessaoUsuario) => req<Disposicao[]>('/disposicoes', u),
  envolvidos: (u: SessaoUsuario) => req<Envolvido[]>('/envolvidos', u),

  // Configurações (RF09)
  config: (u: SessaoUsuario) => req<Configuracao>('/config', u),
  salvarConfig: (u: SessaoUsuario, c: Partial<Configuracao>) =>
    req<Configuracao>('/config', u, { method: 'PUT', body: JSON.stringify(c) }),

  // Reservas (RF10-RF15)
  reservas: (u: SessaoUsuario) => req<Reserva[]>('/reservas', u),
  reserva: (u: SessaoUsuario, id: number) => req<Reserva>(`/reservas/${id}`, u),
  criarReserva: (u: SessaoUsuario, p: NovaReservaPayload) =>
    req<Reserva>('/reservas', u, { method: 'POST', body: JSON.stringify(p) }),
  alterarReserva: (u: SessaoUsuario, id: number, p: NovaReservaPayload) =>
    req<Reserva>(`/reservas/${id}`, u, { method: 'PUT', body: JSON.stringify(p) }),
  cancelar: (u: SessaoUsuario, id: number) =>
    req<Reserva>(`/reservas/${id}/cancelar`, u, {
      method: 'POST',
      body: JSON.stringify({ confirmar: true }),
    }),
  verificarConflito: (
    u: SessaoUsuario,
    ambienteId: number,
    inicio: string,
    termino: string,
    ignorar?: number
  ) => {
    const qs = new URLSearchParams({
      ambienteId: String(ambienteId),
      inicio,
      termino,
    });
    if (ignorar) qs.set('ignorar', String(ignorar));
    return req<{ conflito: boolean; conflitos: { reservaId: number; inicio: string; termino: string }[] }>(
      `/reservas/verificar-conflito?${qs.toString()}`,
      u
    );
  },

  // Painéis (RF16/RF17)
  grade: (u: SessaoUsuario, ambienteId: number) =>
    req<GradeResposta>(`/paineis/grade/${ambienteId}`, u),
  visaoGeral: (u: SessaoUsuario) =>
    req<VisaoGeralResposta>('/paineis/visao-geral', u),
  atendente: (u: SessaoUsuario, setorId?: number) =>
    req<{ setorId: number | null; cards: CardAtendente[] }>(
      `/paineis/atendente${setorId ? `?setorId=${setorId}` : ''}`,
      u
    ),
};
