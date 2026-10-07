import { Ambiente } from './ambiente';

/**
 * Configuração de nuvem: Cognito Hosted UI com PKCE.
 * Valores obtidos das saídas do deploy (domínio do user pool e app client público).
 * O client ID é público (cliente sem segredo, PKCE). Não há segredos neste arquivo.
 */
export const environment: Ambiente = {
  modoAutenticacao: 'cognito',
  apiBase: '/api',
  cognito: {
    dominio: 'https://sisgares-hackaton.auth.us-east-1.amazoncognito.com',
    clientId: '1nlicv2todgpen7q2kkj967bh7',
    redirectUri: 'https://d2tz68zpw6i1uf.cloudfront.net/auth/callback',
    logoutUri: 'https://d2tz68zpw6i1uf.cloudfront.net/login',
    escopos: ['openid', 'email', 'profile'],
  },
};
