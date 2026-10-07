import { Ambiente } from './ambiente';

/**
 * Configuração de nuvem: Cognito Hosted UI com PKCE.
 * Os valores abaixo são placeholders e devem ser substituídos pelas saídas do stack CDK
 * (domínio do user pool e app client público). Não há segredos neste arquivo.
 */
export const environment: Ambiente = {
  modoAutenticacao: 'cognito',
  apiBase: '/api',
  cognito: {
    dominio: 'https://sisgares.auth.us-east-1.amazoncognito.com',
    clientId: 'SUBSTITUIR_PELO_CLIENT_ID',
    redirectUri: 'https://SUBSTITUIR_PELO_DOMINIO/auth/callback',
    logoutUri: 'https://SUBSTITUIR_PELO_DOMINIO/login',
    escopos: ['openid', 'email', 'profile'],
  },
};
