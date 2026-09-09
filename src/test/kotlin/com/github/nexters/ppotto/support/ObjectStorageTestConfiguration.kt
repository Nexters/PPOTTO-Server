package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.global.storage.ObjectStorageCleaner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

@TestConfiguration(proxyBeanMethods = false)
class ObjectStorageTestConfiguration {
    @Bean
    @Primary
    fun objectStorageCleaner(): RecordingObjectStorageCleaner = RecordingObjectStorageCleaner()
}

class RecordingObjectStorageCleaner :
    ObjectStorageCleaner,
    ResettableFake {
    val deletedPrefixes = CopyOnWriteArrayList<String>()
    val deletedObjectKeys = CopyOnWriteArrayList<String>()

    override fun deleteByPrefix(prefix: String): Int {
        deletedPrefixes += prefix
        return 0
    }

    override fun deleteAll(objectKeys: Collection<String>): Int {
        deletedObjectKeys += objectKeys
        return objectKeys.size
    }

    override fun reset() {
        deletedPrefixes.clear()
        deletedObjectKeys.clear()
    }

    fun clear() = reset()
}
