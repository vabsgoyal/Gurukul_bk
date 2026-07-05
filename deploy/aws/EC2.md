# Deploy Gurukul Backend on EC2

Run the Spring Boot API on a single **EC2** instance in **eu-north-1**, connected to your existing Aurora cluster via **IAM auth** (no DB password).

Repo: https://github.com/vabsgoyal/Gurukul_bk

```text
Internet → EC2 (Docker, port 8080)
              ↓ IAM (instance role)
         Aurora PostgreSQL (gurukul)
```

---

## Prerequisites

| Item | Status |
|------|--------|
| Aurora cluster `gurukul` (eu-north-1) | ✅ you have this |
| Flyway v1 applied | ✅ |
| AWS CLI / Console access (admin) | required for first setup |
| GitHub repo access | public clone OK |

---

## Step 1 — Create IAM role for EC2

The EC2 instance needs `rds-db:connect` (same as your Mac `gurukul-deploy` policy).

**CloudShell or admin terminal:**

```bash
# Trust policy — EC2 can assume this role
cat > /tmp/gurukul-ec2-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "ec2.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
EOF

aws iam create-role \
  --role-name GurukulEc2Role \
  --assume-role-policy-document file:///tmp/gurukul-ec2-trust.json

aws iam put-role-policy \
  --role-name GurukulEc2Role \
  --policy-name RdsIamConnect \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Action": "rds-db:connect",
      "Resource": "arn:aws:rds-db:eu-north-1:916169432799:dbuser:cluster-LBYY3IYQCMKKMJ6IFW54KBOR6A/postgres"
    }]
  }'

aws iam create-instance-profile --instance-profile-name GurukulEc2Profile

aws iam add-role-to-instance-profile \
  --instance-profile-name GurukulEc2Profile \
  --role-name GurukulEc2Role
```

---

## Step 2 — Launch EC2 instance

### Console

1. **EC2 → Launch instance**
2. **Name:** `gurukul-backend`
3. **AMI:** Amazon Linux 2023
4. **Instance type:** `t3.small` (2 vCPU, 2 GB — enough for Docker + Java)
5. **Key pair:** create or select one (SSH access)
6. **Network:** default VPC, **public subnet**, **Auto-assign public IP: Enable**
7. **Security group:** create `gurukul-ec2-sg`
   - Inbound **8080** from your IP (or `0.0.0.0/0` for public API — lock down later)
   - Inbound **22** from your IP only (SSH)
   - Outbound: all (default)
8. **IAM instance profile:** `GurukulEc2Profile`
9. **Advanced → Metadata:** set **IMDS hop limit = 2** (allows Docker container to use instance IAM role)
10. Launch

### CLI

```bash
aws ec2 run-instances \
  --image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --instance-type t3.small \
  --key-name YOUR_KEY_PAIR \
  --iam-instance-profile Name=GurukulEc2Profile \
  --metadata-options "HttpTokens=required,HttpPutResponseHopLimit=2,HttpEndpoint=enabled" \
  --security-group-ids sg-XXXXXXXX \
  --subnet-id subnet-XXXXXXXX \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=gurukul-backend}]' \
  --region eu-north-1
```

Note the **public IP** or attach an **Elastic IP** for a stable URL.

---

## Step 3 — SSH into the instance

```bash
ssh -i your-key.pem ec2-user@EC2_PUBLIC_IP
```

---

## Step 4 — Bootstrap (install Docker + run app)

On the EC2 instance, run the bootstrap script from the repo:

```bash
curl -fsSL https://raw.githubusercontent.com/vabsgoyal/Gurukul_bk/main/deploy/aws/ec2-bootstrap.sh | bash
```

Or clone and run manually:

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# log out and back in for docker group

git clone https://github.com/vabsgoyal/Gurukul_bk.git
cd Gurukul_bk
sudo cp deploy/aws/gurukul-backend.env.example /etc/gurukul/backend.env
# edit /etc/gurukul/backend.env if needed
sudo mkdir -p /etc/gurukul
sudo cp deploy/aws/gurukul-backend.env.example /etc/gurukul/backend.env

docker build -t gurukul-backend:latest .
sudo cp deploy/aws/gurukul-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now gurukul-backend
```

---

## Step 5 — Verify

From your laptop:

```bash
curl http://EC2_PUBLIC_IP:8080/actuator/health

curl http://EC2_PUBLIC_IP:8080/api/v1/schools/11111111-1111-1111-1111-111111111111
```

On the instance:

```bash
sudo systemctl status gurukul-backend
sudo docker logs gurukul-backend
```

---

## Environment variables

File: `/etc/gurukul/backend.env`

| Variable | Value |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `AWS_REGION` | `eu-north-1` |
| `SPRING_DATASOURCE_URL` | `jdbc:aws-wrapper:postgresql://gurukul.cluster-cro4soma8nh2.eu-north-1.rds.amazonaws.com:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |

No password. Credentials come from the **EC2 instance role** via the AWS SDK inside the container.

---

## Deploy updates (new code from GitHub)

SSH to EC2:

```bash
cd ~/Gurukul_bk
git pull origin main
docker build -t gurukul-backend:latest .
sudo systemctl restart gurukul-backend
curl -s localhost:8080/actuator/health
```

---

## Optional — HTTPS with Nginx + domain

1. Point DNS `api.yourdomain.com` → EC2 Elastic IP
2. Install nginx + certbot on EC2
3. Proxy `443 → localhost:8080`
4. Security group: open **443**, close public **8080**

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Unable to load AWS credentials` | EC2 has no instance profile, or IMDS hop limit is 1 (set to **2**) |
| `PAM authentication failed` | Wrong auth mode — must use IAM URL (`jdbc:aws-wrapper:...`), not password |
| App starts but health fails | Wait ~90s (Aurora + Flyway); check `docker logs gurukul-backend` |
| Connection timeout to Aurora | Security group / network — IAG cluster should allow internet egress from EC2 |
| Port 8080 refused | `sudo systemctl status gurukul-backend`; check security group inbound |

---

## Cost (approx, eu-north-1)

| Resource | ~$/month |
|----------|----------|
| `t3.small` EC2 | ~$15 |
| Elastic IP (attached) | free |
| Aurora (existing) | separate |

Cheaper dev option: `t3.micro` (~$8) if startup time is acceptable.

---

## Security checklist (before real users)

- [ ] Restrict SG port 8080 to known IPs or put nginx + HTTPS in front
- [ ] Remove SSH `0.0.0.0/0` — your IP only
- [ ] Add JWT auth (currently `permitAll`)
- [ ] Use Elastic IP + Route 53 for stable DNS

See also: [PIPELINE.md](./PIPELINE.md) · [readme_run.md](../../readme_run.md) · [DEPLOYMENT.md](./DEPLOYMENT.md)
