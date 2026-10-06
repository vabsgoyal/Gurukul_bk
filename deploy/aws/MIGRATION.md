# Moving production to another server, Region or AWS account

Used for the October 2026 move from account 916169432799 (Mumbai) to account 485157611101 (Sydney),
and meant to be reused for the planned move back to Mumbai. Supabase (database), GoDaddy (DNS),
WhatsApp, Expo and Google are outside AWS, so only the server, the two buckets and the IAM pieces move.

## What production consists of

| Piece | Where | Notes |
|---|---|---|
| Server | EC2, Amazon Linux 2023 x86_64 | Backend JAR (`gurukul-backend`), WA-AKG gateway (`wa-akg`), nginx + Let's Encrypt |
| Server role `GurukulEc2Role` | IAM | SSM (deploys), read `releases/*` in the deploy bucket, read/write `chat-attachments/`, `school-logos/`, `profile-photos/`, `admissions/` in the uploads bucket, Claude on Bedrock |
| Deploy bucket | S3 | JARs uploaded by GitHub Actions - nothing to copy, the next deploy uploads a fresh one |
| Uploads bucket | S3 | User files - copy the objects listed in the DB (`message.attachment_object_key`, `school.logo_object_key`, `id_card_profile.photo_object_key`, `admission_document.object_key`) |
| Deploy user `gurukul-deploy` | IAM | `s3:PutObject` on `releases/*`, `ssm:SendCommand` on the instance, read command results |
| DNS | GoDaddy | `api.smartgurukul.org` and `wa-akg.smartgurukul.org` A records |

## Build the new server (no downtime)

1. Launch the instance (20 GB gp3, the server role, a security group with 80/443 open and 22 for admins only) and attach an Elastic IP.
2. Run `deploy/aws/provision-server.sh` on it.
3. Copy from the current server, straight server-to-server (through a laptop it's far too slow): `/etc/gurukul/backend.env`, `/opt/gurukul/gurukul-backend.jar`, `/opt/whatsapp-gateway`, `/etc/letsencrypt`, `/etc/nginx/nginx.conf`, `/etc/nginx/conf.d/*.conf`. Extract onto disk, not `/tmp` - on Amazon Linux `/tmp` is RAM.
4. In `backend.env` set `AWS_REGION`, `APP_ANTHROPIC_MODEL` (Bedrock inference profile for that Region, e.g. `au.` / `apac.` / `eu.`), `APP_CHAT_ATTACHMENTS_BUCKET`.
5. Check: `nginx -t`, the certificate is served for `api.smartgurukul.org`, the role can reach both buckets, SSM shows the instance Online, and Bedrock answers from the server.

Do not start `gurukul-backend` or `wa-akg` yet.

## Cutover (~10-15 min of downtime)

Beforehand: lower the TTL of both A records to 600 s, and have a GitHub repo admin ready to update secrets.

1. Freeze merges to `main`.
2. Old server: `sudo systemctl disable --now wa-akg gurukul-backend`.
3. Copy the uploads objects to the new bucket.
4. New server: `sudo systemctl enable --now gurukul-backend wa-akg certbot-renew.timer`; wait for `/actuator/health` and the WhatsApp session to show CONNECTED.
5. GoDaddy: point both A records at the new Elastic IP.
6. GitHub secrets: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (new deploy user), `S3_BUCKET`, `EC2_INSTANCE_ID`. Merge the PR that updates the Regions and bucket names in `deploy.yml` / `deploy-from-s3.sh` - its deploy is the end-to-end test of the pipeline.
7. Verify: public health, an OTP login, a push, a chat upload, an AI chat reply.

Rollback (until users have written data only to the new setup): start the old services again and point DNS back.

## Afterwards

After a few quiet days: stop, then terminate the old instance; release its IP; empty and delete the old buckets; delete any temporary admin access keys used for the move.
