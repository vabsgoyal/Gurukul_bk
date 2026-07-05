#!/usr/bin/env bash
# EC2 deploy script — install to /opt/gurukul/deploy-from-s3.sh
# Pulls latest JAR from S3, restarts systemd service, checks health.
set -euo pipefail

S3_URI="${S3_URI:-s3://gurukul-deploys-916169432799/releases/gurukul-backend.jar}"
JAR_PATH="${JAR_PATH:-/opt/gurukul/gurukul-backend.jar}"
AWS_REGION="${AWS_REGION:-eu-north-1}"
PORT="${PORT:-8080}"

aws s3 cp "$S3_URI" "$JAR_PATH" --region "$AWS_REGION"
sudo systemctl restart gurukul-backend
sleep 25
curl -sf "http://localhost:${PORT}/actuator/health"
