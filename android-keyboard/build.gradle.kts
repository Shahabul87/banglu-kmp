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

    defaultConfig {
        applicationId = "com.banglu.banglu_type"
        minSdk = 24
        targetSdk = 36
        versionCode = 2026
        versionName = "1.4.0"
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
        buildConfig = true
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
