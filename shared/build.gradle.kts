import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.android.library")
}

kotlin {
    jvm {
        // S49: the desktop app bundles a Java 17 runtime (jpackage/Temurin 17).
        // Without this, the Gradle daemon's JBR 21 emits class-file 65 and the
        // packaged app dies with UnsupportedClassVersionError at first convert.
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    // S45: the web/extension target — same engine, compiled to JS. Kotlin/JS
    // (not Wasm) so old Android Chrome in BD still runs the converter page.
    js(IR) {
        moduleName = "banglu-engine"
        browser()
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // S48: desktop-grade JDBC store/loader (also reused by jvmTest)
            implementation("org.xerial:sqlite-jdbc:3.45.1.0")
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.xerial:sqlite-jdbc:3.45.1.0")
            }
        }
    }
}

android {
    namespace = "com.banglu.engine"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test>().configureEach {
    // The JVM engine parity suite loads the full Bengali validator dictionary,
    // extended phonetic dictionary, and bigram model from SQLite.
    maxHeapSize = "4g"
}
