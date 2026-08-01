package com.github.nexters.ppotto.global.identifier

import java.util.UUID

@JvmInline
value class UserId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

@JvmInline
value class BoardId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

@JvmInline
value class StickerId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

@JvmInline
value class AnalysisId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

@JvmInline
value class PhotoId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

@JvmInline
value class DrawingId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

@JvmInline
value class TermId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}
