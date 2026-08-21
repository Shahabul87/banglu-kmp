pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "banglu-kmp"
include(":shared")
include(":desktop-app")
include(":windows-ime")
include(":dictionary-compiler")
include(":android-keyboard")
include(":android_account")
