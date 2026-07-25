plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    id("ppotto.database")
}

group = "com.github.nexters"
version = "1.0.0-alpha"
extra["jooq.version"] =
    libs.versions.jooq
        .get()

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

dependencies {
    implementation(platform(libs.spring.cloud.dependencies))

    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jooq.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    runtimeOnly(libs.postgresql)

    developmentOnly(libs.spring.boot.docker.compose)
    developmentOnly(libs.spring.dotenv)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    testRuntimeOnly(libs.junit.platform.launcher)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
}

configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(
                libs.versions.detekt.kotlin
                    .get(),
            )
        }
    }
}

tasks.jar {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
}
