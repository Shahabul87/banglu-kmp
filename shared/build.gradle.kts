import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
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
    maxHeapSize = "2g"
}
