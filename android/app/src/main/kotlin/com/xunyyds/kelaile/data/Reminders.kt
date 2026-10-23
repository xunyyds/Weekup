package com.xunyyds.kelaile.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.Executors

object Reminders {
    private const val CHANNEL = "classes"
    private const val PREFS = "native_class_reminders"
    private const val MAX = 300
    internal const val ACTION = "com.xunyyds.kelaile.data.REMINDER"

    @Synchronized
    fun sync(context: Context, data: AppData) {
        val app = context.applicationContext

        val latest = Repository(app).load()
        val events = ReminderPlan.events(latest)
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        for (id in 1..MAX) pending(app, id, PendingIntent.FLAG_NO_CREATE)?.let { alarm.cancel(it); it.cancel() }
        check(prefs.edit().clear().commit()) { "无法保存提醒状态" }
        if (events.isEmpty()) return
        if (!notificationAllowed(app)) return
        channel(app)

        val editor = prefs.edit()
        events.forEachIndexed { i, event ->
            val id = i + 1
            editor.putString("${id}_title", "上课提醒：${event.course.name}")
                .putString("${id}_body", "上课地点：${event.course.location} · 教师：${event.course.teacher}")
                .putLong("${id}_trigger", event.triggerAtMillis).putLong("${id}_start", event.startsAtMillis)
        }
        check(editor.commit()) { "无法保存提醒状态" }
        try {
            events.forEachIndexed { i, event ->
                val intent = pending(app, i + 1, PendingIntent.FLAG_UPDATE_CURRENT)!!
                if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.triggerAtMillis, intent)
                else alarm.set(AlarmManager.RTC_WAKEUP, event.triggerAtMillis, intent)
            }
        } catch (e: RuntimeException) {
            for (id in 1..MAX) pending(app, id, PendingIntent.FLAG_NO_CREATE)?.let { alarm.cancel(it); it.cancel() }
            prefs.edit().clear().commit()
            throw e
        }
    }

    private fun pending(context: Context, id: Int, flags: Int): PendingIntent? = PendingIntent.getBroadcast(context, id,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION).setData(android.net.Uri.parse("kelaile://reminder/$id")),
        flags or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    internal fun notificationAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled()
    }

    internal fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "课程开始前提醒" })
        }
    }

    @Synchronized
    internal fun deliver(context: Context, id: Int) {
        if (id !in 1..MAX || !notificationAllowed(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trigger = prefs.getLong("${id}_trigger", -1)
        val start = prefs.getLong("${id}_start", -1)
        val now = System.currentTimeMillis()
        if (trigger < 0 || now < trigger || now > start + 15 * 60_000L) return
        val title = prefs.getString("${id}_title", null) ?: return
        val body = prefs.getString("${id}_body", "").orEmpty()
        check(prefs.edit().remove("${id}_trigger").commit())
        channel(context)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        builder.setSmallIcon(context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setAutoCancel(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        }
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, builder.build()) }
        catch (_: SecurityException) {                                                           }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION) return
        val id = intent.data?.lastPathSegment?.toIntOrNull() ?: return
        val pending = goAsync()
        ReceiverWorker.execute {
            try {
                if (id !in 1..300) return@execute

                for (dueId in 1..300) Reminders.deliver(context.applicationContext, dueId)

                Reminders.sync(context.applicationContext, Repository(context).load())
            } catch (failure: Exception) {
                android.util.Log.e("KelaileReminders", "无法发送或补充课程提醒", failure)
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        ReceiverWorker.execute {
            try { Reminders.sync(context.applicationContext, Repository(context).load()) }
            catch (failure: Exception) { android.util.Log.e("KelaileReminders", "无法恢复课程提醒；保留课表原文件", failure) }
            finally { pending.finish() }
        }
    }
}

private object ReceiverWorker {

    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "kelaile-reminders") }
    fun execute(task: () -> Unit) { executor.execute(task) }
}
