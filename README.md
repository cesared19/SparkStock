# SparkStock

Device stock for the SparkyTC suite: VR headsets lent to testers, laptops and monitors assigned to people,
and everything in between. People sign in with their **SparkyTC account**, and SparkyTC can ask SparkStock which
devices a tester has (to record the device a test ran on).

## Features

- **Devices by type.** Each type (VR headset, Laptop, Monitor, Peripheral, or your own) has its own extra fields,
  e.g. *System version*, *Controller serials*, *MDM* and *Battery health* for VR headsets. Admins add types and
  fields in **Device types**. Each device gets the next asset tag of its type (`VR-0001`, `LAP-0001`...).
- **Lent or assigned.** *Lent* types (VR headsets) are checked out by anyone who needs them, for 7 days by default,
  and returned by the borrower or an admin. *Assigned* types (laptops) are given to a person by an admin.
- **Lifecycle.** Available, on loan, assigned, in repair, lost, retired. Sending a device on loan to repair ends the
  loan; every change is kept in the device's history, with who did it and a note.
- **Dashboard.** Your devices, every overdue loan and warranties ending within 30 days.
- **QR labels.** Print a PDF of labels on A4 or Letter adhesive sheets (Avery L7160, L7159 or 5160 layouts), skipping
  the labels already used. A label holds the device's address (`/t/VR-0001`), so the phone's own camera opens it, no
  app or HTTPS needed.
- **Import from a spreadsheet** (admins, **Import**): a CSV such as the Confluence database exported with
  *••• → Export → CSV*. Columns are matched by name and can be changed; a preview shows what each row will do before
  anything is saved, and the import is all or nothing. Rows are matched to devices by ID, then serial number, so
  importing the same file again updates instead of duplicating. Unknown types are created as lent types with
  *Firmware / OS* and *Accessories* fields, and `N/A` cells count as empty. Status and holders are never changed.
- **Project** per device (the SparkyTC project short code, e.g. `VR`), for the coverage matrix later.
- **Roles from SparkyTC.** SparkyTC admins manage devices, types and locations; testers borrow and return.
- Light and dark theme (follows the system), and phone, tablet and desktop layouts.

## Architecture

| Part | Stack | Where |
| --- | --- | --- |
| Backend | Spring Boot 3, Kotlin, JDK 21, Flyway | `service/` |
| Client | Remix, React, Tailwind | `client/` |
| Database | PostgreSQL 16, its own database (`sparkstock`) | the same server as SparkyTC's |

### Signing in with SparkyTC

SparkStock has no passwords of its own. The login form sends the username and password to SparkyTC's
`/api/v1/auth/login` once (through SparkStock's backend) and keeps the SparkyTC session token it gets back, in
SparkStock's own cookie (`__sparkstock`, not SparkyTC's `__session`: cookies are shared by every port of a host).
Each request is checked against SparkyTC's `/api/v1/auth/me` again every 5 minutes
(`SPARKYTC_SESSION_CACHE_SECONDS`), so an account disabled or demoted in SparkyTC stops working here too. Sessions
last as long as the SparkyTC session (7 days). Admins' *Assign* list comes from SparkyTC's user list.

### Integration API (for SparkyTC)

Read only, with the shared secret `STOCK_INTEGRATION_TOKEN` as `Authorization: Bearer <token>` (off while empty).
The backend is published only on `127.0.0.1`; SparkyTC reaches it on the shared docker network as
`http://stock-backend:8080`.

- `GET /api/integration/v1/devices?holderUserID=<SparkyTC user ID>&usage=POOL&q=` – the devices a tester has.
- `GET /api/integration/v1/devices/{ref}` – one device by asset tag.

Each device comes as `{ ref, id, name, manufacturer, model, type, usage, version, status, holderUserID, holderName, dueAt }`.
`ref` is the asset tag and `version` the type's coverage field (the headset's system version).

## Deploying (next to SparkyTC)

1. Create the database on SparkyTC's PostgreSQL server:

   ```sql
   CREATE USER sparkstock WITH PASSWORD '...';
   CREATE DATABASE sparkstock OWNER sparkstock;
   ```

2. Find SparkyTC's docker network with `docker network ls` (it ends in `_app-network`).
3. `cp .env.example .env` and fill it in (`SESSION_SECRET` and `STOCK_INTEGRATION_TOKEN`: `openssl rand -hex 32`).
4. `docker compose up -d --build`. SparkStock opens on port 3100. Sign in with a SparkyTC admin account.

Each `up` backs up the database before the backend migrates it; daily backups go to `BACKUP_DIR` (14 daily,
8 weekly). To restore, the scripts in `deploy/backup` work like SparkyTC's:

```bash
docker compose stop stock-backend
docker compose run --rm stock-database-backup restore daily/sparkstock-YYYYMMDD-HHMMSS.dump --replace
docker compose start stock-backend
```

## Resources

SparkStock is light: measured with the backend capped at a 256 MB heap (`STOCK_JAVA_OPTIONS`), the backend uses about
300 MB of RAM and the client about 100 MB, with no CPU while idle (there are no background jobs besides the nightly
`pg_dump`, a few MB). It answered 500 device-list requests in 2 seconds. It calls SparkyTC once per signed-in person
every 5 minutes at most (to check the session), and SparkyTC will call it only when someone records a result.

## Developing

```bash
cd service && ./gradlew test        # integration tests: Testcontainers (needs Docker) and a fake SparkyTC
cd client && npx tsc && npm run lint && npm run build
```

Run the backend against a local SparkyTC with `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` and
`SPARKYTC_API_URL=http://localhost:8080` (`--server.port=8081`), and the client with
`API_URL=http://localhost:8081 PORT=3100 npm run dev`.
