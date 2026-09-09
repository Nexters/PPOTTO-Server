package com.github.nexters.ppotto.global.transaction

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

fun afterCommit(block: () -> Unit) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        block()
        return
    }
    TransactionSynchronizationManager.registerSynchronization(
        object : TransactionSynchronization {
            override fun afterCommit() = block()
        },
    )
}
