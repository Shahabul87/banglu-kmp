import java.io.File as JFile
import java.net.URLClassLoader
import java.util.Properties

plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    // Storage.kt/DraftStore.kt use @Serializable; without this plugin the
    // serializers are never generated and learned.json writes throw at runtime.
    alias(libs.plugins.kotlin.serialization)
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
    // Global hotkey via OS hotkey APIs (Carbon RegisterEventHotKey on macOS,
    // RegisterHotKey on Windows, X11 on Linux) — NO permissions needed and no
    // keyboard event tap. JNativeHook was abandoned: it required Accessibility
    // + Input Monitoring, false-registered without them, and crashed natively
    // (CFMachPortInvalidate in destroy_event_runloop_info) on permission
    // changes. JNA 5.14 pinned: jkeymaster's transitive JNA predates aarch64.
    implementation("com.github.tulskiy:jkeymaster:1.3")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.banglu.desktop.MainKt"
        // Packaging needs a full JDK with jpackage (the IDE JBR lacks it).
        // Uses the Gradle-provisioned Temurin 17 toolchain; CI sets its own.
        // S128 (production audit): the bare developer-path fallback broke
        // clean-clone builds. Order: explicit BANGLU_JDK, then the known dev
        // JDK if it exists on THIS machine (the daemon's JBR lacks jpackage —
        // see the packaging landmines in CLAUDE.md), then the running JVM.
        javaHome = System.getenv("BANGLU_JDK")
            ?: "/Users/mdshahabulalam/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.17+10/Contents/Home"
                .takeIf { JFile(it).exists() }
            ?: System.getProperty("java.home")
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            // jpackage builds a MINIMAL runtime; Compose's default module set
            // omits these. jdeps-verified requirements of our jars — without
            // java.sql the installed app dies at first convert (JDBC store).
            modules("java.sql", "java.instrument", "java.management", "jdk.unsupported")
            packageName = "Banglu"
            packageVersion = "1.3.23"
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

tasks.withType<Test> { useJUnitPlatform() }

// S128 (production audit): packaging shipped whatever dictionary.sqlite sat
// in resources/common — a stale version passes every test (tests read the
// repo-root db) and then gets REJECTED by the runtime version gate inside
// the installed app, silently degrading it to seed mode. This task makes
// the version match a PACKAGING dependency: it reads the db's metadata
// version with the project's own sqlite-jdbc and compares it to
// DictionaryVersion.REQUIRED parsed from the shared source of truth.
val verifyPackagedDictionary by tasks.registering {
    val dbFile = project.layout.projectDirectory.file("resources/common/dictionary.sqlite").asFile
    val versionSource = rootProject.layout.projectDirectory
        .file("shared/src/commonMain/kotlin/com/banglu/engine/DictionaryVersion.kt").asFile
    inputs.file(versionSource)
    outputs.upToDateWhen { false }
    doLast {
        require(dbFile.exists()) {
            "resources/common/dictionary.sqlite is missing — copy the current repo-root dictionary.sqlite before packaging"
        }
        val required = Regex("""REQUIRED\s*=\s*"([^"]+)"""")
            .find(versionSource.readText())?.groupValues?.get(1)
            ?: error("Could not parse DictionaryVersion.REQUIRED")
        val jars = configurations.getByName("runtimeClasspath").files
            .filter { it.name.contains("sqlite-jdbc") || it.name.contains("slf4j") }
        require(jars.any { it.name.contains("sqlite-jdbc") }) { "sqlite-jdbc not on runtimeClasspath" }
        val loader = URLClassLoader(jars.map { it.toURI().toURL() }.toTypedArray())
        val driver = Class.forName("org.sqlite.JDBC", true, loader)
            .getDeclaredConstructor().newInstance() as java.sql.Driver
        val conn = driver.connect("jdbc:sqlite:${dbFile.absolutePath}", Properties())
        val actual = conn.use { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT value FROM metadata WHERE key='version'").use { rs ->
                    if (rs.next()) rs.getString(1) else "missing"
                }
            }
        }
        require(actual == required) {
            "Packaged dictionary version $actual != engine REQUIRED $required — " +
                "the installed app would reject it at runtime and fall back to seed mode. " +
                "cp <repo-root>/dictionary.sqlite desktop-app/resources/common/dictionary.sqlite"
        }
        logger.lifecycle("packaged dictionary version OK: $actual")
        // S128: the resources dir is gitignored (150MB dictionary), so the
        // third-party notices copy would silently go missing on a clean
        // clone — refresh it from the source of truth at every packaging.
        val noticesSrc = rootProject.layout.projectDirectory
            .file("dictionary-compiler/data/LICENSES.md").asFile
        noticesSrc.copyTo(JFile(dbFile.parentFile, "LICENSES.md"), overwrite = true)
    }
}

// Every packaging/distribution task must refuse a stale dictionary.
tasks.matching { it.name.startsWith("package") || it.name.startsWith("createDistributable") }
    .configureEach { dependsOn(verifyPackagedDictionary) }
