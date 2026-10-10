# Local MQ test environment

An IBM MQ developer queue manager (`QM1`) in Docker, with plain and TLS channels and some test queues. For local
testing only: it uses throw-away passwords and self-signed certificates that are generated on your machine.

**Licence:** `docker-compose.yml` sets `LICENSE: accept` for the `icr.io/ibm-messaging/mq` developer image. By running
it you accept the licence terms IBM publishes with that image, so read them first.

**Needs:** Docker with Compose, PowerShell, and a JDK on the `PATH` or in `JAVA_HOME` (for `keytool`).

## First-time setup

```powershell
cd docker
.\certs\generate-certs.ps1        # test certificates and MessagePigeon key/trust stores
.\setup-env.ps1                   # creates .env with random passwords for the MQ users
docker compose up -d
docker compose logs -f mq         # wait for "Started queue manager"
```

## Connection settings for MessagePigeon

| | Plain | TLS |
|---|---|---|
| Host / Port | localhost / 1414 | localhost / 1414 |
| Queue manager | QM1 | QM1 |
| Channel | `PIGEON.PLAIN.SVRCONN` | `PIGEON.TLS.SVRCONN` |
| User | `app` | `app` |
| Password | `MQ_APP_PASSWORD` in `.env` | same |
| TLS | off | on, TLSv1.2 or TLSv1.3 |
| Trust store | | `certs\truststore.p12` or `.jks` |
| Key store (optional, mutual TLS) | | `certs\client.p12` or `.jks` |
| Store passwords | | contents of `certs\store-password.txt` |

`DEV.APP.SVRCONN` also exists but is TLS-only (set by the image).

Queues: `DEV.QUEUE.1`, `DEV.QUEUE.2`, `DEV.QUEUE.3`, `PIGEON.TEST.QUEUE.1`, `PIGEON.TEST.QUEUE.2`.
Web console: https://localhost:9443/ibmmq/console (user `admin`, password `MQ_ADMIN_PASSWORD`).

For TLS 1.2 try cipher `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`; for 1.3 `TLS_AES_256_GCM_SHA384`.

`docker compose down -v` throws away queue contents and starts fresh.
