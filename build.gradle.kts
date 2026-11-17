plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.application)
    alias(libs.plugins.javafx)
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
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "midituutti.MidiTuuttiKt"
}

tasks.test {
    useJUnitPlatform()
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
