package com.github.nexters.ppotto.global.error

open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

open class InvalidInputException(
    errorCode: ErrorCode = CommonErrorCode.INVALID_INPUT,
    message: String = errorCode.message,
) : BusinessException(errorCode, message)

open class UnauthorizedException(
    errorCode: ErrorCode = CommonErrorCode.UNAUTHORIZED,
    message: String = errorCode.message,
) : BusinessException(errorCode, message)

open class ForbiddenException(
    errorCode: ErrorCode = CommonErrorCode.FORBIDDEN,
    message: String = errorCode.message,
) : BusinessException(errorCode, message)

open class NotFoundException(
    errorCode: ErrorCode = CommonErrorCode.RESOURCE_NOT_FOUND,
    message: String = errorCode.message,
) : BusinessException(errorCode, message)

open class ConflictException(
    errorCode: ErrorCode = CommonErrorCode.CONFLICT,
    message: String = errorCode.message,
) : BusinessException(errorCode, message)
