import java.io.File as JFile
import java.net.URLClassLoader
import java.util.Properties

plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

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
            modules("java.sql", "java.instrument", "java.management", "jdk.unsupported")
            packageName = "BangluTyper"
            packageVersion = "1.0.0"
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
