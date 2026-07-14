plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    // Provides Dispatchers.Main on desktop JVM (crash on first keystroke without it)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation(libs.kotlinx.serialization.json)
    // Global hotkey (system-wide) — macOS needs Accessibility permission
    implementation("com.github.kwhat:jnativehook:2.2.2")
}

compose.desktop {
    application {
        mainClass = "com.banglu.desktop.MainKt"
        // Packaging needs a full JDK with jpackage (the IDE JBR lacks it).
        // Uses the Gradle-provisioned Temurin 17 toolchain; CI sets its own.
        javaHome = System.getenv("BANGLU_JDK") ?: "/Users/mdshahabulalam/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.17+10/Contents/Home"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "Banglu"
            packageVersion = "1.0.0"
            description = "Type Bangla anywhere with lowercase English letters"
            vendor = "Banglu"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
            // resources/common/dictionary.sqlite is bundled into every
            // installer (prepare-dist.sh copies it; gitignored — 143MB).
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            macOS { iconFile.set(project.layout.projectDirectory.file("icons/banglu.icns")) }
            windows {
                iconFile.set(project.layout.projectDirectory.file("icons/banglu.ico"))
                menu = true; shortcut = true
            }
            linux { iconFile.set(project.layout.projectDirectory.file("icons/banglu.png")) }
        }
    }
}

kotlin { jvmToolchain(17) }
