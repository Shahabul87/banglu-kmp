# Play Console — Data Safety Form Answers (Banglu Keyboard)

Ready-to-enter answers for **Play Console → App content → Data safety**.
Derived from the 2026-07-10 code audit (verifyImePrivacyBoundary enforced;
IME process offline; network only in the :ui process account/sync/billing
code and the system speech recognizer).

## Section 1 — Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (only when the user signs in / uses voice) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS backend; auth tokens AES/GCM at rest) |
| Do you provide a way for users to request that their data is deleted? | **Not applicable** — this version has NO accounts and collects NO data off-device. Learned words live only on the device and are clearable in Settings (and removed by uninstall). |

## Section 2 — Data types

### Collected: Personal info
- **Email address / Name** — Collected, NOT shared. Optional (only with account sign-in). Purpose: Account management. Encrypted in transit: yes.

### Collected: Audio
- **Voice or sound recordings** — Collected (processed ephemerally), **shared with the device's speech provider (e.g., Google)** for transcription. Optional (only when the user taps the mic, after the prominent-disclosure screen). Purpose: App functionality. Not stored by Banglu.

### Collected: App activity (only if signed in + sync)
- **Other user-generated content** (custom typing formulas / dictionary preferences synced to the Banglu backend) — Collected, NOT shared. Optional. Purpose: App functionality (cross-device sync).

### Collected: Purchase history (only if subscribing)
- **Purchase history** — Google Play Billing subscription state validated with the backend. Collected, NOT shared. Purpose: App functionality.

### NOT collected (answer No)
- Keystrokes / typed text: **never leaves the device**. Learned words, clipboard history, and the dictionary are local-only.
- Location, contacts, photos, files, health, financial info, device IDs, analytics/diagnostics telemetry: none. (S44 launch build: no accounts, no billing, and no INTERNET permission — verifiable via `aapt dump permissions`.)

## Section 3 — Declarations that reviewers check for keyboards

- **Privacy policy URL:** https://shahabul87.github.io/banglu-privacy-policy/
- **Prominent disclosure (mic):** in-app `VoicePermissionActivity` shows a
  disclosure and gets consent BEFORE requesting RECORD_AUDIO. ✅ shipped.
- **IME data notice:** Play may show the standard "this keyboard may collect
  text you type" system warning at enable-time — that is OS-level and expected;
  the policy + form above answer it.

## Content rating questionnaire
- Everyone; no user-generated public content, no violence/sexual content,
  utility app category (Tools / Productivity).

## Also required in Console before first release
- App access: provide a demo account ONLY if reviewers need sign-in to test
  gated features; core keyboard works without sign-in — state that.
- Ads declaration: **No ads**.
- Government apps / COVID / financial features: No.
