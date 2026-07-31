package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.global.storage.ObjectStorageCleaner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class ObjectStorageTestConfiguration {
    @Bean
    @Primary
    fun objectStorageCleaner(): RecordingObjectStorageCleaner = RecordingObjectStorageCleaner()
}

class RecordingObjectStorageCleaner : ObjectStorageCleaner {
    val deletedPrefixes = mutableListOf<String>()
    val deletedObjectKeys = mutableListOf<String>()

    override fun deleteByPrefix(prefix: String): Int =
        prefix
            .also(deletedPrefixes::add)
            .let { 0 }

    override fun deleteAll(objectKeys: Collection<String>): Int =
        objectKeys
            .also(deletedObjectKeys::addAll)
            .size

    fun clear() {
        deletedPrefixes.clear()
        deletedObjectKeys.clear()
    }
}
