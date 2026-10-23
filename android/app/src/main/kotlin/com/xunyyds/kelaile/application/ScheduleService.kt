package com.xunyyds.kelaile.application

import android.content.Context
import android.net.Uri
import com.xunyyds.kelaile.data.*
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

    /** 仅从 Excel 表格导入课表；旧版 JSON 备份在此明确拒绝，不做静默转换。 */
    suspend fun readTimetable(uri: Uri): SchoolImport = withContext(Dispatchers.IO) {
        require(isSpreadsheet(uri)) { "课表导入仅支持 Excel（.xlsx）表格；旧版 JSON 备份不支持此入口，请改用「应用数据恢复」" }
        val stream = app.contentResolver.openInputStream(uri) ?: error("无法读取文件")
        stream.use { XlsxTimetable.parse(XlsxReader.read(it)) }
    }

    /** 仅从整应用备份恢复；Excel 课表在此明确拒绝，避免两种语义混用。 */
    suspend fun readAppBackup(uri: Uri): AppData = withContext(Dispatchers.IO) {
        require(!isSpreadsheet(uri)) { "应用数据恢复需要备份文件（.json）；导入 Excel 课表请使用「课表导入」" }
        readBackup(uri)
    }

    private fun isSpreadsheet(uri: Uri): Boolean {
        val name = uri.lastPathSegment.orEmpty().lowercase()
        val mime = app.contentResolver.getType(uri).orEmpty().lowercase()
        return name.endsWith(".xlsx") || mime.contains("spreadsheetml")
    }

    /** 导出整应用数据备份（设置、课表、备注与颜色全量）。 */
    suspend fun exportBackup(uri: Uri, data: AppData) = withContext(Dispatchers.IO) {
        val bytes = BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("无法写入文件")
    }

    /** 导出 Excel 课表，与整应用备份完全分开。 */
    suspend fun exportTimetable(uri: Uri, data: AppData) = withContext(Dispatchers.IO) {
        require(data.courses.isNotEmpty()) { "还没有课程可导出" }
        val bytes = XlsxWriter.writeTimetable(data)
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("无法写入文件")
    }
}
