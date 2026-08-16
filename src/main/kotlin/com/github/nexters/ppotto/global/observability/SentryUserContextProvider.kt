package com.github.nexters.ppotto.global.observability

import io.sentry.protocol.User
import io.sentry.spring7.SentryUserProvider
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SentryUserContextProvider : SentryUserProvider {
    override fun provideUser(): User? =
        SecurityContextHolder
            .getContext()
            .authentication
            ?.takeUnless { it is AnonymousAuthenticationToken }
            ?.principal
            ?.let { it as? UUID }
            ?.let { userId -> User().apply { id = userId.toString() } }
}
