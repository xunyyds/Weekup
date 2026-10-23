package com.xunyyds.kelaile.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException

class Repository internal constructor(
    private val directory: File,
    private val legacy: File,
    private val storage: AtomicStorage,
) {
    constructor(context: Context) : this(
        context.applicationContext.filesDir,
        File(context.applicationContext.applicationInfo.dataDir, "app_flutter/课表.json"),
        AndroidAtomicStorage(File(context.applicationContext.filesDir, "课表.json")),
    )

    fun load(): AppData = synchronized(lock) {
        when {
            nativeExists() -> readNative()
            legacy.exists() -> decodeFile(legacy)
            else -> AppData()
        }
    }

    fun save(data: AppData) = synchronized(lock) {

        val bytes = BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
        if (nativeExists()) readNative() else if (legacy.exists()) decodeFile(legacy)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建数据目录" }
        storage.write(bytes)
    }

    fun restore(data: AppData) = synchronized(lock) {
        val bytes = BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建数据目录" }
        if (nativeExists()) {
            storage.openRead().use { input ->
                File(directory, "课表-recovery-${System.currentTimeMillis()}.json").outputStream().use { output -> input.copyTo(output) }
            }
        }

        storage.write(bytes)
    }

    private fun nativeExists() = storage.exists()
    private fun readNative(): AppData = storage.openRead().use { stream -> decodeBytes(stream.readBytesLimited()) }
    private fun decodeFile(source: File): AppData = source.inputStream().use { decodeBytes(it.readBytesLimited()) }
    private fun decodeBytes(bytes: ByteArray): AppData {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        return BackupCodec.decode(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString())
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (out.size() + count > BackupCodec.MAX_CHARS * 4) throw IOException("课表文件过大")
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    companion object { private val lock = Any() }
}

internal interface AtomicStorage {
    fun exists(): Boolean
    fun openRead(): java.io.InputStream
    fun write(bytes: ByteArray)
}

private class AndroidAtomicStorage(private val file: File) : AtomicStorage {
    private val atomic = AtomicFile(file)
    override fun exists() = file.exists() || File(file.path + ".bak").exists()
    override fun openRead(): java.io.InputStream = atomic.openRead()
    override fun write(bytes: ByteArray) {
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }
}
