# الهندسية المتحدة — API

Express + PostgreSQL backend for the maintenance center at الحدايق. The Android app and customer website use the same API.

## Local setup

1. Run `npm ci` and copy `.env.example` to `.env`.
2. Set `DATABASE_URL` to a local development database and a random `JWT_SECRET`.
3. Run `npm run migrate` to apply additive schema changes.
4. On a **fresh empty database only**, set `MANAGER_NAME`, `MANAGER_EMAIL` or `MANAGER_PHONE`, and `MANAGER_PASSWORD` (12–72 bytes), then run `npm run bootstrap`.
5. Run `npm start`. Sign in with your own manager account and add your employees and inventory from the app.

The bootstrap creates one real manager account. It never inserts customers, repair orders, stock, or sample invoices, and refuses to change an existing database. `npm run seed` is retained as an alias for the same safe bootstrap. There is no HTTP reset/seed endpoint.

## Access and workflows

- `/api/auth/login`, `/api/auth/me`, `/api/auth/change-password`
- Manager/reception: customers, devices, order intake, scheduling, quote approval and rejection, order details, invoices.
- Technician: assigned jobs, start/pause, checklist, photos, spare parts, price quote, invoice and signature.
- Manager: employees, stock, reports and CSV, `/api/technicians/:id/itinerary?date=YYYY-MM-DD`, `/api/technicians/:id/history?limit=30&offset=0`.
- Public: `/api/public/requests` and tracking by order code **and** customer phone.

New records always belong to الحدايق. Historical data is preserved. Job changes record actor and time in the order activity log. Itinerary shows scheduled visits and their addresses/status; it is not live GPS tracking.

Password changes and resets invalidate older sessions; the app keeps the new token after a user changes their own password. Role changes take effect immediately. Closed invoices preserve their original parts totals.

## Verification

CI uses an isolated database whose name ends in `_test`, `NODE_ENV=test`, and `test/fixtures.js`. This fixture loader is guarded and cannot run against an operational database. Run `npm test` with `TEST_BASE_URL` pointing to the locally running test API after loading fixtures. Never load test fixtures into a real store.

Existing operational demo records are **not deleted automatically** by a deployment. Identify and back up those records before any separately reviewed cleanup. No cleanup runs during migration or startup.
