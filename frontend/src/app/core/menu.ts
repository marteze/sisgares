import { GRUPOS, Grupo } from './auth/perfis';

/** Item do menu principal; exibido somente aos grupos listados. */
export interface ItemMenu {
  rotulo: string;
  rota: string;
  icone: string;
  grupos: readonly Grupo[];
}

const { ADMINISTRADOR, SETOR_ATENDENTE, SOLICITANTE } = GRUPOS;

/** Menu completo, conforme a tabela de rotas do design. */
export const ITENS_MENU: readonly ItemMenu[] = [
  { rotulo: 'Painel do solicitante', rota: '/painel/solicitante', icone: 'calendar_view_week', grupos: [SOLICITANTE, ADMINISTRADOR] },
  { rotulo: 'Painel do atendente', rota: '/painel/atendente', icone: 'dashboard', grupos: [SETOR_ATENDENTE, ADMINISTRADOR] },
  { rotulo: 'Reservas', rota: '/reservas', icone: 'event_note', grupos: [SOLICITANTE, ADMINISTRADOR] },
  { rotulo: 'Nova reserva', rota: '/reservas/nova', icone: 'add_circle', grupos: [SOLICITANTE, ADMINISTRADOR] },
  { rotulo: 'Setores', rota: '/cadastros/setores', icone: 'apartment', grupos: [ADMINISTRADOR] },
  { rotulo: 'Ambientes', rota: '/cadastros/ambientes', icone: 'meeting_room', grupos: [ADMINISTRADOR] },
  { rotulo: 'Disposições', rota: '/cadastros/disposicoes', icone: 'event_seat', grupos: [ADMINISTRADOR] },
  { rotulo: 'Grupos de recursos', rota: '/cadastros/grupos', icone: 'category', grupos: [ADMINISTRADOR] },
  { rotulo: 'Recursos', rota: '/cadastros/recursos', icone: 'devices', grupos: [ADMINISTRADOR] },
  { rotulo: 'Configuração', rota: '/configuracao', icone: 'settings', grupos: [ADMINISTRADOR] },
  { rotulo: 'Caixa simulada', rota: '/caixa-simulada', icone: 'mail', grupos: [ADMINISTRADOR] },
];

/** Filtra os itens do menu visíveis para os grupos do usuário. */
export function menuParaGrupos(grupos: readonly Grupo[]): ItemMenu[] {
  return ITENS_MENU.filter((item) => item.grupos.some((g) => grupos.includes(g)));
}
