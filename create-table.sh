#!/usr/bin/env bash
set -euo pipefail

aws dynamodb create-table \
  --endpoint-url http://localhost:8000 \
  --region us-east-1 \
  --table-name battleship-games-beta \
  --attribute-definitions AttributeName=gameId,AttributeType=S \
  --key-schema AttributeName=gameId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --no-cli-pager
