plugins {
    id("org.flywaydb.flyway")
    id("org.jooq.jooq-codegen-gradle")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "jooqCodegen"(libs.findLibrary("postgresql").get())
}

val dotenv = file(".env").takeIf { it.exists() }
    ?.readLines()
    ?.filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("=") }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    ?: emptyMap()

fun env(key: String, default: String): String =
    providers.environmentVariable(key).orNull ?: dotenv[key] ?: default

val dbUrl = "jdbc:postgresql://${env("POSTGRES_HOST", "localhost")}:${env("POSTGRES_PORT", "54782")}/${env("POSTGRES_DB", "ppotto")}"
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

tasks.named("compileKotlin") {
    mustRunAfter(tasks.named("jooqCodegen"))
}
