/** Grupos do Cognito que definem os perfis do SISGARES. */
export const GRUPOS = {
  ADMINISTRADOR: 'Administrador',
  SETOR_ATENDENTE: 'Setor_Atendente',
  SOLICITANTE: 'Solicitante',
} as const;

export type Grupo = (typeof GRUPOS)[keyof typeof GRUPOS];

/** Rótulos de interface para cada grupo. */
export const ROTULO_GRUPO: Record<Grupo, string> = {
  Administrador: 'Administrador',
  Setor_Atendente: 'Setor atendente',
  Solicitante: 'Solicitante',
};

/** Dados do usuário autenticado extraídos do token. */
export interface Usuario {
  sub: string;
  nome: string;
  email: string;
  grupos: Grupo[];
}

/** Usuários fictícios disponíveis no modo mock (um por perfil). */
export const USUARIOS_FICTICIOS: ReadonlyArray<Usuario> = [
  { sub: 'mock-admin', nome: 'Ana Administradora', email: 'admin@exemplo.gov.br', grupos: ['Administrador'] },
  { sub: 'mock-atendente', nome: 'Bruno Atendente', email: 'atendente@exemplo.gov.br', grupos: ['Setor_Atendente'] },
  { sub: 'mock-solicitante', nome: 'Carla Solicitante', email: 'solicitante@exemplo.gov.br', grupos: ['Solicitante'] },
];

/** Filtra valores desconhecidos, mantendo apenas grupos reconhecidos. */
export function normalizarGrupos(valor: unknown): Grupo[] {
  const conhecidos = Object.values(GRUPOS) as string[];
  const lista = Array.isArray(valor) ? valor : typeof valor === 'string' ? [valor] : [];
  return lista.filter((g): g is Grupo => typeof g === 'string' && conhecidos.includes(g));
}
