#!/usr/bin/env bash
# Prepares a fresh Amazon Linux 2023 (x86_64) instance to run the Gurukul backend JAR and the WA-AKG
# WhatsApp gateway behind nginx - the setup production runs today. Run as ec2-user from a checkout of
# this repo (only deploy/aws/ is used). Idempotent: safe to run again.
#
# It installs software and service definitions only. It does NOT start the backend or the gateway:
# two gateways on one WhatsApp session, or two backends running the same scheduled jobs against the
# shared database, cause real problems - start them only at cutover (see MIGRATION.md).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "==> Swap (2 GB): headroom for spikes on small instances"
if ! swapon --show | grep -q /swapfile; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo "/swapfile none swap defaults 0 0" | sudo tee -a /etc/fstab >/dev/null
fi
echo "vm.swappiness=10" | sudo tee /etc/sysctl.d/90-swappiness.conf >/dev/null && sudo sysctl -q -p /etc/sysctl.d/90-swappiness.conf
sudo timedatectl set-timezone UTC

echo "==> Packages: Java 25 (Corretto), nginx, PostgreSQL 17 client, Node 22 (NodeSource)"
sudo dnf -y -q install java-25-amazon-corretto-headless nginx postgresql17 python3-pip augeas-libs rsync
if ! node -v 2>/dev/null | grep -q '^v22'; then
  curl -fsSL https://rpm.nodesource.com/setup_22.x | sudo bash - >/dev/null
  sudo dnf -y -q install nodejs
fi

echo "==> certbot (Let's Encrypt) in its own venv"
[ -x /opt/certbot/bin/certbot ] || sudo python3 -m venv /opt/certbot
sudo /opt/certbot/bin/pip install -q --upgrade pip certbot certbot-nginx
sudo ln -sf /opt/certbot/bin/certbot /usr/bin/certbot

echo "==> Users and directories"
id gurukul >/dev/null 2>&1 || sudo useradd --system --home-dir /opt/whatsapp-gateway --shell /sbin/nologin gurukul
sudo mkdir -p /opt/gurukul /etc/gurukul && sudo chown ec2-user:ec2-user /opt/gurukul
sudo install -o root -g root -m 755 "$HERE/deploy-from-s3.sh" /opt/gurukul/deploy-from-s3.sh

echo "==> Service definitions (installed, not started)"
for unit in gurukul-backend.service wa-akg.service certbot-renew.service certbot-renew.timer; do
  sudo install -o root -g root -m 644 "$HERE/$unit" /etc/systemd/system/$unit
done
sudo systemctl daemon-reload
sudo systemctl enable --now nginx >/dev/null 2>&1

cat <<'NEXT'

Done. Still to copy from the current server before cutover (see MIGRATION.md):
  /etc/gurukul/backend.env           (then set AWS_REGION, APP_ANTHROPIC_MODEL, APP_CHAT_ATTACHMENTS_BUCKET)
  /opt/gurukul/gurukul-backend.jar   (or let the first deploy fetch it)
  /opt/whatsapp-gateway              (app, .env, node_modules, built .next)
  /etc/letsencrypt, /etc/nginx/nginx.conf, /etc/nginx/conf.d/*.conf
NEXT
