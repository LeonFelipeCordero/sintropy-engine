plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.allopen") version "2.2.20"
    id("io.quarkus")
    id("org.jooq.jooq-codegen-gradle") version "3.19.26"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

val coroutinesVersion = "1.10.2"
dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-vertx")
    implementation("io.quarkus:quarkus-arc")

    implementation("org.postgresql:postgresql:42.7.3")
    jooqCodegen("org.postgresql:postgresql:42.7.3")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.quarkiverse.jooq:quarkus-jooq:2.1.0")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")

    testImplementation("org.testcontainers:testcontainers:1.20.2")
    testImplementation("org.testcontainers:postgresql:1.20.2")
    testImplementation("org.testcontainers:junit-jupiter:1.20.2")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-guava:3.26.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${coroutinesVersion}")
}

group = "com.ph"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
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
                    excludes = "flyway_schema_history"
                }
                target {
                    packageName = "com.ph.sintropyengine.jooq.generated"
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

tasks.named("compileKotlin") {
    dependsOn(tasks.named("jooqCodegen"))
}