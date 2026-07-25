buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.4.0")
        classpath("org.postgresql:postgresql:42.7.11")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jooq.codegen)
    alias(libs.plugins.flyway)
}

group = "com.github.nexters"
version = "0.0.1-SNAPSHOT"
extra["jooq.version"] = libs.versions.jooq.get()

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
    sourceSets.getByName("main") {
        kotlin.srcDir("src/generated/jooq")
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.spring.cloud.get()}")
    }
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.jooq.kotlin)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    developmentOnly(libs.spring.dotenv)
    developmentOnly(libs.spring.boot.docker.compose)
    jooqCodegen(libs.postgresql)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.extensions.spring)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val dotenv = file(".env").takeIf { it.exists() }
    ?.readLines()
    ?.filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("=") }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    ?: emptyMap()

fun env(key: String, default: String): String =
    providers.environmentVariable(key).orNull ?: dotenv[key] ?: default

val dbUrl = "jdbc:postgresql://${env("POSTGRES_HOST", "localhost")}:${env("POSTGRES_PORT", "5432")}/${env("POSTGRES_DB", "ppotto")}"
val dbUser = env("POSTGRES_USER", "ppotto")
val dbPassword = env("POSTGRES_PASSWORD", "ppotto")

flyway {
    url = dbUrl
    user = dbUser
    password = dbPassword
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = dbUrl
            user = dbUser
            password = dbPassword
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
            }
            generate {
                isPojos = true
                isPojosAsKotlinDataClasses = true
                isImmutablePojos = true
                isRecords = true
                isKotlinNotNullRecordAttributes = true
                isKotlinNotNullPojoAttributes = true
                isKotlinDefaultedNullablePojoAttributes = true
                isKotlinDefaultedNullableRecordAttributes = true
                isImplicitJoinPathsAsKotlinProperties = true
                isJavaTimeTypes = true
                isDaos = false
                isFluentSetters = false
                isDeprecated = false
                isGeneratedAnnotationDate = false
            }
            target {
                packageName = "com.github.nexters.ppotto.jooq"
                directory = "src/generated/jooq"
            }
        }
    }
}

tasks.named("jooqCodegen") {
    inputs.files(fileTree("src/main/resources/db/migration"))
        .withPropertyName("migrations")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.jar {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
}
