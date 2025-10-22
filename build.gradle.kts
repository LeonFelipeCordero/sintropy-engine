plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.liquibase.gradle") version "2.2.2"
    id("org.jetbrains.kotlin.plugin.jpa") version "2.0.20"
    id("org.jooq.jooq-codegen-gradle") version "3.19.26"
}

group = "com.ph"
version = "0.0.1-SNAPSHOT"
description = "syntropy-engine"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val coroutinesVersion = "1.10.2"
val jacksonVersion = "2.20.0"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")

    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    jooqCodegen("org.postgresql:postgresql")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    liquibaseRuntime("org.postgresql:postgresql")
    liquibaseRuntime("org.liquibase:liquibase-core")
    liquibaseRuntime("org.springframework.boot:spring-boot-starter-data-jpa")
    liquibaseRuntime("info.picocli:picocli:4.7.5")

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    testImplementation("io.rest-assured:kotlin-extensions")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${coroutinesVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
}

liquibase {
    activities.register("main") {
        this.arguments = mapOf(
            "url" to "jdbc:postgresql://localhost:5432/postgres",
            "username" to "postgres",
            "password" to "postgres",
            "driver" to "org.postgresql.Driver",
            "changelogFile" to "src/main/resources/db/changelog/db.changelog-master.yaml",
            "logLevel" to "info",
        )
    }
}

jooq {
    executions {
        configuration {
            jdbc {
                url = "jdbc:postgresql://localhost:5432/postgres"
                user = "postgres"
                password = "postgres"
                driver = "org.postgresql.Driver"
            }
            generator {
                database {
                    inputSchema = "public"
                    includes = ".*"
                    excludes = "databasechangelog|databasechangeloglock"
                }
                target {
                    packageName = "com.ph.syntropyengine.jooq.generated"
                    directory = "src/main/kotlin-gen"
                }
                generate {
                    isDaos = false
                    isPojos = true
                    isRecords = true
                }
            }
        }
    }
}

/**
 * This is too problematic as it requires to always have a database to be up to date,
 * Therefore is preferable to generate your entities manually
 */
tasks.named("compileKotlin") {
    dependsOn(tasks.named("jooqCodegen"))
}

//tasks.named("jooqCodegen") {
//    dependsOn(tasks.named("update"))
//}

//liquibase//tasks.named("dev") {
//    dependsOn(tasks.named("update"))
//}