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
    // S136 (F-011): the account/billing feature is NOT part of the launch
    // build — it is hidden, its permissions are stripped, and shipping its
    // auth/billing bytecode fused install-time gained nothing but review
    // surface. Opt back in with -PbangluAccount=true when the feature ships.
    if (project.findProperty("bangluAccount") == "true") {
        dynamicFeatures += setOf(":android_account")
    }

    defaultConfig {
        applicationId = "com.banglu.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 2162
        versionName = "1.5.125"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // S166b: the AAB's only native code is androidx.graphics.path, a
            // Google prebuilt that ships FULLY STRIPPED (llvm-nm: "no
            // symbols") — so Play's "upload debug symbols" warning cannot be
            // satisfied for it and is safe to ignore. This setting stays so
            // any FUTURE native dependency that does carry a symbol table
            // gets packed into the bundle automatically.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
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
    // S169: installs the shipped baseline profile (src/main/baseline-prof.txt) on
    // first launch so ART AOT-compiles the typing path instead of interpreting it
    // for the first day. Perf-only; no behaviour.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

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

    // S55: pure-Kotlin unit tests (VoiceSessionPolicy) — no Robolectric/
    // Android runtime needed, JUnit4 to match AGP's default unit-test runner.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)

    // S136 (F-006): on-device instrumentation — the multiprocess erase
    // provider and the accessibility tree are outside the JVM test wall.
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}

val verifyImePrivacyBoundary by tasks.registering {
    group = "verification"
    description = "Ensures IME hot-path code stays offline and account/billing code stays out of the keyboard process."

    val keyboardSourceDir = layout.projectDirectory.dir("src/main/kotlin/com/banglu/keyboard")
    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val accountSourceDir = rootProject.layout.projectDirectory.dir("android_account/src/main/kotlin/com/banglu/keyboard")
    val accountManifestFile = rootProject.layout.projectDirectory.file("android_account/src/main/AndroidManifest.xml")
    // S108: the shared engine runs inside the IME process too — a network
    // dependency added there would be invisible to a keyboard-only scan.
    val sharedCommonDir = rootProject.layout.projectDirectory.dir("shared/src/commonMain/kotlin")
    val sharedAndroidDir = rootProject.layout.projectDirectory.dir("shared/src/androidMain/kotlin")

    inputs.dir(keyboardSourceDir)
    inputs.dir(accountSourceDir)
    inputs.dir(sharedCommonDir)
    inputs.file(manifestFile)
    inputs.file(accountManifestFile)

    doLast {
        // S108: renamed from a 9-file whitelist to "every file in the IME
        // process package" — the whitelist silently skipped renamed files and
        // never saw new ones. These named files must still EXIST so a rename
        // is caught instead of un-scanned.
        val mustExistHotPathFiles = listOf(
            "BangluIMEService.kt",
            "ComposeKeyboardView.kt",
            "KeyboardState.kt",
            "AndroidDictionaryLoader.kt",
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
            "URL(",
            // S108: whole client families, not just the two classes the
            // account module happens to use today.
            "okhttp",
            "io.ktor",
            "HttpClient(",
            "java.net.Socket",
            "InetAddress",
            "URLConnection"
        )

        val violations = mutableListOf<String>()
        mustExistHotPathFiles.forEach { relativePath ->
            if (!keyboardSourceDir.file(relativePath).asFile.exists()) {
                violations += "$relativePath is gone — update verifyImePrivacyBoundary if it was renamed"
            }
        }
        val scanRoots = listOf(
            keyboardSourceDir.asFile to "android-keyboard",
            sharedCommonDir.asFile to "shared/commonMain",
            sharedAndroidDir.asFile to "shared/androidMain"
        )
        scanRoots.forEach { (root, label) ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        violations += "$label/${file.name} must not reference `$token`"
                    }
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
