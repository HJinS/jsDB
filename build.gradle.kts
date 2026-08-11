import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    val kotlinVersion = "2.1.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    application
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

java {
    toolchain{
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "js.db"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val kotestVersion = "5.9.1"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("org.apache.logging.log4j:log4j-core:2.26.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.0")

    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.2")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.2")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter", "junit-jupiter", "5.6.2")
    testImplementation("io.mockk:mockk:1.13.17")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-datatest:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("kotest.framework.classpath.scanning.autoscan.disable", "true")
    systemProperty("kotest.framework.parallelism", (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1))
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging { events("passed", "failed"); showStandardStreams = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Optional: 자주 사용하는 설정 예시
        freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            "-Xjvm-default=all",
        )
    }
}



application {
    mainClass.set("MainKt")
}