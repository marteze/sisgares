import { Ambiente } from './ambiente';

/**
 * Configuração padrão (execução local): autenticação simulada, sem Cognito.
 * Para a nuvem, use a configuração `nuvem` (environment.nuvem.ts).
 */
export const environment: Ambiente = {
  modoAutenticacao: 'mock',
  apiBase: '/api',
};
