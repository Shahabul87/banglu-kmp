# banglu-kmp

Kotlin Multiplatform Bengali phonetic typing engine, Android IME keyboard, and iOS keyboard skeleton.

## Modules

- `shared/` — KMP engine (parity with `banglu-web` SmartEngine)
- `android-keyboard/` — Android IME (Jetpack Compose UI)
- `ios-keyboard-engine/` — Pure Swift port of the engine
- `ios-app/` — iOS SwiftUI host app + keyboard extension skeleton
- `dictionary-compiler/` — JSON → SQLite compiler for the 480K dictionary

## Build

```bash
# Run shared engine tests and Android IME privacy boundary checks
./gradlew :android-keyboard:verifyImePrivacyBoundary :shared:allTests

# Validate Android release artifacts before launch
./scripts/validate_android_release.sh

# Optional real-device smoke during release validation
RUN_DEVICE_SMOKE=1 ./scripts/validate_android_release.sh
```

## Related

Web companion: [banglu-web](https://github.com/Shahabul87/bangluweb)
