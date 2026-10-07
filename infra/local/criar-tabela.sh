#!/usr/bin/env bash
# Cria a tabela single-table "sisgares" (PK/SK + GSI1..GSI5) no DynamoDB Local.
# Credenciais fictícias: o DynamoDB Local não as valida.
set -euo pipefail

ENDPOINT="${DYNAMODB_ENDPOINT:-http://localhost:8000}"
TABELA="${TABELA_SISGARES:-sisgares}"
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1

if aws dynamodb describe-table --table-name "$TABELA" --endpoint-url "$ENDPOINT" >/dev/null 2>&1; then
  echo "Tabela $TABELA já existe em $ENDPOINT."
  exit 0
fi

atributos="AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S"
gsis=""
for n in 1 2 3 4 5; do
  atributos="$atributos AttributeName=GSI${n}PK,AttributeType=S AttributeName=GSI${n}SK,AttributeType=S"
  [ -n "$gsis" ] && gsis="$gsis,"
  gsis="$gsis{\"IndexName\":\"GSI${n}\",\"KeySchema\":[{\"AttributeName\":\"GSI${n}PK\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"GSI${n}SK\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"}}"
done

# shellcheck disable=SC2086
aws dynamodb create-table \
  --table-name "$TABELA" \
  --endpoint-url "$ENDPOINT" \
  --attribute-definitions $atributos \
  --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
  --global-secondary-indexes "[$gsis]" \
  --billing-mode PAY_PER_REQUEST >/dev/null

echo "Tabela $TABELA criada em $ENDPOINT."
