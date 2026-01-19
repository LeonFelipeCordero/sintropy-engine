plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.allopen") version "2.2.20"
    id("io.quarkus")
    id("org.jooq.jooq-codegen-gradle") version "3.19.26"
    id("org.flywaydb.flyway") version "11.15.0"
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
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkiverse.micrometer.registry:quarkus-micrometer-registry-otlp:3.4.1")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-vertx")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-websockets-next")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-scheduler")

    implementation("io.smallrye.reactive:mutiny-kotlin:3.0.1")

    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql:11.15.0")
    implementation("org.postgresql:postgresql:42.7.3")
    jooqCodegen("org.postgresql:postgresql:42.7.3")
    implementation("io.quarkiverse.jooq:quarkus-jooq:2.1.0")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:kotlin-extensions")
    testImplementation("org.assertj:assertj-guava:3.26.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
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
//    dependsOn(tasks.named("jooqCodegen"))
}

// Flyway migrations in command line
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.15.0")
    }
}
flyway {
    url = "jdbc:postgresql://localhost:5432/postgres"
    user = "postgres"
    password = "postgres"
    locations = arrayOf("classpath:db/migration")
    driver = "org.postgresql.Driver"
}
