package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

private val EXTEND_USERS_MIGRATION =
    ClassPathResource("db/migration/V20260730112020__extend_users_for_social_accounts.sql")
private val ADD_USER_NAME_MIGRATION =
    ClassPathResource("db/migration/V20260807103000__add_user_name.sql")

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
                        ScriptUtils.executeSqlScript(connection, EXTEND_USERS_MIGRATION)

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

                        And("불완전한 레거시 행을 남긴 채 사용자 이름 추가 마이그레이션을 적용하면") {
                            Then("NOT VALID 완전성 제약이 전체 UPDATE에서 다시 검사돼 마이그레이션이 중단된다") {
                                val exception =
                                    shouldThrow<ScriptStatementFailedException> {
                                        ScriptUtils.executeSqlScript(connection, ADD_USER_NAME_MIGRATION)
                                    }
                                exception.cause
                                    ?.message
                                    ?.contains("ck_users_social_identity_complete") shouldBe true
                            }
                        }

                        And("레거시 행의 소셜 정보를 채운 뒤 사용자 이름 추가 마이그레이션을 적용하면") {
                            connection
                                .prepareStatement(
                                    """
                                    UPDATE users
                                    SET provider = 'KAKAO', provider_user_id = 'legacy', email = 'legacy@example.com'
                                    WHERE id = ?
                                    """.trimIndent(),
                                ).use {
                                    it.setObject(1, legacyUserId)
                                    it.executeUpdate()
                                }
                            ScriptUtils.executeSqlScript(connection, ADD_USER_NAME_MIGRATION)

                            Then("기존 행 이름을 홍길동으로 채우고 이름 없는 행은 거부한다") {
                                connection
                                    .prepareStatement("SELECT name FROM users WHERE id = ?")
                                    .use {
                                        it.setObject(1, legacyUserId)
                                        it.executeQuery().use { result ->
                                            result.next() shouldBe true
                                            result.getString("name") shouldBe "홍길동"
                                        }
                                    }
                                shouldThrow<SQLException> {
                                    connection.createStatement().use {
                                        it.executeUpdate("INSERT INTO users (name) VALUES (NULL)")
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    connection.createStatement().use {
                        it.execute("RESET search_path")
                        it.execute("DROP SCHEMA $schema CASCADE")
                    }
                }
            }
        }
    })
