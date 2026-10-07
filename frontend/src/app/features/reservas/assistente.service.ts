import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';

/** Recurso proposto pelo Assistente. */
export interface RecursoProposto {
  recursoId: number | string;
  quantidade: number;
}

/** Proposta de preenchimento do formulário (Req. 18.5); nunca é gravada como Reserva. */
export interface PropostaAssistente {
  ambienteId?: number | string;
  /** `yyyy-MM-dd`. */
  data?: string;
  /** `HH:mm`. */
  inicio?: string;
  termino?: string;
  participantes?: number;
  finalidade?: string;
  recursos?: RecursoProposto[];
}

export interface RespostaAssistente {
  proposta: PropostaAssistente;
  camposNaoPreenchidos: string[];
}

/** Limite de caracteres da descrição em linguagem natural (Req. 18.1). */
export const LIMITE_DESCRICAO = 1000;

/** Rótulos em português dos campos que o Assistente pode deixar sem preencher. */
export const ROTULOS_CAMPOS: Record<string, string> = {
  ambienteId: 'Ambiente',
  data: 'Data',
  inicio: 'Horário de início',
  termino: 'Horário de término',
  participantes: 'Número de participantes',
  finalidade: 'Finalidade',
  recursos: 'Recursos',
};

/** Acesso ao Assistente de reserva (`POST /api/assistente/propostas`). */
@Injectable({ providedIn: 'root' })
export class AssistenteService {
  private readonly http = inject(HttpClient);

  propor(descricao: string): Observable<RespostaAssistente> {
    return this.http
      .post<RespostaAssistente>('/api/assistente/propostas', { descricao })
      .pipe(timeout(15000));
  }
}
