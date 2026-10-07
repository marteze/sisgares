import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

/** Indica se a URL pertence à API do SISGARES (prefixo `/api`, relativo ou na mesma origem). */
export function ehRequisicaoApi(url: string, base = environment.apiBase, origem = globalThis.location?.origin): boolean {
  const prefixo = base.endsWith('/') ? base.slice(0, -1) : base;
  const casa = (caminho: string) => caminho === prefixo || caminho.startsWith(`${prefixo}/`) || caminho.startsWith(`${prefixo}?`);
  if (url.startsWith('/') && !url.startsWith('//')) return casa(url);
  if (!origem) return false;
  try {
    const alvo = new URL(url);
    return alvo.origin === origem && casa(alvo.pathname);
  } catch {
    return false;
  }
}

/** Interceptor funcional: adiciona `Authorization: Bearer` somente em requisições para `/api`. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!ehRequisicaoApi(req.url)) return next(req);
  const token = inject(AuthService).token();
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
};
