import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Recurso solicitado na Reserva; `quantidade` ausente para itens sem contagem. */
export interface RecursoCard {
  descricao: string;
  quantidade?: number;
}

/** Pedido enviado ao SNP, com link para consulta (Req. 16.3). */
export interface PedidoSnpCard {
  numero: string;
  link: string;
}

/** Card de uma Reserva com Período na data da coluna (Req. 16.2, 16.3). */
export interface CardReserva {
  reservaId: string;
  /** Início do Período em ISO-8601 (`HH:mm` ou data-hora completa). */
  inicio: string;
  termino: string;
  finalidade: string;
  solicitanteNome: string;
  ambiente: string;
  recursos: RecursoCard[];
  pedidosSnp: PedidoSnpCard[];
  cancelada: boolean;
}

/** Coluna do painel: uma data (`yyyy-MM-dd`) com seus cards. */
export interface ColunaAtendente {
  data: string;
  cards: CardReserva[];
}

export interface RespostaPainelAtendente {
  colunas: ColunaAtendente[];
}

export interface FiltroPainelAtendente {
  /** Data de referência `yyyy-MM-dd`. */
  data: string;
  colunas: number;
  fds: boolean;
}

/** Acesso à API do Painel do Atendente. A visibilidade é aplicada no backend (Req. 16.4, 16.5). */
@Injectable({ providedIn: 'root' })
export class PainelAtendenteService {
  private readonly http = inject(HttpClient);

  consultar(filtro: FiltroPainelAtendente): Observable<RespostaPainelAtendente> {
    const params = new HttpParams()
      .set('data', filtro.data)
      .set('colunas', filtro.colunas)
      .set('fds', filtro.fds);
    return this.http.get<RespostaPainelAtendente>('/api/paineis/atendente', { params });
  }
}
