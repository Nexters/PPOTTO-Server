package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.auth.infrastructure.sha256Hex
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class RedisRefreshTokenStore(
    private val redisTemplate: StringRedisTemplate,
    properties: JwtAuthProperties,
) : RefreshTokenStore {
    private val expirationSeconds = Duration.ofDays(properties.refreshTokenExpirationDays).seconds

    override fun save(
        userId: UserId,
        refreshToken: String,
    ) {
        val tokenHash = refreshToken.sha256Hex()
        redisTemplate.execute(
            SAVE_SCRIPT,
            listOf(userKey(userId), tokenKey(tokenHash)),
            TOKEN_KEY_PREFIX,
            tokenHash,
            expirationSeconds.toString(),
            userId.toString(),
        )
    }

    override fun findUserId(refreshToken: String): UserId? =
        redisTemplate
            .opsForValue()
            .get(tokenKey(refreshToken.sha256Hex()))
            ?.let { UserId(UUID.fromString(it)) }

    override fun rotate(
        userId: UserId,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean {
        val currentHash = currentRefreshToken.sha256Hex()
        val newHash = newRefreshToken.sha256Hex()
        return redisTemplate.execute(
            ROTATE_SCRIPT,
            listOf(userKey(userId), tokenKey(currentHash), tokenKey(newHash)),
            currentHash,
            newHash,
            userId.toString(),
            expirationSeconds.toString(),
        ) == SUCCESS
    }

    override fun delete(userId: UserId) {
        redisTemplate.execute(DELETE_SCRIPT, listOf(userKey(userId)), TOKEN_KEY_PREFIX)
    }

    private fun userKey(userId: UserId) = "$USER_KEY_PREFIX$userId"

    private fun tokenKey(tokenHash: String) = "$TOKEN_KEY_PREFIX$tokenHash"

    private companion object {
        const val USER_KEY_PREFIX = "auth:refresh:user:"
        const val TOKEN_KEY_PREFIX = "auth:refresh:token:"
        const val SUCCESS = 1L

        val SAVE_SCRIPT =
            DefaultRedisScript(
                """
                local previous = redis.call('GET', KEYS[1])
                if previous then redis.call('DEL', ARGV[1] .. previous) end
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                redis.call('SET', KEYS[2], ARGV[4], 'EX', ARGV[3])
                return 1
                """.trimIndent(),
                Long::class.java,
            )

        val ROTATE_SCRIPT =
            DefaultRedisScript(
                """
                if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
                if redis.call('GET', KEYS[2]) ~= ARGV[3] then return 0 end
                redis.call('DEL', KEYS[2])
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[4])
                redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[4])
                return 1
                """.trimIndent(),
                Long::class.java,
            )

        val DELETE_SCRIPT =
            DefaultRedisScript(
                """
                local current = redis.call('GET', KEYS[1])
                if current then redis.call('DEL', ARGV[1] .. current) end
                redis.call('DEL', KEYS[1])
                return 1
                """.trimIndent(),
                Long::class.java,
            )
    }
}
