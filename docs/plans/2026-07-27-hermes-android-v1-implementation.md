# Hermes Android v1 — Full Implementation Plan (Local-Model Ready)

> **For Hermes / local models:** Execute **one work package at a time**. Do not skip acceptance checks. After each package: `./gradlew :app:assembleDebug` must pass. Prefer small PRs/commits per package.
>
> **Human owner:** Carter  
> **Repo:** `C:\Users\CarterPalasti\hermes-android`  
> **Architecture source of truth:** `C:\Users\CarterPalasti\AppData\Local\hermes\profiles\main\plans\android-hermes-app.md`  
> **Kanban:** `C:\Users\CarterPalasti\hermes-android\KANBAN.md` (update after each package)

---

## Goal

Ship a personal Android client that talks to remote `hermes serve` and covers locked **v1** features: real chat (stream + tools + approvals + clarify + interrupt + attach), sessions admin, models, profiles, config, cron, gateway, pairing, logs, ops, app settings — with Nous-branded UI shell already started.

**Out of v1:** skills, toolsets, plugins, MCP, file explorer, webhooks, memory admin, credential pool, OAuth admin, analytics, hosting backend, voice (v1.1).

---

## Architecture (do not reinvent)

| Concern | Approach |
|---------|----------|
| Auth | `provider=basic`, user `Hermes`, pass `Password` (dev). `POST /auth/password-login` → cookies → `POST /api/auth/ws-ticket` → WS `?ticket=` |
| Chat | JSON-RPC over `/api/ws` — **not** REST message streaming |
| Management | Dashboard REST (`/api/sessions`, `/api/model/*`, `/api/config*`, `/api/cron/*`, etc.) |
| UI | Kotlin + Jetpack Compose Material3, API 31+, package `app.hermes` |
| State | `AppViewModel` + `StateFlow` (Factory). Prefer extracting feature VMs when a file exceeds ~400 lines of new logic |
| Brand | Nous blue `#0000F2` accents; white/black readable body; faint watermark; blue top bar |

### Critical RPC / events (chat)

**Outbound**
- `session.create`, `session.resume`, `session.list` (as needed)
- `prompt.submit` `{ session_id, text, ... }`
- `session.interrupt`
- `approval.respond`
- `clarify.respond` (confirm exact name in `tui_gateway` if build fails)
- `file.attach` (images first)

**Inbound**
- `gateway.ready`, `session.info`
- `message.start` / `message.delta` / `message.complete`
- `tool.start` / `tool.progress` / `tool.complete`
- `thinking.delta` or `reasoning.delta`
- `approval.request`, `clarify.request`
- `error`, `status.update`

**Reference sources (read before coding chat packages)**
- `AppData/Local/hermes/hermes-agent/apps/shared/src/json-rpc-gateway.ts`
- `apps/desktop/src/app/session/hooks/use-message-stream/`
- `hermes_cli/dashboard_auth/routes.py` (`/auth/password-login`, `/api/auth/ws-ticket`)
- `tui_gateway/server.py` method table
- `web/src/lib/api.ts` REST surface

### Live test target

- URL: `http://192.168.1.201:9119` (or host running `hermes serve --host 0.0.0.0 --port 9119`)
- Auth: username `Hermes`, password `Password`, provider **`basic`** (never `"password"`)

### Build / deploy commands (every package)

```bash
export JAVA_HOME="/c/Program Files (x86)/Android/openjdk/jdk-17.0.14"
export PATH="/c/Program Files (x86)/Android/android-sdk/platform-tools:$PATH"
cd /c/Users/CarterPalasti/hermes-android
./gradlew :app:assembleDebug
# deploy if device online:
DEV='adb-R5CY70F83BH-j0Ov3Q (2)._adb-tls-connect._tcp'   # or whatever adb devices shows
adb -s "$DEV" install -r 'C:\Users\CarterPalasti\hermes-android\app\build\outputs\apk\debug\app-debug.apk'
adb -s "$DEV" shell am force-stop app.hermes
adb -s "$DEV" shell am start -n app.hermes/.ui.MainActivity
```

### Coding rules for local models

1. **Do not** invent Basic Auth headers. Cookie jar + ticket only for gated mode.
2. **Do not** replace JSON-RPC chat with REST chat.
3. Keep Compose state as `StateFlow` + ViewModel Factory (no `mutableStateOf` for shared app state).
4. Prefer small pure functions + unit tests for JSON-RPC parsing/event reduce.
5. Touch only files listed in the package unless a compile error forces a tiny fix elsewhere — then note it.
6. Update `KANBAN.md` status when package done.
7. Never commit secrets beyond existing `DevCredentials.kt` TEMP hardcodes; do not expand secret storage to plaintext long-term without encrypted prefs.
8. If stuck on RPC method name: grep hermes-agent `tui_gateway` — do not guess forever.

### Current codebase map (as of plan write)

```
app/src/main/java/app/hermes/
  HermesApplication.kt
  auth/AuthManager.kt
  net/
    SharedHttp.kt          # cookie client + HermesApi (models/sessions/ticket)
    LoginHelper.kt
    CookieJarImpl.kt
    ApiClientFactory.kt    # thin / possibly redundant
    gateway/
      GatewayWsClient.kt
      GatewayConnectionManager.kt
  ui/
    MainActivity.kt        # shell nav
    AppViewModel.kt        # GOD object — prefer extract after WP-A
    ChatScreen.kt
    SessionsScreen.kt
    ModelsScreen.kt
    ManageHubScreen.kt
    CronScreen.kt          # placeholder
    LoginScreen.kt
    Models.kt              # AppTab, ManagePage, ChatMessage, AppState
    DevCredentials.kt
    components/ShellComponents.kt
    theme/HermesTheme.kt
```

---

## Work package index (execute in order unless noted)

| ID | Name | Depends on | Est. model sessions |
|----|------|------------|---------------------|
| **WP-0** | Guardrails & inventory | — | 1 |
| **WP-A** | JSON-RPC client hardening | WP-0 | 1–2 |
| **WP-B** | Chat event model + reducer | WP-A | 1–2 |
| **WP-C** | Chat UI: tools / thinking | WP-B | 1 |
| **WP-D** | Approvals + clarify | WP-B, WP-C | 1–2 |
| **WP-E** | Interrupt / reconnect / resume polish | WP-A, WP-B | 1 |
| **WP-F** | Image attachments | WP-A, WP-B | 1–2 |
| **WP-G** | Home dashboard | WP-E optional | 1 |
| **WP-H** | Sessions admin extras | — | 1 |
| **WP-I** | Profiles | — | 1 |
| **WP-J** | Config editor | — | 1–2 |
| **WP-K** | Cron real API | — | 1 |
| **WP-L** | Gateway status | — | 1 |
| **WP-M** | Pairing | — | 1 |
| **WP-N** | Logs | — | 1 |
| **WP-O** | Ops (doctor/backup/debug-share) | — | 1 |
| **WP-P** | App settings | — | 1 |
| **WP-Q** | Visual polish pass | any after shell | 1 |
| **WP-R** | Hardening (encrypted store, strip dev creds flag) | after WP-A login stable | 1 |

**Parallelism for local models:**  
- After **WP-B** lands, **WP-G / H / I / J / K / L / M / N / O / P** can run in parallel with chat packages **C–F** if different files.  
- **Do not** parallelize two packages both heavily editing `AppViewModel.kt` without first finishing **WP-A** extract.

---

# WP-0 — Guardrails & inventory

**Objective:** Freeze facts so implementers do not thrash.

### Tasks

1. Read this plan + architecture plan fully.
2. Confirm `DevCredentials` and login use provider discovery → `basic`.
3. Run `./gradlew :app:assembleDebug` — must be green before any feature work.
4. List open stubs in Manage hub (`ManagePage` entries with `implemented=false`).
5. Update KANBAN: mark WP-0 done.

**Acceptance**
- [ ] Clean debug build
- [ ] Document any drift between this plan and code in a short `docs/plans/STATUS.md` note

**Files:** none required unless STATUS note  
**Create:** `docs/plans/STATUS.md` (optional progress log)

---

# WP-A — JSON-RPC client hardening

**Objective:** Make WS RPC correct: request IDs, pending futures, proper event dispatch, ticket refresh on reconnect.

### Why

`GatewayWsClient` / `GatewayConnectionManager` currently simplify responses (resume without matching id). Chat reliability depends on real JSON-RPC 2.0.

### Files

- Modify: `net/gateway/GatewayWsClient.kt`
- Modify: `net/gateway/GatewayConnectionManager.kt` (or merge into one client — pick one primary API)
- Modify: `ui/AppViewModel.kt` (use new client API)
- Create: `net/gateway/JsonRpcTypes.kt`
- Create: `test/.../JsonRpcParserTest.kt` (unit test JVM if possible; else pure Kotlin tests)

### Required behavior

```text
send(method, params) -> suspend Result<JSONObject>
// assigns id, waits for { id, result } or { id, error }
// notifications/events: { method, params } without id → callbacks
```

**Events to surface via Flow or callbacks:**
`gateway.ready`, `message.*`, `tool.*`, `thinking.delta`, `approval.request`, `clarify.request`, `session.info`, `error`

### Steps for implementer

1. Add `JsonRpcTypes.kt` with sealed classes for Request / Response / Notification.
2. Rewrite parser: if `id != null` complete deferred; if `method != null` emit event.
3. Replace fake `delay(1000)` response completion.
4. On reconnect: mint new ticket via `HermesApi.mintWsTicket()`, reconnect, optionally `session.resume`.
5. Unit-test parse of sample frames (copy sanitized samples from a live session log if available).

### Acceptance

- [ ] `prompt.submit` returns real JSON-RPC result or error (logged)
- [ ] Streaming deltas still update UI
- [ ] Disconnect + Retry reconnects and can send another message
- [ ] `assembleDebug` green

### Done definition for chat packages

Do **not** start WP-D until WP-A event dispatch can deliver `approval.request` to ViewModel even if UI stub logs only.

---

# WP-B — Chat transcript model + event reducer

**Objective:** Replace flat `ChatMessage(role, content)` with structured transcript items so tools/approvals render correctly.

### Files

- Create: `ui/chat/ChatModels.kt`
- Create: `ui/chat/ChatEventReducer.kt`
- Create: `test/.../ChatEventReducerTest.kt`
- Modify: `ui/AppViewModel.kt` — store `List<TranscriptItem>`
- Modify: `ui/ChatScreen.kt` — render new model
- Modify: `ui/Models.kt` — deprecate or slim old `ChatMessage` if unused

### Data model (minimum)

```kotlin
sealed class TranscriptItem {
  data class Message(
    val id: String,
    val role: Role, // User, Assistant, System
    val parts: List<ContentPart>,
    val isStreaming: Boolean = false,
  ) : TranscriptItem()

  data class ToolCall(
    val id: String,
    val name: String,
    val argsPreview: String,
    val outputPreview: String?,
    val status: ToolStatus, // Running, Done, Error
  ) : TranscriptItem()

  data class ApprovalPrompt(
    val id: String,
    val title: String,
    val detail: String,
    val options: List<String>, // e.g. allow_once, allow_always, deny
  ) : TranscriptItem()

  data class ClarifyPrompt(
    val id: String,
    val question: String,
    val choices: List<String>?,
  ) : TranscriptItem()
}

sealed class ContentPart {
  data class Text(val text: String) : ContentPart()
  data class Thinking(val text: String) : ContentPart()
}
```

### Reducer rules

- `message.start` → create assistant bubble streaming
- `message.delta` → append text part
- `message.complete` → isStreaming=false
- `tool.start` → add ToolCall Running
- `tool.complete` → update output + Done
- `approval.request` → add ApprovalPrompt (block composer send except respond)
- `clarify.request` → add ClarifyPrompt

Map real payload field names from live events; keep adapter layer if desktop uses nested `params.payload`.

### Acceptance

- [ ] Unit tests cover delta append + tool lifecycle
- [ ] UI still shows user/assistant text
- [ ] No crash on unknown event (ignore or system log line)
- [ ] Build green

---

# WP-C — Chat UI: tools + thinking

**Objective:** Readable tool cards and collapsible thinking.

### Files

- Modify: `ui/ChatScreen.kt`
- Create: `ui/chat/ToolCallCard.kt`
- Create: `ui/chat/ThinkingBlock.kt`

### UI requirements

- Tool rows collapsed by default: show name + status chip
- Expand: args + stdout/stderr (truncate > 4k chars with “Show more”)
- Thinking in muted collapsible block above/within assistant message
- Keep Nous styling: readable surfaces, blue user bubbles OK

### Acceptance

- [ ] Live turn that uses a tool shows a tool card
- [ ] Long tool output does not freeze UI
- [ ] Build green

---

# WP-D — Approvals + clarify

**Objective:** Unblock agent turns that wait on human input.

### Files

- Modify: `ui/chat/ChatEventReducer.kt`
- Create: `ui/chat/ApprovalCard.kt`
- Create: `ui/chat/ClarifyCard.kt`
- Modify: `ui/AppViewModel.kt` — `respondApproval`, `respondClarify`
- Modify: `ui/ChatScreen.kt` — render cards; disable normal send while blocking prompt active (optional: allow queue)

### RPC

```kotlin
// Confirm exact param keys against tui_gateway
ws.send("approval.respond", mapOf(
  "request_id" to id,
  "decision" to "allow_once" // or allow_always / deny
))
ws.send("clarify.respond", mapOf(
  "request_id" to id,
  "text" to answer // or choice
))
```

### Acceptance

- [ ] Forced approval scenario completes after Allow
- [ ] Deny stops or returns error cleanly
- [ ] Clarify free-text answer continues turn
- [ ] Build green + manual phone test

---

# WP-E — Interrupt / reconnect / session resume

**Objective:** Production-feel connection reliability.

### Files

- Modify: `ui/AppViewModel.kt`
- Modify: `net/gateway/*`
- Modify: `ui/ChatScreen.kt` (banner already exists — ensure accurate states)

### Behavior

1. `interrupt()` → `session.interrupt` and clear busy flag on ack/event
2. On WS drop → state ERROR/CLOSED, auto-retry with backoff (cap attempts)
3. On reopen → `session.resume` if runtime session id known
4. If resume fails → create new session and system message
5. Composer disabled when not OPEN; draft text preserved

### Acceptance

- [ ] Stop cancels in-flight turn (or best-effort)
- [ ] Toggle airplane mode briefly → Retry works
- [ ] Session id stable across reconnect when server allows
- [ ] Build green

---

# WP-F — Image attachments

**Objective:** Pick image → upload/attach → include in next prompt.

### Files

- Create: `ui/chat/AttachmentPicker.kt` (Activity Result API)
- Modify: `AndroidManifest.xml` — only needed media permissions for API 31+ (prefer photo picker; avoid broad storage if possible)
- Modify: `net/SharedHttp.kt` or new `net/UploadApi.kt` — multipart if REST upload exists; else WS `file.attach`
- Modify: `ui/ChatScreen.kt` — attach button
- Modify: `ui/AppViewModel.kt`

### Research step (mandatory first hour)

Grep desktop for `file.attach` and upload endpoints. Document chosen path in STATUS.md.

### Acceptance

- [ ] Pick a photo from gallery
- [ ] Assistant can “see” or acknowledges attachment per server capability
- [ ] Build green

---

# WP-G — Home dashboard

**Objective:** Landing summary after login (or first tab content).

### Option A (recommended)

Add `AppTab.Home` **or** show Home as Chat empty-state header + Manage top card.  
Prefer: **Chat empty state becomes mini-home** to avoid 5-tab clutter — unless product wants explicit Home tab.

### Files

- Create: `ui/HomePanel.kt`
- Modify: `ChatScreen.kt` empty state OR `MainActivity.kt` tab
- Modify: `HermesApi` — `GET /api/status` fields as available

### Show

- Base URL, connection chip
- Active model/provider
- Last 3 sessions (tap opens)
- Buttons: New chat, Reconnect, Open Manage

### Acceptance

- [ ] Visible without hunting
- [ ] Taps navigate correctly
- [ ] Build green

---

# WP-H — Sessions admin extras

**Objective:** Search / rename / delete / export beyond list+open.

### Files

- Modify: `net/SharedHttp.kt` (`HermesApi`)
- Modify: `ui/SessionsScreen.kt`
- Modify: `ui/AppViewModel.kt`

### REST (verify against live OpenAPI/dashboard)

Typical patterns from desktop web:
- `GET /api/sessions?q=` or `/api/sessions/search?q=`
- `PATCH/PUT` title
- `DELETE /api/sessions/{id}`
- export download URL

### UI

- Search field
- Long-press or overflow menu: Rename, Delete, Export
- Confirm delete dialog

### Acceptance

- [ ] Search filters list
- [ ] Rename persists after refresh
- [ ] Delete removes session
- [ ] Export shares/saves file or opens share sheet
- [ ] Build green

---

# WP-I — Profiles

**Objective:** List/switch profiles (create/delete if API easy).

### Files

- Create: `ui/ProfilesScreen.kt`
- Modify: `ManageHubScreen` / `ManagePage.Profiles.implemented = true`
- Modify: `HermesApi` — `/api/profiles`, switch endpoint
- Modify: `AppViewModel` — on switch, refresh models/sessions and reconnect WS if required

### Acceptance

- [ ] See profiles
- [ ] Switch active profile reflected in model label
- [ ] Build green

---

# WP-J — Config editor

**Objective:** Schema-driven tree + raw YAML toggle.

### Files

- Create: `ui/config/ConfigScreen.kt`
- Create: `ui/config/ConfigTree.kt`
- Create: `ui/config/ConfigRawEditor.kt`
- Modify: `HermesApi`:
  - `GET /api/config`
  - `GET /api/config/schema`
  - `GET /api/config/raw`
  - `PUT` equivalents (confirm methods)
- Set `ManagePage.Config.implemented = true`

### UX

- Default: tree of sections
- Edit scalar fields with proper types (bool switch, number, string)
- “Raw YAML” mode for power users
- Save + discard; show server validation errors

### Acceptance

- [ ] Load config
- [ ] Change a harmless string field, save, reload shows change
- [ ] Raw mode round-trips
- [ ] Build green

**Safety:** Do not auto-edit auth secrets in tests against prod gateway without user OK.

---

# WP-K — Cron real API

**Objective:** Replace Cron placeholder.

### Files

- Replace: `ui/CronScreen.kt`
- Create: `ui/cron/CronDetailScreen.kt` (optional)
- Modify: `HermesApi` — `/api/cron` list, trigger, pause/resume
- Modify: `AppViewModel` or `CronViewModel`

### UI

- List jobs: name, schedule, enabled
- Actions: Run now, Pause/Resume
- Detail: last run status if API provides

### Acceptance

- [ ] Lists real jobs from server
- [ ] Trigger causes a run (verify via jobs API or logs)
- [ ] Build green

---

# WP-L — Gateway status

**Objective:** Show gateway/platforms health + test action if available.

### Files

- Create: `ui/GatewayScreen.kt`
- Modify: `HermesApi` — status + platform test endpoints from `web/src/lib/api.ts`
- `ManagePage.Gateway.implemented = true`

### Acceptance

- [ ] Shows running/not + platforms
- [ ] Test button returns visible result
- [ ] Build green

---

# WP-M — Pairing

**Objective:** Approve/revoke/clear pending pairings.

### Files

- Create: `ui/PairingScreen.kt`
- Wire REST pairing endpoints
- `ManagePage.Pairing.implemented = true`

### Acceptance

- [ ] Lists pending/paired
- [ ] Approve/revoke works against live server (or empty state if none)
- [ ] Build green

---

# WP-N — Logs

**Objective:** Tail/filter logs.

### Files

- Create: `ui/LogsScreen.kt`
- `GET /api/logs` (params: level, limit, source)
- Auto-refresh toggle 2–5s
- `ManagePage.Logs.implemented = true`

### Acceptance

- [ ] Shows log lines
- [ ] Filter by level
- [ ] Build green

---

# WP-O — Ops

**Objective:** Doctor, backup, debug-share, gateway restart if exposed.

### Files

- Create: `ui/OpsScreen.kt`
- Wire endpoints carefully (destructive actions need confirm dialogs)
- `ManagePage.Ops.implemented = true`

### Acceptance

- [ ] Doctor output displayed
- [ ] Backup triggers download or path message
- [ ] Confirm dialogs on restart
- [ ] Build green

---

# WP-P — App settings

**Objective:** Local-only settings.

### Files

- Create: `ui/SettingsScreen.kt`
- Create: `data/UserPreferences.kt` (DataStore)
- Theme: System / Light / Dark
- Text size scale
- Clear saved login
- `ManagePage.Settings.implemented = true`
- Wire `HermesTheme(darkTheme = …)` from prefs

### Acceptance

- [ ] Theme switch persists across process death
- [ ] Logout/clear creds returns to login
- [ ] Build green

---

# WP-Q — Visual polish pass

**Objective:** Improve readability/brand without new features.

### Checklist

- [ ] Watermark opacity tunable (0.04–0.08)
- [ ] Consistent 8dp grid padding
- [ ] Type hierarchy: title/body/label
- [ ] Cards elevation + contrast on light and dark
- [ ] Login remains blue hero; in-app body white/black
- [ ] Remove leftover debug labels (“Dev auto-fill”) from production UI or hide behind `BuildConfig.DEBUG`
- [ ] Launcher icon + app name “Hermes Agent”

### Files

- `theme/HermesTheme.kt`, `components/ShellComponents.kt`, screens as needed

---

# WP-R — Hardening

**Objective:** Safer defaults for daily use.

### Tasks

1. EncryptedSharedPreferences / Security Crypto for password optional save
2. `DevCredentials.AUTO_LOGIN` only if `BuildConfig.DEBUG`
3. Certificate/cleartext: keep LAN cleartext but document risk
4. Crash-safe WS close on `onCleared`
5. Strip verbose Log.i of full message payloads if they contain secrets

### Acceptance

- [ ] Release build does not hardcode password in UI defaults (debug OK)
- [ ] No cookie bleed across logout
- [ ] Build green debug + release assemble

---

## Suggested execution schedule for local models

### Sprint 1 — Chat is real
1. WP-0  
2. WP-A  
3. WP-B  
4. WP-C  
5. WP-D  
6. WP-E  

**Gate:** User can run a tool-using turn with approval on phone.

### Sprint 2 — Management surface
7. WP-H, WP-I, WP-J (parallel OK)  
8. WP-K, WP-L, WP-M (parallel OK)  
9. WP-N, WP-O, WP-G  

### Sprint 3 — Polish
10. WP-F (attachments)  
11. WP-P  
12. WP-Q  
13. WP-R  

---

## Per-package report template (models must return this)

```markdown
## Package WP-X complete
- Files touched: ...
- Build: PASS/FAIL (paste last 20 lines if fail)
- Manual test: ...
- Deviations from plan: ...
- Follow-ups: ...
- KANBAN updated: yes/no
```

---

## Explicit non-goals (reject if model starts these)

- Rewriting app in React Native/Flutter
- Embedding full Hermes backend on device
- Skills/plugins/MCP admin UIs
- Perfect desktop pixel clone
- Multi-server fleets (single server v1)
- Voice input

---

## Quick “start here” prompt for a local model

Copy-paste:

```text
You are implementing Hermes Android from the plan at:
C:\Users\CarterPalasti\hermes-android\docs\plans\2026-07-27-hermes-android-v1-implementation.md

Do ONLY work package WP-A (JSON-RPC client hardening).
Follow coding rules in the plan.
Read existing gateway files before editing.
Run ./gradlew :app:assembleDebug at the end.
Return the package report template.
Do not start WP-B.
```

---

## Tracking

| Field | Value |
|-------|--------|
| Plan path | `hermes-android/docs/plans/2026-07-27-hermes-android-v1-implementation.md` |
| Architecture | `profiles/main/plans/android-hermes-app.md` |
| Kanban | `hermes-android/KANBAN.md` |
| Next package | **WP-0** then **WP-A** |
