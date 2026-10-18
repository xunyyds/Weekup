package com.xu.wake.application

import android.content.Context
import android.net.Uri
import com.xu.wake.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

                                                                                                          
class ScheduleService(context:Context) {
    private val app=context.applicationContext
    private val repository=Repository(app)
    private val mutex=Mutex()
    data class Saved(val data:AppData,val reminderFailed:Boolean)
    suspend fun load():AppData=withContext(Dispatchers.IO) {
        mutex.withLock { repository.load().also { runCatching { Reminders.sync(app,it) } } }
    }
    suspend fun save(data:AppData,recovery:Boolean=false):Saved=withContext(Dispatchers.IO+NonCancellable) {
        mutex.withLock {
            if(recovery)repository.restore(data) else repository.save(data)
            Saved(data,runCatching { Reminders.sync(app,data) }.isFailure)
        }
    }
    suspend fun readBackup(uri:Uri):AppData=withContext(Dispatchers.IO) {
        val text=app.contentResolver.openInputStream(uri)?.use { input ->
            val out=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true){val size=input.read(buffer);if(size<0)break;require(out.size()+size<=BackupCodec.MAX_CHARS*4){"备份文件过大"};out.write(buffer,0,size)}
            val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString()
        } ?: error("无法读取文件")
        BackupCodec.decode(text)
    }
    suspend fun export(uri:Uri,data:AppData)=withContext(Dispatchers.IO) {
        val bytes=BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
        app.contentResolver.openOutputStream(uri,"wt")?.use { it.write(bytes) } ?: error("无法写入文件")
    }
}
