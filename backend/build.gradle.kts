plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

repositories {
    mavenCentral()
}

group = "pl.fairydeck.booksearch"
version = "0.1.0"

application {
    mainClass.set("pl.fairydeck.booksearch.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("booksearch-v2.jar")
    }
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.host.common)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.jooq)
    implementation(libs.liquibase.core)
    implementation(libs.sqlite.jdbc)

    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val frontendDir = rootProject.layout.projectDirectory.dir("frontend")

val frontendInstall by tasks.registering(Exec::class) {
    workingDir = frontendDir.asFile
    commandLine("pnpm", "install")
}

val buildFrontend by tasks.registering(Exec::class) {
    dependsOn(frontendInstall)
    workingDir = frontendDir.asFile
    commandLine("pnpm", "build")
}

val copyFrontend by tasks.registering(Copy::class) {
    dependsOn(buildFrontend)
    from(frontendDir.dir("dist"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.processResources {
    dependsOn(copyFrontend)
}
