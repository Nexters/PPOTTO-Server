plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.flywaydb.flyway:org.flywaydb.flyway.gradle.plugin:${libs.versions.flyway.get()}")
    implementation("org.flywaydb:flyway-database-postgresql:${libs.versions.flyway.get()}")
    implementation("org.jooq.jooq-codegen-gradle:org.jooq.jooq-codegen-gradle.gradle.plugin:${libs.versions.jooq.get()}")
    implementation("org.jooq:jooq-codegen:${libs.versions.jooq.get()}")
    implementation("org.postgresql:postgresql:${libs.versions.postgresql.get()}")
}
