#!/usr/bin/env bash
# Executa o ImportadorSeed localmente: lê data/ e imagens/ e grava no DynamoDB Local.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$RAIZ/backend/seed/target/seed-0.1.0-SNAPSHOT.jar"

# Credenciais fictícias exigidas pelo SDK; o DynamoDB Local não as valida
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION=us-east-1
export TABELA_SISGARES="${TABELA_SISGARES:-sisgares}"
export DYNAMODB_ENDPOINT="${DYNAMODB_ENDPOINT:-http://localhost:8000}"

if [ ! -f "$JAR" ]; then
  echo "Gerando o jar do seed..."
  mvn -q -f "$RAIZ/backend/pom.xml" -pl seed -am package -DskipTests
fi

java -cp "$JAR" br.mp.mpf.sisgares.seed.ExecutarSeedLocal "$RAIZ/data" "$RAIZ/imagens"
