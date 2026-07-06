import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Load signing credentials from local.properties (gitignored)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.banglu.keyboard"
    compileSdk = 36
    dynamicFeatures += setOf(":android_account")

    defaultConfig {
        applicationId = "com.banglu.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 2046
        versionName = "1.5.9"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("BANGLU_STORE_FILE", "banglu-release.jks"))
            storePassword = localProps.getProperty("BANGLU_STORE_PASSWORD", "")
            keyAlias = "banglu"
            keyPassword = localProps.getProperty("BANGLU_KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // Release-grade performance (R8, no debuggable overhead) with the
        // DEBUG signature: installs OVER the daily debug build on the dev
        // phone without uninstalling, so learned words survive. Compose runs
        // 2-4x faster than the debug variant — this is what typing-feel
        // testing must use; never judge smoothness on the debug build.
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui) {
        exclude(group = "androidx.activity", module = "activity-compose")
    }
    implementation(libs.compose.foundation) {
        exclude(group = "androidx.activity", module = "activity-compose")
    }
    implementation(libs.compose.material3) {
        exclude(group = "androidx.activity", module = "activity-compose")
    }
    implementation(libs.activity.ktx)
    implementation(libs.lifecycle.runtime)
    debugImplementation(libs.compose.ui.tooling) {
        exclude(group = "androidx.activity", module = "activity-compose")
    }
}

val verifyImePrivacyBoundary by tasks.registering {
    group = "verification"
    description = "Ensures IME hot-path code stays offline and account/billing code stays out of the keyboard process."

    val keyboardSourceDir = layout.projectDirectory.dir("src/main/kotlin/com/banglu/keyboard")
    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val accountSourceDir = rootProject.layout.projectDirectory.dir("android_account/src/main/kotlin/com/banglu/keyboard")
    val accountManifestFile = rootProject.layout.projectDirectory.file("android_account/src/main/AndroidManifest.xml")

    inputs.dir(keyboardSourceDir)
    inputs.dir(accountSourceDir)
    inputs.file(manifestFile)
    inputs.file(accountManifestFile)

    doLast {
        val hotPathFiles = listOf(
            "BangluIMEService.kt",
            "ComposeKeyboardView.kt",
            "KeyboardState.kt",
            "AndroidDictionaryLoader.kt",
            "EmojiData.kt",
            "ReactionGifFactory.kt",
            "BangluComposeHost.kt",
            "BangluProcessGuards.kt"
        )
        val forbiddenTokens = listOf(
            "AuthSessionStore(",
            "MobileAuthClient(",
            "BackendSyncClient(",
            "BillingEntitlementManager(",
            "CredentialManager",
            "BillingClient",
            "HttpURLConnection",
            "java.net.URL",
            "URL("
        )

        val violations = mutableListOf<String>()
        hotPathFiles.forEach { relativePath ->
            val file = keyboardSourceDir.file(relativePath).asFile
            if (!file.exists()) return@forEach
            val text = file.readText()
            forbiddenTokens.forEach { token ->
                if (text.contains(token)) {
                    violations += "$relativePath must not reference `$token`"
                }
            }
        }

        listOf(
            "AuthSessionStore.kt",
            "MobileAuthClient.kt",
            "BackendSyncClient.kt",
            "BillingEntitlementManager.kt"
        ).forEach { relativePath ->
            val file = accountSourceDir.file(relativePath).asFile
            if (!file.exists() || !file.readText().contains("BangluProcessGuards.requireUiProcess")) {
                violations += "$relativePath must call BangluProcessGuards.requireUiProcess"
            }
        }

        val manifest = manifestFile.asFile.readText()
        val imeService = Regex(
            "<service[\\s\\S]*?android:name=\"\\.BangluIMEService\"[\\s\\S]*?(?:</service>|/>)"
        ).find(manifest)?.value
        if (imeService == null) {
            violations += "AndroidManifest.xml must declare .BangluIMEService"
        } else if (imeService.contains("android:process=")) {
            violations += ".BangluIMEService must stay in the default app process"
        }

        listOf("MainActivity", "SettingsActivity", "VoicePermissionActivity", "TutorialActivity")
            .forEach { activity ->
                val block = Regex(
                    "<activity[\\s\\S]*?android:name=\"\\.$activity\"[\\s\\S]*?(?:</activity>|/>)"
                ).find(manifest)?.value
                if (block == null) {
                    violations += "AndroidManifest.xml must declare .$activity"
                } else if (!block.contains("android:process=\":ui\"")) {
                    violations += ".$activity must run in android:process=\":ui\""
                }
            }

        val accountManifest = accountManifestFile.asFile.readText()
        val accountActivityBlock = Regex(
            "<activity[\\s\\S]*?android:name=\"com\\.banglu\\.keyboard\\.AccountActivity\"[\\s\\S]*?(?:</activity>|/>)"
        ).find(accountManifest)?.value
        if (accountActivityBlock == null) {
            violations += "android_account manifest must declare com.banglu.keyboard.AccountActivity"
        } else if (!accountActivityBlock.contains("android:process=\":ui\"")) {
            violations += "AccountActivity must run in android:process=\":ui\""
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "IME privacy boundary failed:\n" + violations.joinToString(separator = "\n") { "- $it" }
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyImePrivacyBoundary)
}
