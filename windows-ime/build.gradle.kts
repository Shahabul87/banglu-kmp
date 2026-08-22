import java.io.File as JFile
import java.net.URLClassLoader
import java.util.Properties

plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * THE single source of truth for this app's version.
 *
 * It stamps three things that must never disagree: the MSI's `packageVersion`
 * (what Windows Installer compares to decide "is this an upgrade?"), the
 * generated `banglu-typer-version.txt` resource the running app reads to know
 * what it is, and the `version` field of the update manifest the release
 * workflow publishes. An update that does not increment this is invisible to
 * both Windows Installer and the in-app updater, so BUMP IT on every shippable
 * change (same discipline as android-keyboard's versionCode/versionName).
 */
val bangluTyperVersion = "1.0.1"

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    testImplementation(kotlin("test"))
}

/**
 * The MSI ships no HTTP stack it does not use.
 *
 * `:shared`'s jvmMain declares `ktor-client-okhttp` transitively for other
 * consumers; nothing in `shared/src` imports `io.ktor` or `okhttp` (verified by
 * grep), and this module's only network code is [java.net.http.HttpClient] from
 * the JDK. Dragging ktor + okhttp + okio (13 jars, ~3.1 MB) into an app whose
 * whole promise is "your typing never leaves your machine" would put three
 * general-purpose HTTP clients on the classpath that no code path can reach —
 * a claim nobody can audit by reading the code.
 *
 * Scoped to THIS project's configurations only: `:shared` and every other
 * consumer keep their dependencies untouched.
 */
configurations.configureEach {
    exclude(group = "io.ktor")
    exclude(group = "com.squareup.okhttp3")
    exclude(group = "com.squareup.okio")
}

/**
 * Writes [bangluTyperVersion] into a classpath resource so the running app can
 * read its own version without a hardcoded Kotlin constant that would silently
 * drift from `packageVersion`. `jpackage.app-version` (set in
 * `app/BangluTyper.cfg` by jpackage) is the authoritative answer for an
 * INSTALLED app; this resource is what makes a `./gradlew :windows-ime:run` dev
 * session — and the smoke workflow's app image — able to answer too.
 */
val generatedVersionDir = layout.buildDirectory.dir("generated/bangluVersion")
val generateVersionResource by tasks.registering {
    val outDir = generatedVersionDir
    val version = bangluTyperVersion
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("banglu-typer-version.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(version)
    }
}
sourceSets.named("main") { resources.srcDir(generatedVersionDir) }
tasks.named("processResources") { dependsOn(generateVersionResource) }

compose.desktop {
    application {
        mainClass = "com.banglu.winime.MainKt"
        // Packaging needs a full JDK with jpackage (the IDE JBR lacks it).
        // Same fallback chain as desktop-app/build.gradle.kts (S128 lesson):
        // explicit BANGLU_JDK, then the known dev JDK if it exists on THIS
        // machine, then the running JVM — a bare developer path breaks
        // clean-clone builds.
        javaHome = System.getenv("BANGLU_JDK")
            ?: "/Users/mdshahabulalam/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.17+10/Contents/Home"
                .takeIf { JFile(it).exists() }
            ?: System.getProperty("java.home")
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            // jpackage builds a MINIMAL runtime; Compose's default module set
            // omits these. jdeps-verified requirements of our jars — without
            // java.sql the installed app dies at first convert (JDBC store).
            // java.net.http carries HttpClient, the JDK's own HTTP stack and
            // the ONLY networking this app has (update/Updater.kt). Without it
            // the installed app dies with NoClassDefFoundError the first time
            // it checks for an update — a jpackage runtime contains exactly the
            // modules named here.
            modules(
                "java.sql", "java.instrument", "java.management",
                "jdk.unsupported", "java.net.http",
            )
            packageName = "BangluTyper"
            packageVersion = bangluTyperVersion
            description = "Type Bangla anywhere on Windows"
            vendor = "Banglu"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
            // resources/common/dictionary.sqlite is bundled into the MSI
            // (CI copies it; gitignored — 143MB). LICENSES.md in the same
            // dir IS committed — see verifyPackagedDictionary below.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            windows {
                iconFile.set(project.layout.projectDirectory.file("icons/banglu.ico"))
                menu = true; shortcut = true
                // ── THE UPGRADE CODE — NEVER CHANGE THIS VALUE ──────────────
                // Windows Installer identifies a *product family* by its
                // UpgradeCode. jpackage generates a RANDOM one per build when
                // none is given, so every MSI we shipped was a different
                // product: installing 1.0.1 next to 1.0.0 left both in Add/
                // Remove Programs, the new one could not replace files the
                // running old one held open, and Windows Installer's answer to
                // a locked file is to schedule the replacement for next boot —
                // which is exactly the "restart your PC" prompt users hit.
                // With a stable UpgradeCode the FindRelatedProducts/
                // RemoveExistingProducts sequence runs instead: the old version
                // is uninstalled and the new one installed in one transaction,
                // in place. Change this value and every already-installed copy
                // becomes un-upgradable forever.
                upgradeUuid = "32c380f5-14c1-4cf8-b3fb-cca107852703"
                // Per-user install (%LOCALAPPDATA%), not Program Files. Two
                // reasons, both about the update story: an in-place upgrade of
                // a per-machine install needs elevation, so every silent update
                // would raise a UAC prompt; and files under Program Files held
                // open by the running app are precisely what pushes Windows
                // Installer into reboot-scheduling. ONE-TIME COST: an existing
                // per-machine 1.0.0 is a different product (no UpgradeCode at
                // all) and is not replaced — it must be uninstalled by hand
                // once. From 1.0.1 onward upgrades are in place and silent.
                perUserInstall = true
                // -PbangluConsole=true builds a console launcher. A GUI-subsystem
                // jpackage app reports every startup failure as the same opaque
                // "Failed to launch JVM" box with the real cause discarded; the
                // console build prints it. Ships false — users must never see a
                // terminal window behind the tray app.
                console = project.findProperty("bangluConsole") == "true"
            }
        }
    }
}

kotlin { jvmToolchain(17) }
tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "4g"   // full validator + bigrams, same as :shared jvmTest
}

// S128 pattern (see desktop-app/build.gradle.kts): packaging shipped
// whatever dictionary.sqlite sat in resources/common — a stale version
// passes every test (tests read the repo-root db) and then gets REJECTED
// by the runtime version gate inside the installed app, silently degrading
// it to seed mode. This task makes the version match a PACKAGING
// dependency: it reads the db's metadata version with the project's own
// sqlite-jdbc and compares it to DictionaryVersion.REQUIRED parsed from
// the shared source of truth.
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
                "cp <repo-root>/dictionary.sqlite windows-ime/resources/common/dictionary.sqlite"
        }
        logger.lifecycle("packaged dictionary version OK: $actual")
        // The resources dir's dictionary.sqlite is gitignored (143MB), but
        // LICENSES.md there IS committed (dataset notices + font OFL) — no
        // copy-on-package needed, unlike desktop-app which regenerates its
        // notices file from a different source of truth at every build.
    }
}

// Every packaging/distribution task must refuse a stale dictionary.
tasks.matching { it.name.startsWith("package") || it.name.startsWith("createDistributable") }
    .configureEach { dependsOn(verifyPackagedDictionary) }

val verifyHookIsolation by tasks.registering {
    val srcRoot = project.layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(srcRoot)
    doLast {
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("/hook/") }
            .filter { it.readText().contains("import com.sun.jna") }
            .map { it.relativeTo(srcRoot).path }.toList()
        require(offenders.isEmpty()) {
            "JNA imports outside hook/ (spec isolation law): $offenders"
        }
    }
}
tasks.named("check") { dependsOn(verifyHookIsolation) }

/**
 * The privacy boundary, enforced the same way the JNA one is.
 *
 * This module's rule used to be "no network capability anywhere". The in-app
 * updater is a deliberate, narrow exception, and "narrow" has to be a property
 * of the source tree rather than a promise in a README:
 *
 *  1. Only `update/` may name an HTTP client. Every other file — the hook, the
 *     controller, the composer, storage, the UI — stays structurally unable to
 *     open a socket, so nothing derived from a keystroke has anywhere to go.
 *  2. `update/` may not reach the engine, the composer, the controller or
 *     storage. The one file that CAN talk to the network cannot see a single
 *     character the user typed.
 *
 * Together those two greps are the actual guarantee behind the README's
 * privacy claim.
 */
val verifyUpdaterIsolation by tasks.registering {
    val srcRoot = project.layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(srcRoot)
    doLast {
        val networkTokens = listOf("java.net.http", "HttpClient", "URLConnection", "java.net.Socket")
        val typingTokens = listOf(
            "com.banglu.engine", "com.banglu.winime.composer", "SmartEngine",
            "WinStorage", "Controller", "Composer",
        )
        val files = srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val networkOutside = files
            .filter { !it.path.contains("/update/") }
            .filter { f -> networkTokens.any { f.readText().contains(it) } }
            .map { it.relativeTo(srcRoot).path }
        require(networkOutside.isEmpty()) {
            "HTTP client references outside update/ (privacy boundary): $networkOutside"
        }
        val typingInside = files
            .filter { it.path.contains("/update/") }
            .filter { f -> typingTokens.any { f.readText().contains(it) } }
            .map { it.relativeTo(srcRoot).path }
        require(typingInside.isEmpty()) {
            "update/ must not reach the engine/composer/controller/storage: $typingInside"
        }
    }
}
tasks.named("check") { dependsOn(verifyUpdaterIsolation) }

/**
 * Renders the control window on the developer's machine so its design can be
 * SEEN rather than guessed at. The app proper is Win32-only, but the window is
 * pure Compose with no JNA in its tree, so it renders anywhere.
 *
 *   ./gradlew :windows-ime:previewControlWindow
 *   ./gradlew :windows-ime:previewControlWindow -Dbanglu.preview.mode=update
 */
val previewControlWindow by tasks.registering(JavaExec::class) {
    group = "banglu"
    description = "Open the control window for design review (dev machine, no hook)"
    mainClass.set("com.banglu.winime.ui.DesignPreviewKt")
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("banglu.preview.mode", providers.systemProperty("banglu.preview.mode").getOrElse("bangla"))
}
