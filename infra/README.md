# Infra (AWS)

Esboço de implantação para o ambiente do hackathon. Não há recursos provisionados
aqui ainda; este documento registra as decisões e o caminho de deploy.

## Alvo

- **Frontend**: build estático (`frontend/dist`) servido por S3 + CloudFront, ou
  AWS Amplify Hosting.
- **Backend**: Node em AWS App Runner / ECS Fargate, ou empacotado em Lambda +
  API Gateway. Como o estado é em memória (CSVs), uma única instância basta para a demo.
- **Autenticação**: Amazon Cognito (User Pool + App Client). O frontend obtém o JWT
  e envia no `Authorization`; o backend valida (`AUTH_MODE=cognito`).

## Variáveis de ambiente (backend)

Ver `backend/.env.example`. Em produção, definir:

```
AUTH_MODE=cognito
COGNITO_USER_POOL_ID=...
COGNITO_CLIENT_ID=...
COGNITO_REGION=us-east-1
CORS_ORIGIN=https://<dominio-do-frontend>
```

## Passos de deploy (resumo)

1. `cd frontend && npm run build` → publicar `dist/` no S3/Amplify.
2. `cd backend && npm run build` → publicar `dist/` no App Runner/ECS/Lambda.
3. Criar o User Pool no Cognito e preencher as variáveis.
4. Ajustar `CORS_ORIGIN` para o domínio do frontend.

> Para a demonstração local, não é necessário nada disto: basta `npm run dev` em
> cada pasta (ver README na raiz).
