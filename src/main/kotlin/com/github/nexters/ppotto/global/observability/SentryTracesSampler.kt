package com.github.nexters.ppotto.global.observability

import io.sentry.SamplingContext
import io.sentry.SentryOptions
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class SentryTracesSampler : SentryOptions.TracesSamplerCallback {
    override fun sample(samplingContext: SamplingContext): Double? =
        samplingContext.customSamplingContext
            ?.get(REQUEST_KEY)
            ?.let { it as? HttpServletRequest }
            ?.takeIf { it.requestURI.startsWith(ACTUATOR_PATH_PREFIX) }
            ?.let { DROP_SAMPLE_RATE }

    private companion object {
        const val REQUEST_KEY = "request"
        const val ACTUATOR_PATH_PREFIX = "/actuator"
        const val DROP_SAMPLE_RATE = 0.0
    }
}
