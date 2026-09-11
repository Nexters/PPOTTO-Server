package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.application.port.BlobMeta
import com.github.nexters.ppotto.analysis.application.port.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.infrastructure.storage.PhotoObjectKeys
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.storage.GcsReadUrlIssuer
import com.github.nexters.ppotto.support.ResettableFake
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakePhotoStorage(
    private val gcsReadUrlIssuer: GcsReadUrlIssuer,
) : PhotoStorage,
    ResettableFake {
    private val objects = ConcurrentHashMap<String, BlobMeta>()

    val deletedAnalysisIds = CopyOnWriteArrayList<AnalysisId>()

    var issueUploadUrlsFailure: Throwable? = null

    var deleteAllFailure: Throwable? = null

    var onUploadedObjects: (() -> Unit)? = null

    override fun issueUploadUrls(photos: List<Photo>): Map<PhotoId, String> {
        issueUploadUrlsFailure?.let { throw it }
        return photos.associate { photo -> photo.id to "https://fake-signed-url/${PhotoObjectKeys.keyFor(photo)}" }
    }

    override fun issueReadUrls(photos: List<Photo>): Map<PhotoId, String> {
        val objectKeyByPhotoId = photos.associate { it.id to PhotoObjectKeys.keyFor(it) }
        val readUrls = gcsReadUrlIssuer.issue(objectKeyByPhotoId.values)
        return objectKeyByPhotoId.mapValues { (_, objectKey) -> readUrls.getValue(objectKey) }
    }

    override fun sourceUri(photo: Photo): String = "gs://fake-bucket/${PhotoObjectKeys.keyFor(photo)}"

    override fun uploadedObjects(
        analysisId: AnalysisId,
        photos: List<Photo>,
    ): Map<PhotoId, BlobMeta> {
        onUploadedObjects?.invoke()
        return photos.mapNotNull { photo -> objects[PhotoObjectKeys.keyFor(photo)]?.let { photo.id to it } }.toMap()
    }

    override fun deleteAll(analysisId: AnalysisId): Int {
        deleteAllFailure?.let { throw it }
        deletedAnalysisIds += analysisId
        val deletedKeys = objects.keys.filter { it.startsWith(PhotoObjectKeys.prefixFor(analysisId)) }
        deletedKeys.forEach { objects -= it }
        return deletedKeys.size
    }

    fun markMissing(photo: Photo) {
        objects -= PhotoObjectKeys.keyFor(photo)
    }

    fun markUploaded(
        photo: Photo,
        size: Long = 1,
        createdAt: Instant = Instant.now(),
    ) {
        objects[PhotoObjectKeys.keyFor(photo)] = BlobMeta(size, createdAt)
    }

    fun markUploaded(photos: Collection<Photo>) {
        photos.forEach { markUploaded(it) }
    }

    fun uploadedObjectCount(): Int = objects.size

    override fun reset() {
        objects.clear()
        deletedAnalysisIds.clear()
        issueUploadUrlsFailure = null
        deleteAllFailure = null
        onUploadedObjects = null
    }

    fun clear() = reset()
}
