import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { anonimoGuard, autenticadoGuard, grupoGuard } from './core/auth/auth.guards';
import { AuthService } from './core/auth/auth.service';
import { GRUPOS } from './core/auth/perfis';

const { ADMINISTRADOR, SETOR_ATENDENTE, SOLICITANTE } = GRUPOS;

/** Tela provisória para rotas cujas telas serão implementadas em tarefas futuras. */
const emConstrucao = () => import('./features/em-construcao/em-construcao').then((m) => m.EmConstrucao);

export const routes: Routes = [
  // `/` leva ao painel do perfil (Req. 20.7) ou ao login quando não autenticado.
  { path: '', pathMatch: 'full', redirectTo: () => inject(AuthService).rotaInicial() },
  {
    path: 'login',
    title: 'Entrar | SISGARES',
    canActivate: [anonimoGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'auth/callback',
    title: 'Concluindo login | SISGARES',
    loadComponent: () => import('./features/login/callback').then((m) => m.Callback),
  },
  {
    path: 'acesso-negado',
    title: 'Acesso negado | SISGARES',
    canActivate: [autenticadoGuard],
    loadComponent: () => import('./features/acesso-negado/acesso-negado').then((m) => m.AcessoNegado),
  },
  {
    path: 'painel/solicitante',
    title: 'Painel do solicitante | SISGARES',
    canActivate: [grupoGuard(SOLICITANTE, ADMINISTRADOR)],
    data: { titulo: 'Painel do solicitante' },
    loadComponent: () =>
      import('./features/painel-solicitante/painel-solicitante').then((m) => m.PainelSolicitante),
  },
  {
    path: 'painel/atendente',
    title: 'Painel do atendente | SISGARES',
    canActivate: [grupoGuard(SETOR_ATENDENTE, ADMINISTRADOR)],
    data: { titulo: 'Painel do atendente' },
    loadComponent: () =>
      import('./features/painel-atendente/painel-atendente').then((m) => m.PainelAtendente),
  },
  {
    path: 'reservas',
    canActivate: [grupoGuard(SOLICITANTE, ADMINISTRADOR)],
    children: [
      {
        path: '',
        title: 'Reservas | SISGARES',
        data: { titulo: 'Reservas' },
        loadComponent: () => import('./features/reservas/lista-reservas').then((m) => m.ListaReservas),
      },
      {
        path: 'nova',
        title: 'Nova reserva | SISGARES',
        data: { titulo: 'Nova reserva' },
        loadComponent: () => import('./features/reservas/formulario-reserva').then((m) => m.FormularioReserva),
      },
      {
        path: ':id',
        title: 'Reserva | SISGARES',
        data: { titulo: 'Reserva' },
        loadComponent: () => import('./features/reservas/formulario-reserva').then((m) => m.FormularioReserva),
      },
    ],
  },
  {
    path: 'cadastros',
    canActivate: [grupoGuard(ADMINISTRADOR)],
    children: [
      { path: 'setores', title: 'Setores | SISGARES', data: { titulo: 'Setores' }, loadComponent: emConstrucao },
      { path: 'ambientes', title: 'Ambientes | SISGARES', data: { titulo: 'Ambientes' }, loadComponent: emConstrucao },
      { path: 'disposicoes', title: 'Disposições | SISGARES', data: { titulo: 'Disposições' }, loadComponent: emConstrucao },
      { path: 'grupos', title: 'Grupos de recursos | SISGARES', data: { titulo: 'Grupos de recursos' }, loadComponent: emConstrucao },
      { path: 'recursos', title: 'Recursos | SISGARES', data: { titulo: 'Recursos' }, loadComponent: emConstrucao },
    ],
  },
  {
    path: 'configuracao',
    title: 'Configuração | SISGARES',
    canActivate: [grupoGuard(ADMINISTRADOR)],
    data: { titulo: 'Configuração' },
    loadComponent: emConstrucao,
  },
  {
    path: 'caixa-simulada',
    title: 'Caixa simulada | SISGARES',
    canActivate: [grupoGuard(ADMINISTRADOR)],
    data: { titulo: 'Caixa simulada' },
    loadComponent: emConstrucao,
  },
  { path: '**', redirectTo: '' },
];
