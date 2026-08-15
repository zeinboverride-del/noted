package com.capacitorjs.plugins.localnotifications

import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.getcapacitor.CapConfig
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginConfig
import com.getcapacitor.plugin.util.AssetUtil
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Contains implementations for all notification actions.
 */
class LocalNotificationManager(
    private val storage: NotificationStorage,
    private val activity: Activity?,
    private val context: Context,
    capConfig: CapConfig
) {

    private val config: PluginConfig = capConfig.getPluginConfiguration("LocalNotifications")

    /**
     * Executed when a notification is launched by the user from the bar.
     */
    fun handleNotificationActionPerformed(data: Intent, notificationStorage: NotificationStorage): JSObject? {
        Logger.debug(Logger.tags("LN"), "LocalNotification received: " + data.dataString)
        val notificationId = data.getIntExtra(NOTIFICATION_INTENT_KEY, Int.MIN_VALUE)
        if (notificationId == Int.MIN_VALUE) {
            Logger.debug(Logger.tags("LN"), "Activity started without notification attached")
            return null
        }
        val existing = notificationStorage.getSavedNotification(notificationId.toString())
        if (isSafeToForget(existing)) {
            notificationStorage.deleteNotification(notificationId.toString())
        }
        val dataJson = JSObject()

        val results = RemoteInput.getResultsFromIntent(data)
        if (results != null) {
            val input = results.getCharSequence(REMOTE_INPUT_KEY)
            dataJson.put("inputValue", input?.toString())
        }
        val menuAction = data.getStringExtra(ACTION_INTENT_KEY)

        dismissVisibleNotification(notificationId)

        dataJson.put("actionId", menuAction)
        var request: JSONObject? = null
        try {
            val notificationJsonString = data.getStringExtra(NOTIFICATION_OBJ_INTENT_KEY)
            if (notificationJsonString != null) {
                request = JSObject(notificationJsonString)
            }
        } catch (e: JSONException) {
        }
        dataJson.put("notification", request)
        return dataJson
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "Default"
            val description = "Default"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(DEFAULT_NOTIFICATION_CHANNEL_ID, name, importance)
            channel.description = description
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            val soundUri = getDefaultSoundUrl(context)
            if (soundUri != null) {
                channel.setSound(soundUri, audioAttributes)
            }
            val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * When a notification carries a custom `sound` but no explicit `channelId`,
     * create (once) a dedicated channel configured with that sound and return its
     * id. On Android 8+ the sound belongs to the channel, not the notification, so
     * a per-notification sound only plays if it rides on its own channel. Returns
     * null when there is no resolvable custom sound (the default channel is used).
     */
    private fun soundChannelId(localNotification: LocalNotification): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val sound = localNotification.sound ?: return null
        val soundUri = SoundResolver.resolveUri(context, sound) ?: return null
        val channelId = "sound_" + SoundResolver.baseName(sound)
        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Notifications ($channelId)", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            channel.setSound(soundUri, audioAttributes)
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    fun schedule(call: PluginCall?, localNotifications: List<LocalNotification>): JSONArray? {
        val ids = JSONArray()
        val notificationManager = NotificationManagerCompat.from(context)

        if (!notificationManager.areNotificationsEnabled()) {
            call?.let { LocalNotificationsError.NOTIFICATIONS_DISABLED.reject(it) }
            return null
        }
        for (localNotification in localNotifications) {
            val id = localNotification.id
            if (id == null) {
                call?.let { LocalNotificationsError.MISSING_IDENTIFIER.reject(it) }
                return null
            }
            dismissVisibleNotification(id)
            cancelTimerForNotification(id)
            buildNotification(notificationManager, localNotification, call)
            ids.put(id)
        }
        return ids
    }

    private fun buildNotification(
        notificationManager: NotificationManagerCompat,
        localNotification: LocalNotification,
        call: PluginCall?
    ) {
        val channelId = localNotification.channelId ?: soundChannelId(localNotification) ?: DEFAULT_NOTIFICATION_CHANNEL_ID
        val foreground = localNotification.foreground == true
        val mBuilder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(localNotification.title)
            .setContentText(localNotification.body)
            .setAutoCancel(localNotification.autoCancel)
            .setOngoing(localNotification.ongoing)
            .setPriority(if (foreground) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setGroupSummary(localNotification.groupSummary)

        localNotification.badge?.let { mBuilder.setNumber(it) }

        if (localNotification.largeBody != null) {
            mBuilder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(localNotification.largeBody)
                    .setSummaryText(localNotification.summaryText)
            )
        }

        localNotification.inboxList?.let { lines ->
            val inboxStyle = NotificationCompat.InboxStyle()
            for (line in lines) inboxStyle.addLine(line)
            inboxStyle.setBigContentTitle(localNotification.title)
            inboxStyle.setSummaryText(localNotification.summaryText)
            mBuilder.setStyle(inboxStyle)
        }

        val soundUri = SoundResolver.resolveUri(context, localNotification.sound) ?: getDefaultSoundUrl(context)
        if (soundUri != null) {
            context.grantUriPermission("com.android.systemui", soundUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            mBuilder.setSound(soundUri)
            mBuilder.setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
        } else {
            mBuilder.setDefaults(Notification.DEFAULT_ALL)
        }

        val group = localNotification.group
        if (group != null) {
            mBuilder.setGroup(group)
            if (localNotification.groupSummary) {
                mBuilder.setSubText(localNotification.summaryText)
            }
        }

        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        mBuilder.setOnlyAlertOnce(true)

        mBuilder.setSmallIcon(localNotification.resolveSmallIcon(context, getDefaultSmallIcon(context)))
        mBuilder.setLargeIcon(localNotification.resolveLargeIcon(context))

        val iconColor = localNotification.resolveIconColor(config.getString("iconColor"))
        if (iconColor != null) {
            try {
                mBuilder.color = Color.parseColor(iconColor)
            } catch (ex: IllegalArgumentException) {
                call?.let { LocalNotificationsError.INVALID_COLOR.reject(it) }
                return
            }
        }

        createActionIntents(localNotification, mBuilder)
        val buildNotification = mBuilder.build()
        // A one-shot `at` already in the past (isTriggered()) fires immediately
        // instead of going through AlarmManager for a moment that's already gone —
        // matching the legacy plugin, which fires it in-process right away rather
        // than rejecting or silently dropping it. An `at`+repeats anchor already in
        // the past gets the same immediate catch-up fire, but the series isn't
        // re-registered afterward: the only interval this feature has is the gap
        // between call time and `at`, and once `at` is stale that gap is gone —
        // there's no way to recover what cadence was actually intended.
        val schedule = localNotification.schedule
        val isStaleRepeatingAt = schedule?.at != null && schedule.isRepeating() && schedule.at!!.time < Date().time
        if (localNotification.isScheduled() && !localNotification.isTriggered() && !isStaleRepeatingAt) {
            triggerScheduledNotification(buildNotification, localNotification)
        } else {
            try {
                val notificationJson = JSObject(localNotification.source ?: "{}")
                LocalNotificationsPlugin.fireReceived(notificationJson)
            } catch (e: JSONException) {
            }
            localNotification.id?.let { notificationManager.notify(it, buildNotification) }
        }
    }

    private fun createActionIntents(localNotification: LocalNotification, mBuilder: NotificationCompat.Builder) {
        val id = localNotification.id ?: return
        // Open intent
        val intent = buildIntent(localNotification, DEFAULT_PRESS_ACTION)
        var flags = PendingIntent.FLAG_CANCEL_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        val pendingIntent = PendingIntent.getActivity(context, id, intent, flags)
        mBuilder.setContentIntent(pendingIntent)

        // Build action types
        val actionTypeId = localNotification.actionTypeId
        if (actionTypeId != null) {
            val actionGroup = storage.getActionGroup(actionTypeId)
            for (notificationAction in actionGroup) {
                val actionIntent = buildIntent(localNotification, notificationAction.id)
                val actionPendingIntent = PendingIntent.getActivity(
                    context,
                    id + (notificationAction.id?.hashCode() ?: 0),
                    actionIntent,
                    flags
                )
                val actionBuilder = NotificationCompat.Action.Builder(
                    R.drawable.ic_transparent,
                    notificationAction.title,
                    actionPendingIntent
                )
                if (notificationAction.isInput()) {
                    val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel(notificationAction.title).build()
                    actionBuilder.addRemoteInput(remoteInput)
                }
                mBuilder.addAction(actionBuilder.build())
            }
        }

        // Dismiss intent
        val dismissIntent = Intent(context, NotificationDismissReceiver::class.java)
        dismissIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        dismissIntent.putExtra(NOTIFICATION_INTENT_KEY, id)
        dismissIntent.putExtra(ACTION_INTENT_KEY, "dismiss")
        var deleteFlags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            deleteFlags = PendingIntent.FLAG_MUTABLE
        }
        val deleteIntent = PendingIntent.getBroadcast(context, id, dismissIntent, deleteFlags)
        mBuilder.setDeleteIntent(deleteIntent)
    }

    private fun buildIntent(localNotification: LocalNotification, action: String?): Intent {
        val intent = if (activity != null) {
            Intent(context, activity.javaClass)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        }
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra(NOTIFICATION_INTENT_KEY, localNotification.id)
        intent.putExtra(ACTION_INTENT_KEY, action)
        intent.putExtra(NOTIFICATION_OBJ_INTENT_KEY, localNotification.source)
        return intent
    }

    private fun triggerScheduledNotification(notification: Notification, request: LocalNotification) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val schedule = request.schedule ?: return
        val requestId = request.id ?: return
        val notificationIntent = Intent(context, TimedNotificationPublisher::class.java)
        notificationIntent.putExtra(NOTIFICATION_INTENT_KEY, requestId)
        notificationIntent.putExtra(TimedNotificationPublisher.NOTIFICATION_KEY, notification)
        var flags = PendingIntent.FLAG_CANCEL_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        var pendingIntent = PendingIntent.getBroadcast(context, requestId, notificationIntent, flags)

        val at = schedule.at
        if (at != null) {
            // A stale `at` (one-shot or repeats) never reaches here — buildNotification()
            // diverts both to an immediate catch-up fire instead. Reachable only with a
            // still-future `at`, so the interval below is always positive.
            if (schedule.isRepeating()) {
                val interval = at.time - Date().time
                alarmManager.setRepeating(AlarmManager.RTC, at.time, interval, pendingIntent)
            } else {
                setExactIfPossible(alarmManager, request, at.time, pendingIntent)
            }
            return
        }

        val every = schedule.every
        if (every != null) {
            val everyInterval = schedule.everyInterval
            if (everyInterval != null) {
                val startTime = Date().time + everyInterval
                alarmManager.setRepeating(AlarmManager.RTC, startTime, everyInterval, pendingIntent)
            }
            return
        }

        val on = schedule.on
        if (on != null) {
            val trigger = on.nextTrigger(Date())
            notificationIntent.putExtra(TimedNotificationPublisher.CRON_KEY, on.toMatchString())
            pendingIntent = PendingIntent.getBroadcast(context, requestId, notificationIntent, flags)
            setExactIfPossible(alarmManager, request, trigger, pendingIntent)
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
            Logger.debug(Logger.tags("LN"), "notification " + requestId + " will next fire at " + sdf.format(Date(trigger)))
        }
    }

    private fun setExactIfPossible(
        alarmManager: AlarmManager,
        localNotification: LocalNotification,
        trigger: Long,
        pendingIntent: PendingIntent
    ) {
        val schedule = localNotification.schedule ?: return
        val useExact = localNotification.isExactNotification && canScheduleExactAlarms(alarmManager)
        if (localNotification.isExactNotification && !useExact) {
            Logger.warn("Capacitor/LocalNotification", "Exact alarms not allowed in user settings.  Notification scheduled with non-exact alarm.")
        }
        if (useExact) {
            if (schedule.allowWhileIdle()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC, trigger, pendingIntent)
            }
        } else {
            if (schedule.allowWhileIdle()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC, trigger, pendingIntent)
            }
        }
    }

    /** Whether the app can currently schedule exact alarms. Always true below Android 12. */
    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun cancel(call: PluginCall) {
        val notificationsToCancel = LocalNotification.getLocalNotificationPendingList(call)
        if (notificationsToCancel != null) {
            val activeIds = currentlyVisibleIds()
            for (id in notificationsToCancel) {
                cancelTimerForNotification(id)
                // Already-delivered notifications keep their storage record so they remain
                // queryable via getByIds()/getAll(TRIGGERED) — cancel only affects pending ones.
                // Mark them cancelled so classification (and reboot-restore) can tell they're
                // no longer actually scheduled even once the alarm itself is gone.
                val existing = storage.getSavedNotification(id.toString())
                val isDelivered = existing != null && isCurrentlyTriggered(existing, activeIds)
                if (isDelivered) {
                    storage.setCancelled(id.toString(), true)
                } else {
                    storage.deleteNotification(id.toString())
                }
            }
        }
        call.resolve()
    }

    /**
     * Cancel all pending (scheduled) notifications.
     */
    fun cancelAll() {
        val activeIds = currentlyVisibleIds()
        for (idStr in storage.getSavedNotificationIds()) {
            val id = idStr.toIntOrNull() ?: continue
            cancelTimerForNotification(id)
            // Same delivered-notification exception as cancel() above.
            val existing = storage.getSavedNotification(idStr)
            val isDelivered = existing != null && isCurrentlyTriggered(existing, activeIds)
            if (isDelivered) {
                storage.setCancelled(idStr, true)
            } else {
                storage.deleteNotification(idStr)
            }
        }
    }

    private fun cancelTimerForNotification(notificationId: Int) {
        val intent = Intent(context, TimedNotificationPublisher::class.java)
        var flags = PendingIntent.FLAG_NO_CREATE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        val pi = PendingIntent.getBroadcast(context, notificationId, intent, flags)
        if (pi != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun dismissVisibleNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /** Live set of notification ids currently showing in the notification shade. */
    fun currentlyVisibleIds(): Set<Int> {
        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        return notificationManager.activeNotifications.map { it.id }.toSet()
    }

    /**
     * Whether [n] currently belongs in the externally-visible SCHEDULED state:
     * not yet triggered, and — if perpetual — not cancelled. `cancelled` is the
     * authoritative signal for a perpetual schedule rather than the live alarm
     * registration, since that doesn't survive a reboot to say the same thing.
     */
    fun isCurrentlyScheduled(n: LocalNotification): Boolean {
        if (n.schedule?.isPerpetual() == true) return !n.cancelled
        return !n.isTriggered()
    }

    /**
     * Whether [n] currently belongs in the externally-visible TRIGGERED state:
     * already fired and won't repeat (one-shot, past due), or perpetual with its
     * current instance still showing in the notification shade. [activeIds] is
     * the live set of currently-visible notification ids (see [currentlyVisibleIds]).
     */
    fun isCurrentlyTriggered(n: LocalNotification, activeIds: Set<Int>): Boolean {
        val nid = n.id
        return n.isTriggered() || (n.schedule?.isPerpetual() == true && nid != null && activeIds.contains(nid))
    }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun getDefaultSoundUrl(context: Context): Uri? {
        val soundId = getDefaultSound(context)
        return if (soundId != AssetUtil.RESOURCE_ID_ZERO_VALUE) {
            Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + soundId)
        } else {
            null
        }
    }

    private fun getDefaultSound(context: Context): Int {
        if (defaultSoundID != AssetUtil.RESOURCE_ID_ZERO_VALUE) return defaultSoundID
        var resId = AssetUtil.RESOURCE_ID_ZERO_VALUE
        val soundConfigResourceName = AssetUtil.getResourceBaseName(config.getString("sound"))
        if (soundConfigResourceName != null) {
            resId = AssetUtil.getResourceID(context, soundConfigResourceName, "raw")
        }
        defaultSoundID = resId
        return resId
    }

    private fun getDefaultSmallIcon(context: Context): Int {
        if (defaultSmallIconID != AssetUtil.RESOURCE_ID_ZERO_VALUE) return defaultSmallIconID
        var resId = AssetUtil.RESOURCE_ID_ZERO_VALUE
        val smallIconConfigResourceName = AssetUtil.getResourceBaseName(config.getString("smallIcon"))
        if (smallIconConfigResourceName != null) {
            resId = AssetUtil.getResourceID(context, smallIconConfigResourceName, "drawable")
        }
        if (resId == AssetUtil.RESOURCE_ID_ZERO_VALUE) {
            resId = android.R.drawable.ic_dialog_info
        }
        defaultSmallIconID = resId
        return resId
    }

    companion object {
        const val NOTIFICATION_INTENT_KEY = "LocalNotificationId"
        const val NOTIFICATION_OBJ_INTENT_KEY = "LocalNotficationObject"
        const val ACTION_INTENT_KEY = "LocalNotificationUserAction"
        const val REMOTE_INPUT_KEY = "LocalNotificationRemoteInput"
        const val DEFAULT_NOTIFICATION_CHANNEL_ID = "default"

        private const val DEFAULT_PRESS_ACTION = "tap"

        private var defaultSoundID = AssetUtil.RESOURCE_ID_ZERO_VALUE
        private var defaultSmallIconID = AssetUtil.RESOURCE_ID_ZERO_VALUE

        /**
         * Whether [n]'s storage record is safe to delete. A one-shot notification
         * is safe only once it has actually fired — never before, so clearing it
         * can't silently cancel a still-pending notification. A perpetual schedule
         * is safe only once it's been marked cancelled — there's no series left to
         * preserve the record for. Pure data check (no context needed), so callers
         * without a full LocalNotificationManager instance (dismiss receiver) can
         * use it too.
         */
        fun isSafeToForget(n: LocalNotification?): Boolean {
            if (n == null) return true
            return if (n.schedule?.isPerpetual() == true) n.cancelled else n.isTriggered()
        }
    }
}
