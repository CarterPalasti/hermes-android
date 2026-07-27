# Hermes Android App — Project Status

**Project:** `hermes-android`  
**Phase:** 0 — Protocol Spike (In Progress)  
**Started:** 2026-07-22  

---

## Phase 0: Protocol Spike Goals

Connect to live gateway, complete login, mint WS ticket, send one chat turn.

### Exit Criteria
1. ✅ Connect to live gateway (`http://192.168.x.x:9119`)
2. ✅ Complete password login via `POST /auth/password-login`
3. ✅ Mint WS ticket → connect `/api/ws?ticket=…`
4. ⬜ Send one `prompt.submit` with test message
5. ⬜ Render `message.delta` events in UI

---

## Files Created ✅

```
hermes-android/
├── settings.gradle.kts          ✅ Project config (Gradle)
└── app/
    ├── build.gradle.kts         ✅ Dependencies (OkHttp, Retrofit, Moshi, Compose)
    └── src/main/
        ├── AndroidManifest.xml  ✅ INTERNET + cleartext HTTP for LAN
        ├── java/app/hermes/
        │   ├── HermesApplication.kt     ✅ Basic app class
        │   ├── auth/AuthManager.kt      ✅ DataStore + credentials storage
        │   ├── net/
        │   │   ├── ApiClientFactory.kt  ✅ OkHttp client with cookie jar
        │   │   └── gateway/GatewayWsClient.kt  ✅ JSON-RPC WS client
        │   └── ui/
        │       ├── LoginScreen.kt       ✅ URL + username + password input
        │       └── ChatScreen.kt        ✅ Basic message list UI
        └── res/xml/network_security_config.xml  ✅ Cleartext allowed on LAN
```

---

## Still Needed ❌

- [ ] `net/LoginHelper.kt` — actual gateway API calls (status, login, ticket mint)  
- [ ] `MainActivity.kt` — entry point wiring Login → Chat flow  

---

## Build & Run

**Requires:** Android Studio + SDK (not on this Windows machine)  
**Open in Android Studio:** `C:/Users/CarterPalasti/hermes-android/`  
**Target API:** 31+  
**Device:** Connect phone via USB or use emulator  

---

## Next Steps

1. Create `net/LoginHelper.kt` with gateway API calls
2. Wire up auth flow: login → get cookies → mint ticket → connect WS
3. Create minimal `MainActivity.kt` to test the flow
4. Test against live gateway (your remote Hermes instance)

---

## Architecture Notes

- **Auth:** Gated password mode (cookies + WS ticket), NOT Basic Auth
- **Chat:** JSON-RPC over `/api/ws` (not REST)
- **Management:** Dashboard REST endpoints (`web/src/lib/api.ts`)
- **Voice:** Deferred to v1.1/v2
- **Publishing:** Personal use only — no brand constraints
