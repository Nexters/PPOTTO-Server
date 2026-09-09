package com.github.nexters.ppotto.global.observability

import io.sentry.protocol.User
import io.sentry.spring7.SentryUserProvider
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SentryUserContextProvider : SentryUserProvider {
    override fun provideUser(): User? {
        val authentication =
            SecurityContextHolder
                .getContext()
                .authentication
                ?.takeUnless { it is AnonymousAuthenticationToken }
        val userId = authentication?.principal as? UUID ?: return null
        return User().apply { id = userId.toString() }
    }
}
