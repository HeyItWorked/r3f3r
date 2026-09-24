# r3f3r

sorry this is just a simple project, still learning spring lol

Track specialist referrals and see which ones need a follow-up.

TODO: will add usage for the new stuff (history, providers, print etc) later, for now just run it and see

A single page: one add form, one table, one status dropdown per row. Overdue is computed on the backend and never stored. Built with Vue 3 + Vite and Java 21 Spring Boot, backed by Oracle Database Free.

![Request path](docs/diagrams/architecture.svg)

One call path: native `fetch` from the browser, through a Vite proxy, to Spring. Three endpoints — list, create, patch status — and a status machine the user can reverse any time.

![Status states](docs/diagrams/state.svg)
![API sequence](docs/diagrams/sequence.svg)
![Referral record](docs/diagrams/data-model.svg)

## Quick start

- **Prerequisites:** Java 21+ with `JAVA_HOME`, Node.js 22.18+ / npm 11+, and Docker (only for optional Oracle)
- **Backend:** `cd backend && ./mvnw spring-boot:run`
- **Frontend:** `cd frontend && npm install && npm run dev`
- **Open:** `http://localhost:5173`

The Vite dev server proxies `/api` to the backend, so the browser always talks to `http://localhost:5173`. With no Oracle environment variables set, the backend uses a local H2 file at `backend/data/` and starts immediately — point it at Oracle to persist for real (see [Database setup](#database-setup)).

## What's inside

- **`frontend/`** — Vue 3 + Vite, plain JavaScript and CSS (no Router, Pinia, or component library).
- **`backend/`** — Spring Boot REST API (`GET/POST/PATCH /api/referrals`), Spring Data JPA, and a portable `schema.sql`.
- **`docs/`** — the roadmap and diagrams that this README references.

## How it works

- Status flows `NEW → SENT → DONE` and is freely reversible; saving the unchanged status is a harmless no-op.
- A referral is **overdue** when its follow-up date is before today and its status is not `DONE`. Today is not overdue. Returning a past-due `DONE` referral to `SENT` makes it overdue again.
- The backend calculates overdue on read; it is never stored as a column.
- The loaded list can be explored entirely client-side — no extra API calls: a **Find** field that matches patient reference or specialist office, an **Overdue only** toggle, and sortable **Patient Ref**, **Specialist Office**, **Follow-up Date**, and **Status** headers (dates sort by their ISO values; the default order is the server's newest-first). The status bar shows "X of Y referrals" while a filter is active. If filtering would hide the selected row with an unsaved status change, the app asks before discarding it.
- Bad input returns `400` with a `fieldErrors` object; an unknown referral id returns `404`; unexpected failures return a generic `500` with no database details.

## Database setup

This app reads its datasource from environment variables, so it can run on H2 locally and Oracle in production with no code changes.

| Variable | Default | Meaning |
| --- | --- | --- |
| `REFERREE_DB_URL` | `jdbc:h2:file:./data/r3f3r;DB_CLOSE_DELAY=-1;MODE=Oracle` | JDBC URL |
| `REFERREE_DB_USER` | `sa` | Database user |
| `REFERREE_DB_PASSWORD` | *(empty)* | Database password |
| `REFERREE_DB_DRIVER` | `org.h2.Driver` | Driver class |
| `REFERREE_DB_DIALECT` | `org.hibernate.dialect.H2Dialect` | Hibernate dialect |

### Oracle Database Free (recommended)

Use the community `gvenzl/oracle-free` image, which ships a native `arm64` build and needs no Oracle account. On Apple Silicon, avoid `oracle-xe:21-full` — it is `amd64` and runs under Rosetta, which cannot start its background processes.

Oracle Free needs at least ~2 GB of RAM. Make sure Docker (or Colima) has at least 2–4 GB allocated to it:

```bash
colima start --memory 4   # if using Colima
docker info --format '{{.MemTotal}}'   # confirm >= 2 GiB
```

```bash
docker run -d --name oracle-free \
  -p 1521:1521 \
  --shm-size=2g \
  -e ORACLE_PASSWORD=Oracle_password1 \
  -e APP_USER=referree \
  -e APP_USER_PASSWORD=referree_pw \
  gvenzl/oracle-free:latest
```

Wait for `Pluggable database FREEPDB1 opened read write`, then:

```bash
cd backend
REFERREE_DB_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1 \
REFERREE_DB_USER=referree \
REFERREE_DB_PASSWORD=referree_pw \
REFERREE_DB_DRIVER=oracle.jdbc.OracleDriver \
REFERREE_DB_DIALECT=org.hibernate.dialect.OracleDialect \
./mvnw spring-boot:run
```

If your container reports a different service name than `FREEPDB1`, use that name in the JDBC URL instead.

### H2 fallback

With no environment variables set, the backend uses a local H2 database stored at `backend/data/`. It persists across restarts; delete the `backend/data/` folder to start fresh. This is for local development only — use Oracle for anything real.

## Demo walkthrough

1. Start the backend and frontend as above, then open `http://localhost:5173`.
2. In the **Add a referral** form, enter patient reference `DEMO-101`, specialist office `Cardiology West`, and a follow-up date in the past.
3. Click **Add**. The row appears with an **Overdue** label, because the follow-up date is in the past and the status is New.
4. Open the status dropdown, choose **Done**, and click **Save**. The label switches to **Current** — a Done referral is never overdue.
5. Open the dropdown again, choose **Sent**, and click **Save**. The **Overdue** label returns, because a past-due Sent referral is overdue again.

Try submitting an empty form: a message appears beside each field and the values you typed are preserved.

## Production build

`cd frontend && npm run build` outputs static files to `frontend/dist/`. The Vite
dev proxy (`/api` → `http://localhost:8080`) only exists during `npm run dev`, so
the built app must reach the backend another way:

- **Same origin (recommended):** serve `dist/` from any static host and reverse-proxy
  `/api/*` to the Spring Boot backend, e.g. nginx
  `location /api/ { proxy_pass http://localhost:8080; }`. No CORS changes needed.
- **Different origin:** inline the backend URL at build time:
  `VITE_API_BASE_URL=https://api.example.com/api npm run build`. The backend has no
  CORS configuration, so cross-origin browser requests will be blocked unless you add
  CORS headers — the same-origin proxy avoids that.

This build has no authentication or permissions and is for fictional data only — do
not point it at real patient records.

## Testing

tests should pass i think

```bash
cd backend
./mvnw test
```

Tests run against H2 in Oracle mode: the referral REST API (create returns 201; list, status change, 404 on unknown id, 400 on invalid input) and the overdue/status rules (today is not overdue; a past outstanding referral is overdue; Done is never overdue; Done returned to Sent is overdue again).

## Boundaries

- One simulated coordinator — no accounts or permissions.
- Specialist office is free text — no provider directory or separate provider table.
- Find/sort are client-side over the loaded list — there is no server-side search, saved views, export, or pagination.
- No detail page or dashboard.
- No history, notes, priority, cancellation, deletion, or editing the original referral fields.
- **Fictional data only.** Do not enter patient names, dates of birth, diagnoses, or clinical notes.

This is a local learning application, not software for clinical use. There is no login, messaging, attachments, or integration with healthcare systems.

## Stopping the services

- Backend (`spring-boot:run`): press `Ctrl+C` in its terminal.
- Frontend (`npm run dev`): press `Ctrl+C` in its terminal.
- Oracle (Docker): `docker stop -f oracle-free`.
- Colima: `colima stop` to shut down the whole Docker VM.

When the backend stops, the H2 fallback database at `backend/data/` is left in place so the next start continues from it; delete that folder to reset.
