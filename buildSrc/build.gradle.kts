plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle.plugin.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.gradle.plugin.jooq)
    implementation(libs.jooq.codegen)
    implementation(libs.postgresql)
}
