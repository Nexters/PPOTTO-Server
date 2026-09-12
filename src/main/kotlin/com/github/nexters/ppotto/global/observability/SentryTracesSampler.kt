package com.github.nexters.ppotto.global.observability

import com.github.nexters.ppotto.global.web.PublicPaths
import io.sentry.SamplingContext
import io.sentry.SentryOptions
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class SentryTracesSampler : SentryOptions.TracesSamplerCallback {
    override fun sample(samplingContext: SamplingContext): Double? {
        val request = samplingContext.customSamplingContext?.get(REQUEST_KEY) as? HttpServletRequest ?: return null
        if (!PublicPaths.isActuator(request.requestURI)) return null
        return DROP_SAMPLE_RATE
    }

    private companion object {
        const val REQUEST_KEY = "request"
        const val DROP_SAMPLE_RATE = 0.0
    }
}
