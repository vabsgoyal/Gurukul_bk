#!/usr/bin/env bash
# Run on a fresh Amazon Linux 2023 EC2 instance (as ec2-user).
# Prerequisites: IAM instance profile with rds-db:connect, IMDS hop limit = 2
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/vabsgoyal/Gurukul_bk.git}"
APP_DIR="${APP_DIR:-/home/ec2-user/Gurukul_bk}"
ENV_FILE="/etc/gurukul/backend.env"

echo "==> Installing Docker and Git..."
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

echo "==> Cloning repository..."
if [[ -d "$APP_DIR/.git" ]]; then
  git -C "$APP_DIR" pull origin main
else
  git clone "$REPO_URL" "$APP_DIR"
fi

echo "==> Installing run script and environment..."
sudo mkdir -p /etc/gurukul /opt/gurukul
sudo cp "$APP_DIR/deploy/aws/run-container.sh" /opt/gurukul/
sudo chmod +x /opt/gurukul/run-container.sh
if [[ ! -f "$ENV_FILE" ]]; then
  sudo cp "$APP_DIR/deploy/aws/gurukul-backend.env.example" "$ENV_FILE"
  echo "    Created $ENV_FILE — set GURUKUL_IMAGE to your ECR URI after first CI deploy."
else
  echo "    $ENV_FILE already exists — leaving unchanged."
fi

echo "==> Building Docker image (initial local tag; CI will push ECR later)..."
docker build -t gurukul-backend:latest "$APP_DIR"

echo "==> Installing systemd service..."
sudo cp "$APP_DIR/deploy/aws/gurukul-backend.service" /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable gurukul-backend
sudo systemctl restart gurukul-backend

echo "==> Waiting for health (up to 120s)..."
for i in $(seq 1 24); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "==> Gurukul backend is UP"
    curl -s http://localhost:8080/actuator/health
    exit 0
  fi
  sleep 5
done

echo "==> Health check timed out — check logs:"
echo "    sudo journalctl -u gurukul-backend -n 50 --no-pager"
echo "    sudo docker logs gurukul-backend"
exit 1
