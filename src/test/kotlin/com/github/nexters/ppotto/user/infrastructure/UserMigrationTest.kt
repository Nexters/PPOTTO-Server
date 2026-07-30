package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

class UserMigrationTest(
    dataSource: DataSource,
) : IntegrationTest({
        Given("최소 컬럼만 가진 기존 users 행이 있을 때") {
            val schema = "user_migration_${UUID.randomUUID().toString().replace("-", "")}"
            val legacyUserId = UUID.randomUUID()

            dataSource.connection.use { connection ->
                connection.createStatement().use {
                    it.execute("CREATE SCHEMA $schema")
                    it.execute("SET search_path TO $schema, public")
                }

                try {
                    connection.createStatement().use {
                        it.execute(
                            """
                            CREATE TABLE users (
                                id UUID PRIMARY KEY DEFAULT uuidv7(),
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
                            )
                            """.trimIndent(),
                        )
                    }
                    connection.prepareStatement("INSERT INTO users (id) VALUES (?)").use {
                        it.setObject(1, legacyUserId)
                        it.executeUpdate()
                    }

                    When("사용자 계정 스키마 확장 마이그레이션을 적용하면") {
                        ScriptUtils.executeSqlScript(
                            connection,
                            ClassPathResource("db/migration/V20260730112020__extend_users_for_social_accounts.sql"),
                        )

                        Then("기존 행을 삭제하거나 가짜 소셜 정보로 채우지 않고 보존한다") {
                            connection
                                .prepareStatement(
                                    """
                                    SELECT provider, provider_user_id, email
                                    FROM users
                                    WHERE id = ?
                                    """.trimIndent(),
                                ).use {
                                    it.setObject(1, legacyUserId)
                                    it.executeQuery().use { result ->
                                        result.next() shouldBe true
                                        result.getObject("provider") shouldBe null
                                        result.getString("provider_user_id") shouldBe null
                                        result.getString("email") shouldBe null
                                    }
                                }
                        }

                        Then("신규 불완전 사용자 행은 거부한다") {
                            shouldThrow<SQLException> {
                                connection.createStatement().use {
                                    it.executeUpdate("INSERT INTO users DEFAULT VALUES")
                                }
                            }
                        }
                    }
                } finally {
                    connection.createStatement().use {
                        it.execute("DROP SCHEMA $schema CASCADE")
                    }
                }
            }
        }
    })
