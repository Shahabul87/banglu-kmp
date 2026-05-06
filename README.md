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
# Run shared engine tests
./gradlew :shared:allTests

# Build Android release AAB
./gradlew :android-keyboard:bundleRelease
```

## Related

Web companion: [banglu-web](https://github.com/Shahabul87/bangluweb)
