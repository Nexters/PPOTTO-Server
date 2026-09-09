package com.github.nexters.ppotto.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate

const val TRANSACTION_ADVICE_ORDER = 100

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true, order = TRANSACTION_ADVICE_ORDER)
class TransactionConfig {
    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate = TransactionTemplate(transactionManager)
}
