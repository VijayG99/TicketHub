# ST-15 - Keycloak (OAuth2 / OIDC)

Keycloak is **optional**. The default stack keeps the gateway's development token endpoint; with Keycloak
enabled the gateway accepts **both** (Keycloak RS256 tokens and dev HS256 tokens), so nothing breaks.

## Start
1. `.env`: `KEYCLOAK_ENABLED=true`
2. `docker compose --profile iam up -d`   (first start imports the realm; allow ~1-2 minutes)
3. Admin console: http://localhost:8180 (login from `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`, default `admin` / `admin12345`; change them in `.env`).

## What is imported (`keycloak/realm/tickethub-realm.json`)
* Realm `tickethub`, realm roles `ADMIN` and `CUSTOMER`.
* Public client `tickethub-web` (password grant enabled for local testing and scripts).
* Test users (development only): `admin / Admin@12345` (ADMIN), `alice / Alice@12345`, `bob / Bob@12345`, `john / John@12345` (CUSTOMER).
  Passwords are in the repository on purpose: this realm is for learning. Never import it into a real environment.

## Get a token and call the API
```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/tickethub/protocol/openid-connect/token \
  -d grant_type=password -d client_id=tickethub-web -d username=alice -d password='Alice@12345' | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/bookings/<id>
```
Run the whole flow with Keycloak logins: `AUTH_MODE=keycloak bash scripts/e2e.sh`

## How the gateway validates it
`KeycloakTokenVerifier` downloads Keycloak's public keys (JWKS) from
`http://keycloak:8080/realms/tickethub/protocol/openid-connect/certs`, checks the RS256 signature, expiry and
the `iss` claim (`http://localhost:8180/realms/tickethub` - the address clients used), and maps
`realm_access.roles` (`ADMIN`/`CUSTOMER`) to the `X-User-Roles` header. The user id is the Keycloak `sub`.
The business services are unchanged: they still trust only the gateway-forwarded identity.

## Troubleshooting
* `401 Invalid or expired token`: is `KEYCLOAK_ENABLED=true` and was the gateway recreated
  (`docker compose up -d apigate-service`)? Did you request the token from `http://localhost:8180`
  (the issuer must match `KEYCLOAK_ISSUER`)?
* Keycloak not answering yet: `docker compose logs keycloak` until it prints "Listening on".
* To turn the dev token endpoint off: `DEV_TOKEN_ENABLED=false`.
