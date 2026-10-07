# Execução local do SISGARES

Pré-requisitos: Docker, AWS SAM CLI, AWS CLI, Java 21, Maven e Node. Gere os jars antes com
`mvn -f backend/pom.xml package -DskipTests`.

Na pasta `infra/local/`:

1. `docker compose up -d` (DynamoDB Local na porta 8000, rede `sisgares-local`)
2. `./criar-tabela.sh` (tabela `sisgares` com PK/SK e GSI1 a GSI5)
3. `./seed-local.sh` (carrega `data/` e `imagens/`; imprime o relatório de carga)
4. `sam local start-api --docker-network sisgares-local` (API em `http://localhost:3000`)
5. Em `frontend/`: `npm start` (o proxy encaminha `/api` para a porta 3000)

A autenticação roda em `MODO_AUTH=mock` e as credenciais `local`/`local` são fictícias, aceitas
apenas pelo DynamoDB Local. A tabela é em memória: refaça os passos 2 e 3 após reiniciar o contêiner.
