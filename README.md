# United Engineering — Maintenance Management System

A complete system for running a home-appliance maintenance business (reception / field
technician / manager roles), with a native Android app and a public customer-facing website,
both backed by the same API and database.

## Components

- **`backend/`** — Node.js + Express + PostgreSQL REST API (auth, work orders, customers,
  technicians, inventory, dashboards, reports/CSV export), plus a static public website (online
  repair requests + request tracking) served from the same server. See `backend/README.md` for
  setup and the full API reference.
- **`android/`** — Native Android app (Kotlin + Jetpack Compose + Material 3 + Hilt + Retrofit),
  Arabic RTL, with three role-specific interfaces. See `android/README.md`.
- **`DEPLOY.md`** — free hosting guide for the backend (Neon + Render).
- **`.github/workflows/`** — CI for both the backend (full endpoint smoke tests) and the Android
  app (build + lint, with the debug APK attached as a downloadable artifact).

## Quick start

1. Run the backend locally (`backend/README.md`) or deploy it for free (`DEPLOY.md`).
2. Open `android/` in Android Studio and run it, or download the latest APK from the GitHub
   Actions tab.
3. Log in with one of the seeded demo accounts listed in `backend/README.md`.
