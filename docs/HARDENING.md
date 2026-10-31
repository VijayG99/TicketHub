# ST-78: hardening S3 and RDS operations

What is **built in the code/config**, and what is **an AWS console/Terraform step** (documented, not automated here).

## S3 (posters)
| Control | Status | Where |
|---|---|---|
| Server-side encryption at rest (SSE-S3, AES-256) | built, **on in the `aws` profile** | `tickethub.media.server-side-encryption`, `MediaStorageService` |
| Cache headers (immutable posters, 1 year) | built | `tickethub.media.cache-control` |
| Type + size validation before upload; remote upload never inside a DB transaction | built (Phase 1) | `MediaStorageService`, `PosterService` |
| Versioning + 30-day expiry of old versions | built for the local MinIO bucket (best effort) | `minio-init` in `docker-compose.yml` |
| Least-privilege access: the service role may only `s3:PutObject` on `posters/*` | **AWS step** | IAM policy below |
| Block all public access, serve through CloudFront (Origin Access Control) | **AWS step**: the local bucket is public-read for learning only | bucket settings |
| TLS-only bucket policy | **AWS step** | policy below |
| Lifecycle: expire non-current versions, abort incomplete multipart uploads | **AWS step** (same idea as the MinIO rule) | bucket lifecycle |

IAM policy for the event-service task role:
```json
{ "Version": "2012-10-17", "Statement": [
  { "Effect": "Allow", "Action": ["s3:PutObject"], "Resource": "arn:aws:s3:::tickethub-media/posters/*" } ] }
```
TLS-only bucket policy statement: `{"Effect":"Deny","Principal":"*","Action":"s3:*","Resource":["arn:aws:s3:::tickethub-media","arn:aws:s3:::tickethub-media/*"],"Condition":{"Bool":{"aws:SecureTransport":"false"}}}`.

## RDS (databases)
| Control | Status | Where |
|---|---|---|
| TLS to the database (`sslmode=require`) | built in the `aws` profile files | `*-service-aws.yml` |
| Password from Secrets Manager, never in Git | built (Phase 1) | [AWS_RDS_AND_SECRETS.md](AWS_RDS_AND_SECRETS.md) |
| Bounded connection pools, short connect timeout, leak detection | built | `spring.datasource.hikari.*` in `config-repo/application.yml` |
| Backup + **tested restore** | local drill built; AWS steps documented | `scripts/db-restore-drill.sh` |
| Multi-AZ, automated backups (7-35 days), point-in-time recovery, deletion protection | **AWS step** | RDS instance settings |
| Force TLS on the server (`rds.force_ssl=1`) | **AWS step** | parameter group |
| One DB user per service, no superuser, `public` schema rights revoked | **AWS step** | `CREATE USER ...; REVOKE ALL ...` |
| Password rotation | **AWS step** (Secrets Manager rotation); services must be restarted/pool refreshed | |
| Alerts: CPU, free storage, connections, replica lag | **AWS step** (CloudWatch) | |

Sizing rule used for the pool: `maximum-pool-size (10) x instances x services` must stay below the RDS `max_connections`.

## The restore drill (do it, do not just read it)
Local: `bash scripts/db-restore-drill.sh` (dump, restore into a scratch DB, compare every table's row count).
AWS: restore the latest snapshot or a point in time to a **new** instance, point a copy of one service at it, run `scripts/smoke-test.sh`, then delete the instance. Write down how long it took: that is your real recovery time.
