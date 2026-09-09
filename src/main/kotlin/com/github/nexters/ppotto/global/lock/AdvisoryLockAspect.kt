package com.github.nexters.ppotto.global.lock

import com.github.nexters.ppotto.global.config.TRANSACTION_ADVICE_ORDER
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.context.expression.MethodBasedEvaluationContext
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.annotation.Order
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

const val ADVISORY_LOCK_ADVICE_ORDER = TRANSACTION_ADVICE_ORDER + 100

@Aspect
@Component
@Order(ADVISORY_LOCK_ADVICE_ORDER)
class AdvisoryLockAspect(
    private val dslContext: DSLContext,
) {
    @Around("@annotation(advisoryLock)")
    fun lock(
        joinPoint: ProceedingJoinPoint,
        advisoryLock: AdvisoryLock,
    ): Any? {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "@AdvisoryLock은 트랜잭션 안에서만 잡을 수 있습니다: ${joinPoint.signature.toShortString()}"
        }
        val lockKey = "${advisoryLock.namespace}:${evaluateKey(joinPoint, advisoryLock.key)}"
        dslContext.select(DSL.field("pg_advisory_xact_lock(hashtextextended({0}, 0))", Any::class.java, DSL.value(lockKey))).execute()
        return joinPoint.proceed()
    }

    private fun evaluateKey(
        joinPoint: ProceedingJoinPoint,
        expression: String,
    ): String {
        val method = (joinPoint.signature as MethodSignature).method
        val context = MethodBasedEvaluationContext(joinPoint.target, method, joinPoint.args, PARAMETER_NAME_DISCOVERER)
        return checkNotNull(PARSER.parseExpression(expression).getValue(context, String::class.java)) {
            "@AdvisoryLock key가 null로 평가되었습니다: $expression"
        }
    }

    companion object {
        private val PARSER = SpelExpressionParser()
        private val PARAMETER_NAME_DISCOVERER = DefaultParameterNameDiscoverer()
    }
}
