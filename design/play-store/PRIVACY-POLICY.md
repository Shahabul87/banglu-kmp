# Privacy Policy — Banglu Bengali Keyboard

**Last updated:** August 26, 2026
**Applies to:** Banglu Bengali Keyboard for Android, version 1.5.82 and later

## Overview

Banglu Bengali Keyboard ("the App") is a phonetic keyboard that converts English letters into Bengali text. The App works entirely on your device: typing, phonetic conversion, suggestions, and learning never leave your phone. This version of the Android app **does not request the INTERNET permission** and has no account, sign-in, sync, or subscription feature. The only time anything leaves your device is optional voice typing, which uses the speech recognition service already on your phone (see "Voice typing" below).

## What the App does not do

- **No keystroke logging:** the App never records or transmits what you type.
- **No analytics, advertising, or tracking:** no analytics SDK, crash reporter, or advertising framework is included.
- **No network access:** the keyboard process cannot open a network connection — the App does not hold the INTERNET permission.
- **No selling of data:** there is nothing collected to sell.

## Data stored on your device

Everything below stays in the App's private storage on your phone. It is excluded from Android cloud backup and device-to-device transfer, is not readable by other apps, and is removed when you uninstall the App.

- **Keyboard preferences:** theme, height, sounds, haptics, and similar settings.
- **Learned words and chosen spellings:** when you pick a suggestion, the App remembers that choice so the same word converts your way next time.
- **Custom conversions:** typing formulas you add yourself.
- **Next-word pairs:** which word you tend to type after another, used for on-device next-word suggestions.
- **English typing learning:** words you use while typing in English mode, for English suggestions.
- **Saved email addresses (identity assist):** when you finish typing an email address in an email field, the App can remember up to eight addresses so it can offer them as one-tap fills in other email fields. It never remembers anything typed in password, one-time-code, or "no personalized learning" fields. This is controlled by **Settings → "ইমেইল ঠিকানা মনে রাখা"**; switching it off deletes the saved addresses immediately.
- **Clipboard history:** when you open the keyboard's clipboard panel, the App can keep up to twelve recently copied texts so you can paste them again. Each item is kept for **one hour** and then deleted automatically. The App never stores a clip that the source app marked as sensitive (for example a password copied from a password manager), and never stores clips while you are in a password, one-time-code, or "no personalized learning" field. You can clear the panel at any time with its clear button.
- **Local diagnostics counters:** a few anonymous numbers (event counts, typing latency) shown on the in-app Diagnostics screen. They contain no typed text and never leave the phone.
- **Dictionary:** the bundled Bengali dictionary (read-only) is copied to the App's private storage for offline conversion.

## Deleting your data

- **Settings → "শেখা শব্দ মুছুন"** deletes every category of learned data at once — learned words and chosen spellings, custom conversions, next-word pairs, English typing learning, and saved email addresses. The App confirms deletion only after the data has actually been removed.
- **Settings switches** let you stop learning ("টাইপিং শেখা"), stop the personal dictionary, or stop remembering email addresses (which also deletes the addresses already saved).
- The **clipboard panel's clear button** deletes clipboard history; items also expire on their own after one hour.
- **Uninstalling** the App removes all of the above.

## Voice typing

Voice typing is optional and only starts when you tap the microphone. Before the first use the App explains this and asks for the microphone permission. Speech is handled by the Android speech recognition service installed on your phone — typically Google's, or your device maker's. Depending on that provider and your device settings, audio may be processed on the device (when an offline Bengali speech pack is installed) or sent to the provider's servers for transcription; the provider's own privacy policy applies to that processing. Banglu never stores audio and never receives it — the transcribed text is inserted directly into what you are typing.

## Permissions

The App requests only:

- **VIBRATE:** haptic feedback when pressing keys. Accesses no personal data.
- **RECORD_AUDIO:** used only while you are actively using voice typing, as described above.

The App does not request INTERNET, ACCESS_NETWORK_STATE, BILLING, contacts, location, storage, or any other permission.

## Data sharing

Banglu shares no data with anyone. The one exception is voice typing, where the audio goes to your phone's speech recognition provider as described above.

## Children's privacy

The App is not directed to children under 13 and collects no personal information from anyone.

## Future features

If a later version adds optional features that need the network (for example an account or cross-device sync), that version will request the permissions it needs, this policy will be updated first, and those features will stay separate from the keyboard's typing process.

## Changes to this policy

Changes are posted here with an updated date.

## Contact

If you have questions about this privacy policy, contact us at:
- Email: isham251087@gmail.com
- GitHub: https://github.com/Shahabul87

## Open source

Banglu Keyboard's phonetic engine is built with transparency in mind: every conversion happens on your device, in a keyboard process that has no network access.
