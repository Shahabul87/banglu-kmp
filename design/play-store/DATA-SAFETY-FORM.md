# Play Console — Data Safety Form Answers (Banglu Keyboard)

Ready-to-enter answers for **Play Console → App content → Data safety**.
Derived from the 2026-07-10 code audit, reconciled 2026-08-17 (S108) against
the ACTUAL launch build: the account feature is disabled and the merged
release manifest carries **no INTERNET permission at all** (verifiable via
`aapt dump permissions` on the .aab — the manifest merger REJECTS the
account library's INTERNET/BILLING requests). Sign-in, sync, and purchase
collection are therefore IMPOSSIBLE in this build; declaring them would
contradict the permission dump and invite reviewer questions.

**Declare for the launch build: Audio only.** When the account feature ships
(INTERNET restored), switch to the "future account build" answers kept in
the appendix below.

## Section 1 — Data collection and security (LAUNCH BUILD)

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** — Audio only (voice typing sends speech to the device's speech provider). Nothing else leaves the device. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (speech goes through the OS SpeechRecognizer's own encrypted channel; the app itself has no network permission). |
| Do you provide a way for users to request that their data is deleted? | **Yes** — no data is stored off-device by Banglu; audio is processed ephemerally by the OS speech provider and never stored by Banglu. On-device learned words are clearable in Settings and removed by uninstall. |

## Section 2 — Data types (LAUNCH BUILD)

### Collected: Audio
- **Voice or sound recordings** — Collected (processed ephemerally), **shared with the device's speech provider (e.g., Google)** for transcription. Optional (only when the user taps the mic, after the prominent-disclosure screen). Purpose: App functionality. Not stored by Banglu.

### NOT collected (answer No)
- Keystrokes / typed text: **never leaves the device**. Learned words, clipboard history, and the dictionary are local-only.
- Personal info (email/name), app activity, purchase history: the launch build has no accounts, no billing, and no INTERNET permission — these CANNOT be collected.
- Location, contacts, photos, files, health, financial info, device IDs, analytics/diagnostics telemetry: none.

## Appendix — future account build (do NOT enter until INTERNET returns)

When the account/sync/billing feature ships in the :ui process, add:
- **Email address / Name** — Collected, NOT shared. Optional (only with account sign-in). Purpose: Account management. Encrypted in transit: yes (HTTPS backend; auth tokens AES/GCM at rest).
- **App activity** (custom typing formulas / dictionary preferences synced to the Banglu backend) — Collected, NOT shared. Optional. Purpose: App functionality (cross-device sync).
- **Purchase history** — Google Play Billing subscription state validated with the backend. Collected, NOT shared. Purpose: App functionality.
- Section 1 deletion answer becomes: **Yes** — account deletion in-app + backend erasure.

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
