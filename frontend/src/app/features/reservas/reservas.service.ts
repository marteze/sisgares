import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Status calculado pelo backend (RN13). */
export type StatusReserva = 'PREVISTA' | 'EM_ANDAMENTO' | 'TRANSCORRIDA' | 'CANCELADA';

export interface AmbienteCatalogo {
  id: string;
  nome: string;
  ativo?: boolean;
}

export interface DisposicaoCatalogo {
  id: string;
  descricao: string;
  /** Nome do arquivo em `/imagens/icones-disposicao/`. */
  imagem?: string;
  /** Texto alternativo de `imagens/descricoes.md` (Req. 20.8). */
  alt?: string;
}

export interface RecursoCatalogo {
  id: string;
  descricao: string;
  limitado: boolean;
  disponibilidade?: number;
  grupoId?: string;
  grupoNome?: string;
  /** GREC_ORDEM do Grupo_Recurso. */
  grupoOrdem?: number;
  /** Vínculos VREC; vazio ou ausente = permitido em qualquer Ambiente (Req. 11.6). */
  ambientesPermitidos?: string[];
}

export interface PeriodoReserva {
  /** Data-hora ISO-8601 local (`yyyy-MM-ddTHH:mm`). */
  inicio: string;
  termino: string;
}

export interface SolicitacaoRecurso {
  recursoId: string;
  quantidade?: number;
}

export interface PedidoSnp {
  numero: string;
  link: string;
}

export interface Reserva {
  id?: string;
  /** `null` = Local_Proprio. */
  ambienteId: string | null;
  finalidade: string;
  participantes: number | null;
  disposicaoId?: string | null;
  complemento?: string;
  periodos: PeriodoReserva[];
  recursos: SolicitacaoRecurso[];
  solicitanteNome?: string;
  status?: StatusReserva;
  ultimaAlteracao?: string;
  pedidosSnp?: PedidoSnp[];
}

export interface ErroApi {
  codigo: string;
  mensagem: string;
  campo?: string;
}

export interface Conflito {
  mensagem: string;
  reservaId?: string;
  codigo?: string;
}

/** Acesso às APIs de Reserva e do catálogo usadas pelo formulário. */
@Injectable({ providedIn: 'root' })
export class ReservasService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Reserva[]> {
    return this.http.get<Reserva[]>('/api/reservas', { params: new HttpParams().set('minhas', true) });
  }

  obter(id: string): Observable<Reserva> {
    return this.http.get<Reserva>(`/api/reservas/${encodeURIComponent(id)}`);
  }

  criar(r: Reserva): Observable<Reserva> {
    return this.http.post<Reserva>('/api/reservas', r);
  }

  alterar(id: string, r: Reserva): Observable<Reserva> {
    return this.http.put<Reserva>(`/api/reservas/${encodeURIComponent(id)}`, r);
  }

  /** Pede o cancelamento da Reserva após confirmação do Solicitante (Req. 12.5). */
  cancelar(id: string): Observable<Reserva> {
    return this.http.post<Reserva>(`/api/reservas/${encodeURIComponent(id)}/cancelamento`, { confirmado: true });
  }

  verificarPeriodo(corpo: {
    ambienteId: string;
    periodo: PeriodoReserva;
    reservaId?: string;
  }): Observable<{ conflitos: Conflito[] }> {
    return this.http.post<{ conflitos: Conflito[] }>('/api/reservas/verificar-periodo', corpo);
  }

  ambientes(): Observable<AmbienteCatalogo[]> {
    return this.http.get<AmbienteCatalogo[]>('/api/catalogo/ambientes');
  }

  recursos(): Observable<RecursoCatalogo[]> {
    return this.http.get<RecursoCatalogo[]>('/api/catalogo/recursos');
  }

  disposicoes(): Observable<DisposicaoCatalogo[]> {
    return this.http.get<DisposicaoCatalogo[]>('/api/catalogo/disposicoes');
  }
}

/** Texto exibido para cada status. */
export const TEXTO_STATUS: Record<StatusReserva, string> = {
  PREVISTA: 'Prevista',
  EM_ANDAMENTO: 'Em andamento',
  TRANSCORRIDA: 'Transcorrida',
  CANCELADA: 'Cancelada',
};
