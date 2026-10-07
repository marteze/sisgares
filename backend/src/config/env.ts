// Configuração central lida do ambiente, com defaults de desenvolvimento.
export const env = {
  port: Number(process.env.PORT ?? 3001),
  corsOrigin: process.env.CORS_ORIGIN ?? 'http://localhost:5173',
  authMode: (process.env.AUTH_MODE ?? 'mock') as 'mock' | 'cognito',
  cognito: {
    userPoolId: process.env.COGNITO_USER_POOL_ID ?? '',
    clientId: process.env.COGNITO_CLIENT_ID ?? '',
    region: process.env.COGNITO_REGION ?? 'us-east-1',
  },
};
