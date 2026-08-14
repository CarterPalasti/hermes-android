# STATUS — Hermes Android (Polished build)

Date: 2026-08-12

## Build
- `assembleDebug`: **PASS** (verified after all changes)
- Deploy: phone ADB offline at end of run — APK ready at
  `app/build/outputs/apk/debug/app-debug.apk`

## Implemented

### Protocol / chat
- `JsonRpcTypes.kt` — request/response/notification parse + build
- `GatewayWsClient` — id-matched pending callbacks via JsonRpcTypes
- Auto-reconnect with backoff (2s, 4s, 8s, 16s, 32s; max 5 attempts)
- Reconnect only when still authenticated (checks `currentBaseUrl`, `lastUsername`, `lastPassword`)
- On reconnect success: re-login + mint ticket, then session.resume or session.create
- Manual reconnect via `AppViewModel.reconnect()` remains functional

### Sessions
- Search query, open/resume, rename, delete, export→clipboard

### Manage / Cron
- Models, Profiles, Config raw editor, Gateway status, Pairing dump, Logs, Ops (doctor/backup/restart), Settings (theme + logout)
- Cron list + run/pause/resume (best-effort multi-path REST)

### REST
- `HermesApi` expanded with alternate path fallbacks for servers that differ slightly
- New: `uploadFile()` best-effort multipart upload (`/api/files/upload`)
- New: `SharedHttp.postMultipart()` helper

## Image attachments (WP-F)
- Photo picker via Activity Result contract (no storage permissions needed)
- Attach button on ChatScreen input area (photo icon)
- Best-effort upload: logs and continues if endpoint unavailable
- File bytes loaded from Uri via ContentResolver

## Unit tests (WP-A)
- `JsonRpcTypesTest.kt` in `app/src/test/java/app/hermes/net/gateway/`
- Tests cover:
  - Response with id + result
  - Notification without id
  - Error response
  - Unparseable JSON → ServerError

## Not done / best-effort
- Encrypted password store
- Exact REST paths may need tuning against your live `/api/*` surface if empty states appear
- Tablet split layout

## Auth reminder
- provider `basic`, user `Hermes`, password `Password`
