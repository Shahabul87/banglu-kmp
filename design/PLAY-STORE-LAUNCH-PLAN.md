# Play Store Launch Checklist & Settings Plan

## CRITICAL — Must fix before Play Store

### 1. Settings Activity (Currently placeholder)

Samsung keyboard settings structure:
```
Samsung Keyboard Settings
├── Languages and types
│   ├── Manage input languages (Bengali, English)
│   └── Keyboard type (QWERTY, 3x4)
├── Smart typing
│   ├── Predictive text (ON/OFF)
│   ├── Auto replace (ON/OFF)
│   ├── Auto capitalize (ON/OFF)
│   ├── Auto spacing (ON/OFF)
│   └── Auto punctuate (double-space → period)
├── Swipe, touch and feedback
│   ├── Keyboard swipe controls
│   ├── Touch and hold space bar (Cursor control / Voice input)
│   ├── Character preview popup (ON/OFF)
│   ├── Haptic feedback (ON/OFF)
│   ├── Sound feedback (ON/OFF)
│   └── Key-tap duration
├── Size and layout
│   ├── Keyboard size (slider)
│   ├── Number row (ON/OFF)
│   └── Alternative characters (ON/OFF)
├── Reset to default settings
└── About keyboard
```

**Banglu Settings (what we need):**
```
Banglu Keyboard Settings
├── Typing
│   ├── Auto-capitalize (ON/OFF) — default ON
│   ├── Double-space period (ON/OFF) — default ON
│   ├── Predictive text / Suggestions (ON/OFF) — default ON
│   └── Default mode (Banglu / English) — default Banglu
├── Feedback
│   ├── Haptic feedback (ON/OFF) — default ON
│   ├── Sound on keypress (ON/OFF) — default ON
│   └── Key preview popup (ON/OFF) — default ON
├── Layout
│   ├── Number row (ON/OFF) — default ON
│   └── Theme (Light / Dark / AMOLED / Auto) — default Auto
├── About
│   ├── Version: 1.0.0
│   ├── Engine: SmartEngine 7-layer
│   ├── Dictionary: 485K words
│   └── Open source / Website link
└── Reset all settings
```

### 2. App Launcher Activity (MISSING)

Currently there's NO launcher activity — user installs the app and sees nothing. Need:
- A main activity that opens when user taps the app icon
- Shows setup wizard: "Enable Banglu Keyboard" → "Set as default" → "Done!"
- Also accessible from notification/settings gear

```
MainActivity (Launcher)
├── Welcome screen with app logo
├── Step 1: "Enable Banglu Keyboard" → opens system keyboard settings
├── Step 2: "Set as Default Keyboard" → opens input method picker
├── Step 3: "You're all set! Try typing Bengali"
├── Quick test area (text field to try typing)
└── Link to Settings
```

### 3. App Icon (Currently placeholder solid color)

Need a proper app icon:
- Adaptive icon for Android 8+
- "বা" (Ba) letter in Banglu brand colors
- Foreground: Bengali letter on white
- Background: Brand color (blue #3D5AFE or green)

### 4. Privacy Policy (REQUIRED for Play Store)

Google Play requires a privacy policy for keyboard apps (handles user input).

Content:
- We do NOT collect typing data
- We do NOT send keystrokes to any server
- Dictionary data is stored locally only
- Learned words stay on device
- No analytics, no tracking
- No internet permission required (we only use VIBRATE)

### 5. AndroidManifest Updates

```xml
<!-- Add launcher activity -->
<activity
    android:name=".MainActivity"
    android:label="@string/app_name"
    android:exported="true"
    android:theme="@style/Theme.BangluKeyboard">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

### 6. Play Store Listing Assets

| Asset | Size | Status |
|-------|------|--------|
| App icon | 512x512 PNG | Need to create |
| Feature graphic | 1024x500 PNG | Need to create |
| Screenshots (phone) | min 2, 1080x1920+ | Take from device |
| Screenshots (tablet) | optional | Skip for now |
| Short description | Max 80 chars | Write |
| Full description | Max 4000 chars | Write |
| Privacy policy URL | Required | Need to host |
| Category | Tools / Productivity | Set |
| Content rating | Everyone | Set |

### 7. Build Configuration for Release

```kotlin
// android-keyboard/build.gradle.kts
android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Keep false for keyboard
            isShrinkResources = false
        }
    }

    // Signing config needed for release
    signingConfigs {
        create("release") {
            storeFile = file("banglu-keystore.jks")
            storePassword = "..."
            keyAlias = "banglu"
            keyPassword = "..."
        }
    }
}
```

---

## Implementation Plan

### Priority 1: Settings Activity (2 hours)
- Rewrite SettingsActivity with Jetpack Compose
- All settings saved to SharedPreferences
- BangluIMEService reads preferences on start
- Settings actually control keyboard behavior

### Priority 2: Main/Launcher Activity (1.5 hours)
- Setup wizard with 3 steps
- Enable keyboard → Set default → Test typing
- Clean Material Design 3 UI

### Priority 3: App Icon (30 min)
- Create adaptive icon with "বা" letter
- Vector drawable for foreground
- Solid color background

### Priority 4: Release Build (1 hour)
- Create signing keystore
- Configure release build
- Generate signed APK/AAB
- Test release build on device

### Priority 5: Play Store Listing (1 hour)
- Take screenshots
- Write descriptions
- Host privacy policy (GitHub Pages or raw URL)
- Fill Play Store console form
