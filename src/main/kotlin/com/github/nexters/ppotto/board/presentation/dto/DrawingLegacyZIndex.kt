package com.github.nexters.ppotto.board.presentation.dto

private const val LEGACY_Z_INDEX_KEY = "zIndex"

fun Map<String, Any?>.legacyZIndex(): Int = (this[LEGACY_Z_INDEX_KEY] as? Number)?.toInt() ?: 0

fun Map<String, Any?>.withoutLegacyZIndex(): Map<String, Any?> = this - LEGACY_Z_INDEX_KEY

fun Map<String, Any?>.withLegacyZIndex(zIndex: Int): Map<String, Any?> = this + (LEGACY_Z_INDEX_KEY to zIndex)
