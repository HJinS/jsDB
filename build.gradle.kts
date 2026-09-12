import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    val kotlinVersion = "2.4.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    application
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

java {
    toolchain{
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

group = "js.db"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val kotestVersion = "6.2.5"

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.apache.logging.log4j:log4j-core:2.26.1")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")

    implementation("io.github.oshai:kotlin-logging:8.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.4.20")
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("kotest.framework.classpath.scanning.autoscan.disable", "true")
    systemProperty("kotest.framework.parallelism", (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1))
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    systemProperty("net.bytebuddy.experimental", "true")
    testLogging { events("passed", "failed"); showStandardStreams = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        // Optional: 자주 사용하는 설정 예시
        freeCompilerArgs.addAll(
            "-jvm-default=enable",
        )
    }
}



application {
    mainClass.set("MainKt")
}