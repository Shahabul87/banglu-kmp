plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.banglu.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.banglu.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("banglu-release.jks")
            storePassword = "banglu2026"
            keyAlias = "banglu"
            keyPassword = "banglu2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    debugImplementation(libs.compose.ui.tooling)
}
