plugins {
    kotlin("jvm") version "1.8.0"
    application
}

group = "com.gmail.brigaccess"
version = "0.1"

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}