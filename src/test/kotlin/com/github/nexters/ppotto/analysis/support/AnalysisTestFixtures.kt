package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.application.PhotoUploadGroupRequest
import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import java.time.Instant

const val DEFAULT_PHOTO_GROUP_COUNT = 90

private val FIRST_TAKEN_AT: Instant = Instant.parse("2026-07-01T00:00:00Z")

fun photoTakenAt(index: Int): Instant = FIRST_TAKEN_AT.plusSeconds(index.toLong())

fun photoUploadItems(
    count: Int = DEFAULT_PHOTO_GROUP_COUNT,
    contentTypeAt: (Int) -> PhotoContentType = { PhotoContentType.JPEG },
): List<PhotoUploadItemRequest> = (0 until count).map { PhotoUploadItemRequest(photoTakenAt(it), contentTypeAt(it)) }

fun photoUploadGroups(
    count: Int = DEFAULT_PHOTO_GROUP_COUNT,
    contentTypeAt: (Int) -> PhotoContentType = { PhotoContentType.JPEG },
): List<PhotoUploadGroupRequest> = photoUploadItems(count, contentTypeAt).asGroups()

fun List<PhotoUploadItemRequest>.asGroups(): List<PhotoUploadGroupRequest> = map { PhotoUploadGroupRequest(listOf(it)) }

fun burstPhotoUploadGroups(
    standaloneCount: Int,
    representativeValues: List<Boolean>,
): List<PhotoUploadGroupRequest> =
    photoUploadItems(standaloneCount).asGroups() +
        PhotoUploadGroupRequest(
            representativeValues.mapIndexed { index, isRepresentative ->
                PhotoUploadItemRequest(photoTakenAt(standaloneCount + index), PhotoContentType.JPEG, isRepresentative)
            },
        )

fun photoUploadGroupsJson(
    count: Int = DEFAULT_PHOTO_GROUP_COUNT,
    contentTypeAt: (Int) -> String = { "image/jpeg" },
): String =
    (0 until count).joinToString(",", prefix = "[", postfix = "]") { index ->
        """{"items": [${photoUploadItemJson(index, contentTypeAt(index))}]}"""
    }

fun burstPhotoUploadGroupsJson(
    standaloneCount: Int,
    representativeValues: List<Boolean>,
): String {
    val standaloneGroups =
        (0 until standaloneCount).map { index -> """{"items": [${photoUploadItemJson(index, "image/jpeg")}]}""" }
    val burstItems =
        representativeValues
            .mapIndexed { index, isRepresentative ->
                photoUploadItemJson(standaloneCount + index, "image/jpeg", isRepresentative)
            }.joinToString(",")
    return (standaloneGroups + """{"items": [$burstItems]}""").joinToString(",", prefix = "[", postfix = "]")
}

private fun photoUploadItemJson(
    index: Int,
    contentType: String,
    isRepresentative: Boolean = true,
): String = """{"takenAt": "${photoTakenAt(index)}", "contentType": "$contentType", "isRepresentative": $isRepresentative}"""
