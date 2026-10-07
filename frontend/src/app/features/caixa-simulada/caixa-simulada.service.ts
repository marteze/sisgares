import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';

/** Alteração de um campo da Reserva, exibida com `<del>`/`<ins>` (Req. 13.6). */
export interface AlteracaoCampo {
  campo: string;
  anterior: string | null;
  novo: string | null;
}

/** E-mail registrado na caixa simulada (SES em modo sandbox/simulado). */
export interface NotificacaoSimulada {
  id: string;
  destinatario: string;
  assunto: string;
  /** Instante ISO-8601 do envio. */
  enviadaEm: string;
  tipo: 'CRIACAO' | 'ALTERACAO' | 'CANCELAMENTO';
  reservaId: string;
  /** Texto puro do corpo; renderizado apenas por binding (sem `innerHTML`). */
  resumo: string;
  alteracoes: AlteracaoCampo[];
}

/**
 * Acesso às notificações da caixa simulada.
 * O endpoint `GET /api/notificacoes` ainda não existe no backend; a tela usa
 * dados de demonstração quando a chamada falha.
 */
@Injectable({ providedIn: 'root' })
export class CaixaSimuladaService {
  private readonly http = inject(HttpClient);

  listar(): Observable<NotificacaoSimulada[]> {
    return this.http.get<NotificacaoSimulada[]>('/api/notificacoes').pipe(timeout(5000));
  }
}

/** E-mails fictícios de exemplo (somente dados fictícios, domínio `exemplo.gov.br`). */
export const NOTIFICACOES_DEMO: NotificacaoSimulada[] = [
  {
    id: 'demo-3',
    destinatario: 'solicitante.ficticio@exemplo.gov.br',
    assunto: 'Reserva R-0003 alterada',
    enviadaEm: '2025-03-10T14:20:00-03:00',
    tipo: 'ALTERACAO',
    reservaId: 'R-0003',
    resumo: 'A reserva R-0003 foi alterada. Confira abaixo os campos modificados.',
    alteracoes: [
      { campo: 'Ambiente', anterior: 'Sala de Reuniões 1', novo: 'Auditório Principal' },
      { campo: 'Período', anterior: '12/03/2025 09:00–10:00', novo: '12/03/2025 14:00–16:00' },
      { campo: 'Participantes', anterior: '8', novo: '40' },
      { campo: 'Recursos', anterior: null, novo: 'Projetor (1)' },
    ],
  },
  {
    id: 'demo-2',
    destinatario: 'atendente.ficticio@exemplo.gov.br',
    assunto: 'Reserva R-0002 cancelada',
    enviadaEm: '2025-03-09T10:05:00-03:00',
    tipo: 'CANCELAMENTO',
    reservaId: 'R-0002',
    resumo: 'A reserva R-0002 (Treinamento interno) foi cancelada pelo solicitante.',
    alteracoes: [{ campo: 'Status', anterior: 'Prevista', novo: 'Cancelada' }],
  },
  {
    id: 'demo-1',
    destinatario: 'atendente.ficticio@exemplo.gov.br',
    assunto: 'Nova reserva R-0001',
    enviadaEm: '2025-03-08T08:30:00-03:00',
    tipo: 'CRIACAO',
    reservaId: 'R-0001',
    resumo: 'Nova reserva cadastrada: Reunião de planejamento, Sala de Reuniões 2, 11/03/2025 10:00–11:30.',
    alteracoes: [],
  },
];
