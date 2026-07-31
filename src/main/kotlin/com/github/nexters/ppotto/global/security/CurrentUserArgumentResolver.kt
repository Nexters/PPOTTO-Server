package com.github.nexters.ppotto.global.security

import com.github.nexters.ppotto.global.error.UnauthorizedException
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == UUID::class.java &&
            (
                parameter.hasParameterAnnotation(AuthenticatedUser::class.java) ||
                    parameter.hasParameterAnnotation(CurrentUser::class.java)
            )

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UUID? =
        SecurityContextHolder.getContext().authentication.let { authentication ->
            when {
                authentication?.principal is UUID -> authentication.principal as UUID
                parameter.hasParameterAnnotation(CurrentUser::class.java) &&
                    (authentication == null || authentication is AnonymousAuthenticationToken) -> null
                else -> throw UnauthorizedException()
            }
        }
}
