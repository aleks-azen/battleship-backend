#!/usr/bin/env bash
set -euo pipefail

echo "Starting DynamoDB Local..."
docker compose up -d

echo "Waiting for DynamoDB Local..."
until aws dynamodb list-tables --endpoint-url http://localhost:8000 --no-cli-pager >/dev/null 2>&1; do
  sleep 1
done

echo "Creating table (if not exists)..."
aws dynamodb create-table \
  --endpoint-url http://localhost:8000 \
  --table-name battleship-games-beta \
  --attribute-definitions AttributeName=gameId,AttributeType=S \
  --key-schema AttributeName=gameId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --no-cli-pager 2>/dev/null || echo "Table already exists"

echo "Starting backend on http://localhost:3000"
DYNAMODB_ENDPOINT=http://localhost:8000 GAMES_TABLE=battleship-games-beta ./gradlew run
