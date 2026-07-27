# Hermes Android App — Project Kanban

**Plan (local models execute this):**  
`docs/plans/2026-07-27-hermes-android-v1-implementation.md`

**Auth:** user `Hermes` / pass `Password` / provider `basic` @ `:9119`  
**Brand:** Nous blue `#0000F2` chrome; white/black readable body + watermark  

---

## Done

- Project scaffold, login, WS chat skeleton, sessions list, models select  
- App shell nav (Chat / Sessions / Cron / Manage)  
- Nous-inspired theme + graphics (readable pass)  
- Full v1 implementation plan written for local-model execution  

---

## Next (from plan)

| Order | Package | Status |
|-------|---------|--------|
| 1 | **WP-0** Guardrails & inventory | ⬜ |
| 2 | **WP-A** JSON-RPC client hardening | ⬜ |
| 3 | **WP-B** Chat event model + reducer | ⬜ |
| 4 | **WP-C** Tools + thinking UI | ⬜ |
| 5 | **WP-D** Approvals + clarify | ⬜ |
| 6 | **WP-E** Interrupt / reconnect / resume | ⬜ |
| 7 | **WP-F** Image attachments | ⬜ |
| 8 | **WP-G…R** Home, sessions admin, profiles, config, cron, gateway, pairing, logs, ops, settings, polish, hardening | ⬜ |

**Sprint 1 gate:** tool-using turn with approval works on phone.

---

## Rules for implementers

1. One work package at a time  
2. `assembleDebug` green before claiming done  
3. No Basic Auth invention; no REST-only chat  
4. Update this kanban when a WP finishes  
