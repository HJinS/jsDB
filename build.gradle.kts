import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    val kotlinVersion = "2.1.20"
    kotlin("jvm") version kotlinVersion
    application
}

java {
    toolchain{
        languageVersion.set(JavaLanguageVersion.of(22))
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
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter", "junit-jupiter", "5.6.2")
    testImplementation("org.assertj", "assertj-core", "3.16.1")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_22)
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