# Deploying الهندسية المتحدة

The repository includes `render.yaml` for a Node service. Connect it to a PostgreSQL database such as Neon, set a random `JWT_SECRET`, and deploy the backend.

- Build: `npm ci && npm run migrate`.
- Start: `npm start`.
- `/health` confirms the API is listening.
- Schema migrations are additive; deploying does not reset or seed business data.

## First manager account

On a fresh database, set `MANAGER_NAME`, `MANAGER_EMAIL` or `MANAGER_PHONE`, and `MANAGER_PASSWORD` in a trusted local environment or host shell. Use the database connection string for the same database, then run `npm run bootstrap`. Remove those bootstrap variables after the manager account is created. The command refuses to change databases with existing accounts.

Existing deployments continue using existing accounts. Create staff and stock from the manager interface; change any old demo passwords before staff use. The old HTTP seed endpoint is removed.

## Existing demo data

Do not rerun old seed scripts or truncate tables. Take a backup and identify which customers/orders/invoices are demo records first. Cleanup of existing operational data requires a separate, reviewed procedure; the code update does not delete it.

## Android

Build `android/` with JDK 17 and SDK 34, or use the GitHub Actions debug APK artifact. Set the backend URL on the login screen. Changing the server logs out the device so credentials cannot be sent to a different server accidentally. The app is branded الهندسية المتحدة and all new intake belongs to الحدايق.

Free hosting can sleep; the app allows for server cold starts. The visit schedule and activity log are available to the manager. Live GPS tracking is not implemented.
