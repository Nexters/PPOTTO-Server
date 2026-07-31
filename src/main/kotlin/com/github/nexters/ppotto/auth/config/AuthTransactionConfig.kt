package com.github.nexters.ppotto.auth.config

import com.github.nexters.ppotto.auth.application.AuthService.Companion.SIGNUP_TRANSACTION
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class AuthTransactionConfig {
    @Bean(SIGNUP_TRANSACTION)
    fun signupTransaction(transactionManager: PlatformTransactionManager): TransactionOperations =
        TransactionTemplate(transactionManager).apply { timeout = SIGNUP_TRANSACTION_TIMEOUT_SECONDS }

    companion object {
        const val SIGNUP_TRANSACTION_TIMEOUT_SECONDS = 5
    }
}
