#!/usr/bin/env bash
# EC2 container lifecycle — install to /opt/gurukul/run-container.sh
set -euo pipefail

ENV_FILE="${ENV_FILE:-/etc/gurukul/backend.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

: "${GURUKUL_IMAGE:?Set GURUKUL_IMAGE in $ENV_FILE}"
: "${AWS_REGION:?Set AWS_REGION in $ENV_FILE}"

login_ecr() {
  local registry
  registry="$(echo "$GURUKUL_IMAGE" | cut -d/ -f1)"
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$registry"
}

pull_and_restart() {
  login_ecr
  docker pull "$GURUKUL_IMAGE"
  docker rm -f gurukul-backend 2>/dev/null || true
  docker run -d --name gurukul-backend \
    --network host \
    --env-file "$ENV_FILE" \
    --restart unless-stopped \
    "$GURUKUL_IMAGE"
  echo "Running $GURUKUL_IMAGE"
  sleep 5
  curl -sf "http://localhost:${PORT:-8080}/actuator/health" || {
    echo "Health check failed — docker logs:" >&2
    docker logs gurukul-backend --tail 80
    exit 1
  }
}

case "${1:-pull-and-restart}" in
  pull-and-restart) pull_and_restart ;;
  *)
    echo "Usage: $0 pull-and-restart" >&2
    exit 1
    ;;
esac
