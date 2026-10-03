# Deploying the backend for free (no server of your own)

Two steps: a database on **Neon** (free forever, no credit card), and an API server on
**Render** (free, no credit card, no Dockerfile needed — it auto-detects the Node project).

> Why Neon instead of Render's own free database? Render's free database **gets deleted after
> 30 days** unless you upgrade to a paid plan. Neon's free tier never expires, which is what a
> real system the maintenance center uses daily actually needs.

## 1. Database (Neon)

1. Go to [neon.com](https://neon.com) and create a free account (Google/GitHub sign-in, no card
   needed).
2. Create a **New Project** — any name, pick the region closest to your users.
3. From the project dashboard, copy the **Connection string** (looks like
   `postgres://user:password@ep-xxxx.neon.tech/dbname?sslmode=require`). You'll need it in the
   next step.

## 2. Server (Render)

1. Connect this repo to your GitHub account (if you haven't already).
2. Go to [render.com](https://render.com), create a free account, and connect it to your GitHub
   account.
3. From the dashboard: **New → Blueprint**, select this repo. Render will find `render.yaml` at
   the repo root automatically and set up a service.
4. Before deploying, it will ask for a `DATABASE_URL` value — paste the connection string you
   copied from Neon. (`JWT_SECRET` is generated automatically, nothing to do there.)
5. Click **Apply/Deploy**. The first run takes 2–3 minutes (installing packages and running
   `npm run migrate`, which sets up the database tables).
6. Once done, you'll get a URL like `https://your-service.onrender.com` — that's your API
   address. Test it: `https://your-service.onrender.com/health` should return `{"ok":true}`.

### Note: the free service sleeps

Render's free tier "sleeps" after 15 minutes of inactivity, and the first request after that
takes about a minute to "wake up." That's expected behavior on the free plan, not a bug.

## 3. Seeding data for the first time (once only)

After the first successful deploy, run the seed once to create login accounts and demo data:

1. From the service page on Render, go to the **Shell** tab (a live terminal inside the
   server itself).
2. Type `npm run seed` and press Enter.
3. It will print the login accounts (password for all demo accounts: `Passw0rd!`).

⚠️ **Don't run `npm run seed` again** afterward unless you want to wipe all real data and reset
back to demo data — it truncates everything before inserting the demo rows.

## 4. Connecting the Android app to the server

The app has a "عنوان السيرفر" (server address) setting on the login screen — enter your Render
URL there once after the first login, and it stays saved on the device. No rebuild needed when
the server address changes.

## After that

- Change every real account's password (not the demo accounts) before handing the app to
  technicians/reception.
- To clear the demo accounts/data and start with real, empty data, run a manual SQL query from
  the Shell tab to delete the demo rows, or ask for a dedicated cleanup script.
