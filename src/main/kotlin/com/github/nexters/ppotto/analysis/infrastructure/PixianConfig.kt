package com.github.nexters.ppotto.analysis.infrastructure

import org.springframework.context.annotation.Configuration
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "pixian", types = [PixianApi::class])
class PixianConfig
