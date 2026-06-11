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
