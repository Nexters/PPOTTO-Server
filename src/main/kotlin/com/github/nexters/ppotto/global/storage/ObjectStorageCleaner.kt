package com.github.nexters.ppotto.global.storage

interface ObjectStorageCleaner {
    fun deleteByPrefix(prefix: String): Int

    fun deleteAll(objectKeys: Collection<String>): Int
}
