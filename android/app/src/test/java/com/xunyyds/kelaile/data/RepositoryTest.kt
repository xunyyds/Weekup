package com.xunyyds.kelaile.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class RepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private class MemoryAtomic : AtomicStorage {
        var bytes: ByteArray? = null
        var failWrite = false
        var writes = 0
        override fun exists() = bytes != null
        override fun openRead() = ByteArrayInputStream(bytes ?: throw IOException("missing"))
        override fun write(bytes: ByteArray) {
            writes++
            if (failWrite) throw IOException("injected interrupted transaction")
            this.bytes = bytes.copyOf()
        }
    }
    @Test fun roundTripKeepsDataIntact() {
        val root = temporary.newFolder()
        val store = MemoryAtomic()
        val repository = Repository(root, store)
        val data = AppData()
        repository.save(data)
        assertEquals(data, repository.load())
    }
    @Test fun emptyStorageLoadsDefaults() {
        val root = temporary.newFolder()
        val repository = Repository(root, MemoryAtomic())
        assertEquals(AppData(), repository.load())
    }
    @Test fun corruptionNeverOverwritesExistingSource() {
        val root = temporary.newFolder()
        val store = MemoryAtomic()
        val repository = Repository(root, store)
        store.bytes = "bad native".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { repository.load() }
        assertThrows(IllegalArgumentException::class.java) { repository.save(AppData()) }
        assertEquals("bad native", String(store.bytes!!))
        assertEquals(0, store.writes)
    }
    @Test fun failedTransactionLeavesPreviousSnapshotReadable() {
        val root = temporary.newFolder()
        val store = MemoryAtomic()
        val repository = Repository(root, store)
        val before = AppData()
        repository.save(before)
        store.failWrite = true
        assertThrows(IOException::class.java) { repository.save(before.copy(settings = before.settings.copy(school = "changed"))) }
        assertEquals(before, repository.load())
    }
    @Test fun invalidCandidateDoesNotReachStorage() {
        val root = temporary.newFolder()
        val store = MemoryAtomic()
        val repository = Repository(root, store)
        val mutable = mutableListOf<CourseSession>()
        val data = AppData(courses = mutable)
        mutable += CourseSession("id", "课程", 1, 1, 2, listOf(60))
        assertThrows(IllegalArgumentException::class.java) { repository.save(data) }
        assertEquals(0, store.writes)
    }
}
