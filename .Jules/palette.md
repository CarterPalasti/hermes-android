## 2024-10-01 - Mobile Keyboard UX on Login Forms
**Learning:** Text fields without specific `KeyboardOptions` use the default keyboard which autocorrects URLs and usernames, and lacks proper "Next" or "Done" IME actions for logical flow.
**Action:** Always provide `KeyboardOptions` tailored to the field type (e.g., `KeyboardType.Uri`, `KeyboardType.Password`) and set correct `imeAction` (Next/Done) along with `autoCorrect = false` where appropriate (e.g., usernames, URLs).
