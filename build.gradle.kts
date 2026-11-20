plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.application)
    alias(libs.plugins.javafx)
    alias(libs.plugins.jlink)
}

repositories {
    mavenCentral()
}

defaultTasks("run")

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.java.midi.decoder)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testfx.core)
    testImplementation(libs.testfx.junit5)
    testImplementation(libs.openjfx.monocle)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "midituutti.MidiTuuttiKt"
}

// Headless JavaFX (Monocle + software pipeline) so UI tests run without a display.
// prism.text=t2k forces the pure-Java text rasterizer for deterministic glyphs.
fun Test.configureHeadlessUiTest(updateScreenshots: Boolean) {
    useJUnitPlatform()
    systemProperty("testfx.headless", "true")
    systemProperty("testfx.robot", "glass")
    systemProperty("prism.order", "sw")
    systemProperty("prism.text", "t2k")
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("java.awt.headless", "true")
    // no sound device in headless environments; the engine then uses a no-op MIDI receiver
    systemProperty("midituutti.noAudio", "true")
    systemProperty("midituutti.updateScreenshots", updateScreenshots.toString())
    // -PexcludeUiTests: CI matrix legs run engine tests only; UI screenshot tests run in the ui-test job
    if (project.hasProperty("excludeUiTests")) {
        filter { excludeTestsMatching("midituutti.ui.*") }
    }
}

tasks.test {
    configureHeadlessUiTest(updateScreenshots = System.getProperty("midituutti.updateScreenshots") == "true")
}

// ./gradlew updateScreenshots — capture golden screenshots instead of asserting on them
val updateScreenshots = tasks.register<Test>("updateScreenshots") {
    group = "verification"
    description = "Runs the UI tests in update mode: (re)creates the golden screenshots under src/test/resources."
    testClassesDirs = sourceSets.named("test").get().output.classesDirs
    classpath = sourceSets.named("test").get().runtimeClasspath
    configureHeadlessUiTest(updateScreenshots = true)
    shouldRunAfter(tasks.test)
}

tasks.compileJava {
    // module-info.java is compiled by javac, but the exported packages live in the Kotlin output
    val kotlinClasses = layout.buildDirectory.dir("classes/kotlin/main").get().asFile.absolutePath
    options.compilerArgs.addAll(listOf("--patch-module", "midituutti=$kotlinClasses"))
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.fxml")
}

jlink {
    options = listOf("--strip-debug", "--no-man-pages", "--no-header-files")
    launcher {
        name = "midi-tuutti"
    }
    addExtraDependencies("javafx")
    forceMerge("java-midi-decoder")
    forceMerge("kotlin")
    val osName = System.getProperty("os.name").lowercase()
    val zipName = when {
        "linux" in osName -> "midi-tuutti-linux.zip"
        "win" in osName -> "midi-tuutti-windows.zip"
        "mac" in osName -> "midi-tuutti-macos.zip"
        else -> throw GradleException("Unsupported operating system: $osName")
    }
    imageZip.set(layout.buildDirectory.file("image-zip/$zipName"))
}

// jlink's --strip-debug does not remove native .debuginfo files (libjvm.debuginfo alone is ~700 MB)
tasks.named("jlink") {
    doLast {
        delete(fileTree(layout.buildDirectory.dir("image")) { include("**/*.debuginfo") })
    }
}
