import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Grupo } from './perfis';

/** Exige usuário autenticado; caso contrário, envia ao login guardando a URL de retorno. */
export const autenticadoGuard: CanActivateFn = (_rota, estado) => {
  const auth = inject(AuthService);
  if (auth.autenticado()) return true;
  auth.guardarRetorno(estado.url);
  return inject(Router).parseUrl('/login');
};

/** Fábrica de guard por grupo do Cognito: permite se o usuário pertence a algum dos grupos. */
export function grupoGuard(...grupos: Grupo[]): CanActivateFn {
  return (_rota, estado) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.autenticado()) {
      auth.guardarRetorno(estado.url);
      return router.parseUrl('/login');
    }
    return auth.possuiAlgumGrupo(grupos) ? true : router.parseUrl('/acesso-negado');
  };
}

/** Impede que usuário já autenticado veja a tela de login. */
export const anonimoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.autenticado() ? inject(Router).parseUrl(auth.rotaPainel()) : true;
};
