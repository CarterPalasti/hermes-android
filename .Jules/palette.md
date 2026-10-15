## 2024-10-15 - Compose Keyboard IME Actions

**Learning:** When building forms in Jetpack Compose, missing `KeyboardOptions` (specifically `ImeAction.Next` and `ImeAction.Go`) severely impacts mobile UX because the user must manually dismiss the keyboard to tap the submit button.
**Action:** Always configure `KeyboardOptions` and `KeyboardActions` on `OutlinedTextField` sequences so users can progress via the keyboard's "Next" and "Go" buttons seamlessly.
