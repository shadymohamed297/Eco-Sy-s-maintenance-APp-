# Maintenance Center API

Backend for نظام مركز صيانة متكامل — a Cairo home-appliance maintenance center app
(reception / field technician / manager roles).

## Setup

```bash
npm install
cp .env.example .env   # edit DATABASE_URL / JWT_SECRET
createdb maintenance_center
npm run migrate
npm run seed
npm start               # http://localhost:3000
```

Seeded accounts (password `Passw0rd!` for all):

| role       | login                        |
|------------|-------------------------------|
| reception  | mona@markazsayana.eg          |
| manager    | khaled@markazsayana.eg        |
| technician | mahmoud@markazsayana.eg       |
| technician | amr@markazsayana.eg           |
| technician | mostafa@markazsayana.eg       |

## API summary

All endpoints except `/health` and `/api/auth/login` require
`Authorization: Bearer <token>` from `POST /api/auth/login`.

- `POST /api/auth/login` `{ identifier, password }`
- `GET /api/auth/me`
- `GET /api/dashboard/reception`
- `GET /api/dashboard/manager?period=month|quarter|year`
- `GET /api/technicians` — available technicians for assignment
- `GET /api/work-orders/customers?phone=` / `POST /api/work-orders/customers`
- `POST /api/work-orders/devices`
- `POST /api/work-orders` — create intake
- `PATCH /api/work-orders/:id/assign` `{ technicianId, scheduledAt }`
- `GET /api/work-orders/mine` — technician's today tasks
- `GET /api/work-orders/:id`
- `POST /api/work-orders/:id/start` / `/pause`
- `PATCH /api/work-orders/checklist-items/:itemId` `{ status, note }`
- `POST /api/work-orders/:id/parts` `{ name, inventoryItemId, status }`
- `GET /api/work-orders/:id/invoice-preview`
- `POST /api/work-orders/:id/close` `{ laborFee, warrantyDiscount, paymentMethod, signatureName, customerRating }`
- `GET /api/work-orders?status=&branch=&search=&limit=&offset=` — filterable list (all orders, not just today's)
- `GET /api/inventory?filter=all|low|pending`
- `POST /api/inventory/:id/purchase-order`
- `GET /api/customers?search=&limit=&offset=`
- `GET /api/customers/:id` — devices + full work-order history
- `GET /api/technicians/roster` (manager) — every technician with monthly stats
- `GET /api/technicians/me/performance` (technician) — self stats + recent closed orders
- `GET /api/reports?period=month|quarter|year` (manager) — totals + breakdown by technician/branch/device
- `GET /api/reports/export?period=` (manager) — CSV download of closed orders in that period

## Production notes

- **Startup guard**: refuses to boot with `NODE_ENV=production` unless a real `JWT_SECRET` is
  set (not the `.env.example` placeholder).
- **Crash safety**: `express-async-errors` is required before any routes so a rejected promise
  in an async handler returns a clean 500 instead of crashing the process; `db.js` also attaches
  a `pool.on('error', ...)` listener — without it, a background Postgres hiccup on an idle
  pooled client crashes the whole server (a real `node-postgres` gotcha, not hypothetical —
  it happened during development and is what led to both fixes).
- **TLS**: `db.js` auto-enables SSL when `DATABASE_URL` contains `sslmode=require` or
  `NODE_ENV=production`, for managed providers like Neon.
- See `../DEPLOY.md` for a full free-hosting walkthrough (Neon + Render, no credit card, no
  server to manage).
