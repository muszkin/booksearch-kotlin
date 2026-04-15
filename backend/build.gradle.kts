plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.openapi.generator)
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
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

val jooqCodegenConfig by configurations.creating

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.jooq)
    implementation(libs.liquibase.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.bcrypt)

    implementation(libs.jsoup)
    implementation(libs.epub4j.core)
    implementation(libs.jakarta.mail)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.impersonator.okhttp)

    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    jooqCodegenConfig(libs.jooq.codegen)
    jooqCodegenConfig(libs.jooq.meta)
    jooqCodegenConfig(libs.jooq)
    jooqCodegenConfig(libs.sqlite.jdbc)
    jooqCodegenConfig(libs.liquibase.core)
    jooqCodegenConfig(libs.picocli)
    jooqCodegenConfig(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// =============================================================================
// OpenAPI Generator Configuration
// =============================================================================

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("${projectDir}/src/main/resources/openapi/api.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/openapi")
    modelPackage.set("pl.fairydeck.booksearch.models")
    globalProperties.set(mapOf(
        "models" to "",
        "modelDocs" to "false",
        "modelTests" to "false"
    ))
    configOptions.set(mapOf(
        "serializationLibrary" to "kotlinx_serialization",
        "enumPropertyNaming" to "UPPERCASE",
        "sourceFolder" to "src/main/kotlin"
    ))
}

// =============================================================================
// Liquibase + JOOQ Codegen Pipeline
// =============================================================================

val jooqCodegenDb = layout.buildDirectory.file("tmp/jooq-codegen.db").get().asFile
val jooqOutputDir = layout.buildDirectory.dir("generated/jooq/src/main/kotlin").get().asFile

val liquibaseMigrate by tasks.registering(JavaExec::class) {
    description = "Run Liquibase migrations on temp SQLite for JOOQ codegen"
    dependsOn("openApiGenerate")

    inputs.files(fileTree("src/main/resources/db/changelog"))
    outputs.file(jooqCodegenDb)

    mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
    classpath = jooqCodegenConfig

    doFirst {
        jooqCodegenDb.parentFile.mkdirs()
        jooqCodegenDb.delete()
    }

    args(
        "--changelog-file=db/changelog/changelog.yml",
        "--url=jdbc:sqlite:${jooqCodegenDb.absolutePath}",
        "--search-path=${projectDir}/src/main/resources",
        "update"
    )
}

val jooqCodegen by tasks.registering(JavaExec::class) {
    description = "Generate JOOQ classes from temp SQLite database"
    dependsOn(liquibaseMigrate)

    inputs.file(jooqCodegenDb)
    outputs.dir(jooqOutputDir)

    mainClass.set("org.jooq.codegen.GenerationTool")
    classpath = jooqCodegenConfig

    doFirst {
        jooqOutputDir.mkdirs()

        val configXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.21.0.xsd">
                <jdbc>
                    <driver>org.sqlite.JDBC</driver>
                    <url>jdbc:sqlite:${jooqCodegenDb.absolutePath}</url>
                </jdbc>
                <generator>
                    <name>org.jooq.codegen.KotlinGenerator</name>
                    <database>
                        <name>org.jooq.meta.sqlite.SQLiteDatabase</name>
                        <includes>users|refresh_tokens|password_reset_tokens|system_config|books|user_library|mirrors|download_jobs|user_settings|deliveries|activity_logs|request_logs</includes>
                        <excludes>DATABASECHANGELOG|DATABASECHANGELOGLOCK</excludes>
                    </database>
                    <generate>
                        <deprecated>false</deprecated>
                        <records>true</records>
                        <pojos>false</pojos>
                        <fluentSetters>true</fluentSetters>
                    </generate>
                    <target>
                        <packageName>pl.fairydeck.booksearch.jooq.generated</packageName>
                        <directory>${jooqOutputDir.absolutePath}</directory>
                    </target>
                </generator>
            </configuration>
        """.trimIndent()

        val configFile = layout.buildDirectory.file("tmp/jooq-codegen-config.xml").get().asFile
        configFile.parentFile.mkdirs()
        configFile.writeText(configXml)

        args(configFile.absolutePath)
    }
}

// Add generated sources to main source set
sourceSets {
    main {
        kotlin {
            srcDir("${layout.buildDirectory.get()}/generated/openapi/src/main/kotlin")
            srcDir(jooqOutputDir)
        }
    }
}

// =============================================================================
// Task Ordering
// =============================================================================

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate", jooqCodegen)
}

// =============================================================================
// Frontend Build
// =============================================================================

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
