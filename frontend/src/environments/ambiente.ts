/** Modos de autenticação suportados pelo frontend. */
export type ModoAutenticacao = 'mock' | 'cognito';

/** Parâmetros do Cognito Hosted UI (OAuth2 Authorization Code + PKCE, cliente público sem segredo). */
export interface ConfiguracaoCognito {
  /** Domínio do Hosted UI, ex.: https://sisgares.auth.us-east-1.amazoncognito.com */
  dominio: string;
  /** App client público (sem client secret). */
  clientId: string;
  /** URL de retorno registrada no app client, ex.: https://app.exemplo/auth/callback */
  redirectUri: string;
  /** URL para onde o Cognito redireciona após o logout. */
  logoutUri: string;
  /** Escopos solicitados. */
  escopos: string[];
}

/** Estrutura comum dos arquivos de environment. */
export interface Ambiente {
  modoAutenticacao: ModoAutenticacao;
  /** Prefixo da API; somente requisições para ele recebem o cabeçalho Authorization. */
  apiBase: string;
  cognito?: ConfiguracaoCognito;
}
