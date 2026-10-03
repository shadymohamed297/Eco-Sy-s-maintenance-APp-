# United Engineering — Android App

Native Android client (Kotlin + Jetpack Compose + Material 3), Arabic RTL, talking to the
backend in `../backend`.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Hilt (DI), Navigation Compose
- Retrofit + OkHttp + kotlinx.serialization
- DataStore Preferences (JWT storage)
- IBM Plex Sans Arabic (bundled under `app/src/main/res/font`, SIL OFL licensed — see
  `app/src/main/assets/licenses/IBMPlexSansArabic-OFL.txt`)

## Running it

1. Run the backend first (see `../backend/README.md`), or deploy it for free per `../DEPLOY.md`.
2. Open `android/` in Android Studio (Koala+) and let it sync.
3. `BuildConfig.BASE_URL` is only the initial value. The real server address is stored on-device
   via DataStore and can be changed any time from the "عنوان السيرفر" (server address) link
   under the login card — tap it, paste a URL, save. No rebuild needed to point the app at a
   different backend.
4. Run the `app` configuration. Log in with any seeded account (see backend README), password
   `Passw0rd!`.

## CI build (no local setup needed)

`.github/workflows/android-build.yml` builds the debug APK and runs lint on every push/PR that
touches `android/`, and can also be triggered manually from the Actions tab ("Run workflow").
The finished APK is attached as a downloadable workflow artifact
(`markaz-sayana-debug-apk`) — install it straight on a device or emulator without needing
Android Studio at all.

## Screens

| # | Screen | Role |
|---|--------|------|
| 1 | Reception dashboard | Reception |
| 2 | New request intake (customer + device) | Reception |
| 3 | Assign technician & schedule | Reception |
| 4 | Technician's daily tasks | Technician |
| 5 | Work order execution (checklist, parts) | Technician |
| 6 | Invoice + customer signature/close-out | Technician |
| 7 | Manager performance dashboard | Manager |
| 8 | Inventory / spare parts | Manager (+ shared) |
| Work Orders (الطلبات) | All work orders — search + status filter | Reception |
| Customers (العملاء) | Search → a customer's devices + full order history | Reception |
| Technicians (الفنيون) | Every technician with today's load, monthly completions, rating | Manager |
| Reports (التقارير) | Totals + breakdown by technician/branch/device, CSV export | Manager |
| My Performance (أدائي) | Personal stats + recent closed jobs | Technician |
| Employees (إدارة الموظفين) | Add/edit/deactivate employee accounts | Manager |

## Known gaps / deliberate simplifications

- Before/after photos use the device camera and are uploaded as base64, same as the signature —
  no cloud storage/CDN, matching the scale of this app.
- Inventory reorder emails and automated WhatsApp delivery are not implemented — those are
  external integrations out of scope for this version.

## Signing: debug build, on purpose

This app is for the maintenance center's own staff, installed directly on their phones — it's
never going to Google Play. A debug build is already signed (with Android's auto-generated debug
key) and installs and runs identically to a release build for that use case; the only real
difference is a somewhat larger APK and no R8 shrinking. If this ever needs proper release
signing (e.g. to publish in-place updates through some other channel), generate a keystore with
`keytool`, wire it into a `release` `signingConfig` in `app/build.gradle.kts`, and store the
keystore + passwords as GitHub Actions secrets rather than committing them.
