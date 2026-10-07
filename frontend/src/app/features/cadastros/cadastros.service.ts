import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { CONFIGS_CADASTRO, DADOS_DEMO_CADASTRO, EntidadeCadastro, ItemCadastro } from './cadastros.config';

/** Acesso a `/api/catalogo/{entidade}` com armazenamento em memória para o modo demonstração. */
@Injectable({ providedIn: 'root' })
export class CadastrosService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/catalogo';
  /** Cópia mutável dos dados fictícios, mantida enquanto a aplicação estiver aberta. */
  private readonly demo = structuredClone(DADOS_DEMO_CADASTRO);
  private proximoIdDemo = 1000;

  listar(entidade: EntidadeCadastro, todos: boolean): Observable<ItemCadastro[]> {
    const params = todos ? new HttpParams().set('todos', true) : undefined;
    return this.http.get<ItemCadastro[]>(`${this.base}/${entidade}`, { params });
  }

  criar(entidade: EntidadeCadastro, corpo: Record<string, unknown>): Observable<ItemCadastro> {
    return this.http.post<ItemCadastro>(`${this.base}/${entidade}`, corpo);
  }

  alterar(entidade: EntidadeCadastro, id: string, corpo: Record<string, unknown>): Observable<ItemCadastro> {
    return this.http.put<ItemCadastro>(`${this.base}/${entidade}/${encodeURIComponent(id)}`, corpo);
  }

  inativar(entidade: EntidadeCadastro, id: string): Observable<ItemCadastro> {
    return this.http.patch<ItemCadastro>(`${this.base}/${entidade}/${encodeURIComponent(id)}/inativar`, {});
  }

  /** Envia a imagem da Disposição como data URL base64 (aceita pelo backend). */
  enviarImagem(id: string, conteudo: string): Observable<ItemCadastro> {
    return this.http.post<ItemCadastro>(`${this.base}/disposicoes/${encodeURIComponent(id)}/imagem`, { conteudo });
  }

  // ---- Modo demonstração (somente em memória) ----

  listarDemo(entidade: EntidadeCadastro, todos: boolean): ItemCadastro[] {
    return this.demo[entidade].filter((i) => todos || i.ativo).map((i) => ({ ...i }));
  }

  salvarDemo(entidade: EntidadeCadastro, id: string | null, corpo: Record<string, unknown>): Observable<ItemCadastro> {
    const dados = CONFIGS_CADASTRO[entidade].deRequisicao(corpo);
    const lista = this.demo[entidade];
    const existente = id ? lista.find((i) => i.id === id) : undefined;
    if (existente) {
      Object.assign(existente, dados);
      return of({ ...existente });
    }
    const novo: ItemCadastro = { ...dados, id: String(this.proximoIdDemo++), ativo: true };
    lista.push(novo);
    return of({ ...novo });
  }

  inativarDemo(entidade: EntidadeCadastro, id: string): void {
    const item = this.demo[entidade].find((i) => i.id === id);
    if (item) item.ativo = false;
  }

  imagemDemo(id: string, conteudo: string): void {
    const item = this.demo.disposicoes.find((i) => i.id === id);
    if (item) item['imagem'] = conteudo;
  }
}
