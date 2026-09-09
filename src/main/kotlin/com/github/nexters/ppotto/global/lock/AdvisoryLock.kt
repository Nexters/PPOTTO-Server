package com.github.nexters.ppotto.global.lock

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdvisoryLock(
    val namespace: String,
    val key: String,
)
