import java.util.Properties

plugins {
    id("com.android.dynamic-feature")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.banglu.keyboard.account"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        buildConfigField(
            "String",
            "BILLING_SUBSCRIPTION_PRODUCT_ID",
            "\"${localProps.getProperty("BILLING_SUBSCRIPTION_PRODUCT_ID", "banglu_pro_monthly")}\""
        )
        buildConfigField(
            "String",
            "BANGLU_API_BASE_URL",
            "\"${localProps.getProperty("BANGLU_API_BASE_URL", "https://bangluweb-production.up.railway.app")}\""
        )
        buildConfigField(
            "String",
            "BANGLU_GOOGLE_WEB_CLIENT_ID",
            "\"${localProps.getProperty("GOOGLE_CLIENT_ID", localProps.getProperty("BANGLU_GOOGLE_WEB_CLIENT_ID", System.getenv("GOOGLE_CLIENT_ID") ?: ""))}\""
        )
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
    implementation(project(":android-keyboard"))
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)
    implementation(libs.billing)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

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
}
