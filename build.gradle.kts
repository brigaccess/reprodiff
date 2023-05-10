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
    implementation("ch.qos.logback:logback-core:1.4.7")
    implementation("ch.qos.logback:logback-classic:1.4.7")
    implementation("commons-codec:commons-codec:1.15")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("org.apache.commons:commons-compress:1.23.0")
    implementation("org.apache.tika:tika-core:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.slf4j:slf4j-api:2.0.7")
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}