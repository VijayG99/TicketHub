# ST-31 - Amazon RDS and externalised secrets

This guide moves the four service databases from the local Postgres containers to Amazon RDS and
keeps every secret out of the repository. It describes what the project already supports and what you
do in AWS. **It was not executed here (no AWS account was available), so treat the AWS side as a checklist
to verify in your own account.**

## What the project already does
* No secret is committed. `.env` is git-ignored; the config repository contains no passwords.
* Every service reads its database password from `SPRING_DATASOURCE_PASSWORD`, the gateway reads
  `JWT_SECRET`, payment reads `PAYMENT_WEBHOOK_SECRET` (and `STRIPE_SECRET_KEY`).
* Profile files `config-repo/<service>-aws.yml` point each service at an RDS host with TLS
  (`sslmode=require`). Hosts come from `EVENT_DB_HOST`, `BOOKING_DB_HOST`, `PAYMENT_DB_HOST`,
  `NOTIFICATION_DB_HOST` on the config-server container.

## Steps in AWS
1. **Create four databases** (or four RDS instances, one per service to keep full ownership):
   `event_db`, `booking_db`, `payment_db`, `notification_db`, user `tickethub`, PostgreSQL 16.
   Put RDS in private subnets; allow port 5432 only from the security group of the ECS/EC2 hosts.
2. **Store secrets** in AWS Secrets Manager (or SSM Parameter Store, SecureString):
   `tickethub/db-password`, `tickethub/jwt-secret`, `tickethub/payment-webhook-secret`.
3. **Inject them as environment variables** at deploy time, never in the image:
   * ECS task definition: `secrets: [{ name: "SPRING_DATASOURCE_PASSWORD", valueFrom: "<secret ARN>" }]`
   * EC2 / plain Docker host: fetch with the AWS CLI into the shell before `docker compose up`:
     ```bash
     export POSTGRES_PASSWORD=$(aws secretsmanager get-secret-value --secret-id tickethub/db-password --query SecretString --output text)
     export JWT_SECRET=$(aws secretsmanager get-secret-value --secret-id tickethub/jwt-secret --query SecretString --output text)
     export PAYMENT_WEBHOOK_SECRET=$(aws secretsmanager get-secret-value --secret-id tickethub/payment-webhook-secret --query SecretString --output text)
     ```
4. **Point the stack at RDS**: in `.env` set `TICKETHUB_PROFILES=docker,aws`, and add the four
   `*_DB_HOST` variables to the `config-server` service `environment:` in a compose override file
   (`docker-compose.aws.yml`), for example:
   ```yaml
   services:
     config-server:
       environment:
         EVENT_DB_HOST: event.xxxxxx.ap-south-1.rds.amazonaws.com
         BOOKING_DB_HOST: booking.xxxxxx.ap-south-1.rds.amazonaws.com
         PAYMENT_DB_HOST: payment.xxxxxx.ap-south-1.rds.amazonaws.com
         NOTIFICATION_DB_HOST: notification.xxxxxx.ap-south-1.rds.amazonaws.com
   ```
   Start with `docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d`. The local
   `*-db` containers are then unused and can be stopped.
5. **Flyway** creates the tables on first start, exactly as it does locally.
6. **Media**: leave `MEDIA_S3_ACCESS_KEY` empty and set the event-service config
   `tickethub.media.endpoint` to empty, `bucket` to your S3 bucket, `region` to your region and
   `public-base-url` to the bucket/CDN URL; the task's IAM role then grants S3 access.

## Verify
* `docker compose logs event-service | grep -i flyway` shows migrations applied to the RDS host.
* `bash scripts/smoke-test.sh` and `bash scripts/e2e.sh` pass.
* `git grep -iE "password|secret"` finds no real value in tracked files.
