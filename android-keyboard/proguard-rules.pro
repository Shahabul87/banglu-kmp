# Banglu release R8 rules.
# Keep classes referenced from AndroidManifest and IME XML stable across minification.
-keep class com.banglu.keyboard.BangluIMEService { *; }
-keep class com.banglu.keyboard.MainActivity { *; }
-keep class com.banglu.keyboard.SettingsActivity { *; }
-keep class com.banglu.keyboard.AccountActivity { *; }
-keep class com.banglu.keyboard.TutorialActivity { *; }
-keep class com.banglu.keyboard.VoicePermissionActivity { *; }

# Keep Kotlin metadata for Compose/credentials reflection edge cases.
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,Signature,InnerClasses,EnclosingMethod

# S108: strip verbose/debug/info logging from release bytecode entirely.
# Warning/error logs are kept deliberately (S70/S77 field diagnostics) and
# carry no typed text — enforced by code review + the privacy-boundary task.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
