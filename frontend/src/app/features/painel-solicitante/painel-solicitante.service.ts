import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Estado de uma célula de 30 minutos calculado pelo backend (Req. 15.11). */
export type EstadoCelula = 'LIVRE' | 'OCUPADO' | 'MARGEM' | 'ULTRAPASSADO' | 'SEM_ANTECEDENCIA';

/** Célula da grade: `inicio` em ISO-8601 (`HH:mm` ou data-hora completa). */
export interface CelulaPainel {
  inicio: string;
  estado: EstadoCelula;
  /** Presente apenas quando o usuário pode abrir a Reserva (Req. 15.5). */
  reservaId?: string;
}

/** Coluna da grade: uma data (`yyyy-MM-dd`) com suas células. */
export interface ColunaPainel {
  data: string;
  celulas: CelulaPainel[];
}

export interface RespostaPainelSolicitante {
  ambienteId?: string;
  colunas: ColunaPainel[];
}

export interface AmbienteResumo {
  id: string;
  nome: string;
}

export interface FiltroPainel {
  ambiente: string;
  /** Data de referência `yyyy-MM-dd`. */
  data: string;
  colunas: number;
  fds: boolean;
}

/** Acesso às APIs do Painel do Solicitante e do catálogo de Ambientes. */
@Injectable({ providedIn: 'root' })
export class PainelSolicitanteService {
  private readonly http = inject(HttpClient);

  listarAmbientes(): Observable<AmbienteResumo[]> {
    return this.http.get<AmbienteResumo[]>('/api/catalogo/ambientes');
  }

  consultar(filtro: FiltroPainel): Observable<RespostaPainelSolicitante> {
    const params = new HttpParams()
      .set('ambiente', filtro.ambiente)
      .set('data', filtro.data)
      .set('colunas', filtro.colunas)
      .set('fds', filtro.fds);
    return this.http.get<RespostaPainelSolicitante>('/api/paineis/solicitante', { params });
  }
}
