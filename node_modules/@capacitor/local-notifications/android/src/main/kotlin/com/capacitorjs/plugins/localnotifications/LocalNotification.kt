package com.capacitorjs.plugins.localnotifications

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import com.getcapacitor.plugin.util.AssetUtil
import java.text.ParseException
import java.util.Date
import org.json.JSONException
import org.json.JSONObject

/**
 * Local notification object mapped from the JS plugin call. Includes the
 * additive `badge` / `foreground` fields.
 */
class LocalNotification {

    var title: String? = null
    var body: String? = null
    var largeBody: String? = null
    var summaryText: String? = null
    var id: Int? = null
    var sound: String? = null
    var iconColor: String? = null
    var actionTypeId: String? = null
    var group: String? = null
    var inboxList: List<String>? = null
    var groupSummary: Boolean = false
    var ongoing: Boolean = false
    var autoCancel: Boolean = true
    // Raw JSON value. `extra` is documented as `any`, not just an object.
    var extra: Any? = null
    var attachments: List<LocalNotificationAttachment>? = null
    var schedule: LocalNotificationSchedule? = null
    var channelId: String? = null
    var source: String? = null
    var badge: Int? = null
    var foreground: Boolean? = null
    var isExactNotification: Boolean = true
    var isExactMandatory: Boolean = false

    /**
     * Internal bookkeeping only — never sent to or read from JS. Set when
     * cancel()/cancelAll() preserves a still-delivered notification's record
     * instead of deleting it, so classification and reboot-restore can tell
     * it's no longer actually scheduled, even though live OS signals like the
     * alarm registration don't survive a reboot to say so themselves.
     */
    var cancelled: Boolean = false

    // Icons are stored as their bare resource base name.
    var smallIcon: String? = null
        set(value) {
            field = AssetUtil.getResourceBaseName(value)
        }
    var largeIcon: String? = null
        set(value) {
            field = AssetUtil.getResourceBaseName(value)
        }

    fun resolveSound(context: Context, defaultSound: Int): String? {
        var resId = AssetUtil.RESOURCE_ID_ZERO_VALUE
        val name = AssetUtil.getResourceBaseName(sound)
        if (name != null) resId = AssetUtil.getResourceID(context, name, "raw")
        if (resId == AssetUtil.RESOURCE_ID_ZERO_VALUE) resId = defaultSound
        return if (resId != AssetUtil.RESOURCE_ID_ZERO_VALUE) {
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + resId
        } else {
            null
        }
    }

    fun resolveIconColor(globalColor: String?): String? = iconColor ?: globalColor

    fun resolveSmallIcon(context: Context, defaultIcon: Int): Int {
        var resId = AssetUtil.RESOURCE_ID_ZERO_VALUE
        smallIcon?.let { resId = AssetUtil.getResourceID(context, it, "drawable") }
        if (resId == AssetUtil.RESOURCE_ID_ZERO_VALUE) resId = defaultIcon
        return resId
    }

    fun resolveLargeIcon(context: Context): Bitmap? {
        largeIcon?.let {
            val resId = AssetUtil.getResourceID(context, it, "drawable")
            return BitmapFactory.decodeResource(context.resources, resId)
        }
        return null
    }

    fun isScheduled(): Boolean {
        val s = schedule ?: return false
        return s.on != null || s.at != null || s.every != null
    }

    /**
     * Whether this notification has already fired and won't fire again — a
     * one-shot `at` whose time has passed. Perpetual schedules (`every`/`on`/
     * `repeats`) are never "triggered"; they stay "scheduled" indefinitely.
     */
    fun isTriggered(): Boolean {
        val s = schedule ?: return false
        if (s.isPerpetual()) return false
        val at = s.at ?: return false
        return at.time <= Date().time
    }

    companion object {

        fun buildNotificationList(call: PluginCall): MutableList<LocalNotification>? {
            val notificationArray = call.getArray("notifications")
            if (notificationArray == null) {
                LocalNotificationsError.INVALID_NOTIFICATIONS_ARRAY.reject(call)
                return null
            }
            val resultLocalNotifications = ArrayList<LocalNotification>(notificationArray.length())
            val notificationsJson: List<JSONObject>
            try {
                notificationsJson = notificationArray.toList()
            } catch (e: JSONException) {
                LocalNotificationsError.INVALID_NOTIFICATION_FORMAT.reject(call)
                return null
            }

            for (jsonNotification in notificationsJson) {
                val notification: JSObject
                try {
                    val identifier = jsonNotification.getLong("id")
                    if (identifier > Int.MAX_VALUE || identifier < Int.MIN_VALUE) {
                        LocalNotificationsError.IDENTIFIER_NOT_INT.reject(call)
                        return null
                    }
                    notification = JSObject.fromJSONObject(jsonNotification)
                } catch (e: JSONException) {
                    LocalNotificationsError.INVALID_JSON.reject(call, e)
                    return null
                }

                try {
                    resultLocalNotifications.add(buildNotificationFromJSObject(notification))
                } catch (e: ParseException) {
                    LocalNotificationsError.INVALID_DATE_FORMAT.reject(call, e)
                    return null
                }
            }
            return resultLocalNotifications
        }

        @Throws(ParseException::class)
        fun buildNotificationFromJSObject(jsonObject: JSObject): LocalNotification {
            val n = LocalNotification()
            n.source = jsonObject.toString()
            n.id = jsonObject.getInteger("id")
            n.body = jsonObject.getString("body")
            n.largeBody = jsonObject.getString("largeBody")
            n.summaryText = jsonObject.getString("summaryText")
            n.actionTypeId = jsonObject.getString("actionTypeId")
            n.group = jsonObject.getString("group")
            n.sound = jsonObject.getString("sound")
            n.title = jsonObject.getString("title")
            n.smallIcon = jsonObject.getString("smallIcon")
            n.largeIcon = jsonObject.getString("largeIcon")
            n.iconColor = jsonObject.getString("iconColor")
            n.attachments = LocalNotificationAttachment.getAttachments(jsonObject)
            n.groupSummary = jsonObject.getBoolean("groupSummary", false) ?: false
            n.channelId = jsonObject.getString("channelId")
            val schedule = jsonObject.getJSObject("schedule")
            if (schedule != null) {
                n.schedule = LocalNotificationSchedule(schedule)
            }
            // getJSObject() only accepts an object and silently drops any other type
            // (e.g. a plain string), so read the raw value instead.
            n.extra = if (jsonObject.has("extra") && !jsonObject.isNull("extra")) jsonObject.get("extra") else null
            n.ongoing = jsonObject.getBoolean("ongoing", false) ?: false
            n.autoCancel = jsonObject.getBoolean("autoCancel", true) ?: true
            if (jsonObject.has("badge")) {
                n.badge = jsonObject.getInteger("badge")
            }
            if (jsonObject.has("foreground")) {
                n.foreground = jsonObject.getBoolean("foreground", false)
            }
            n.isExactNotification = jsonObject.getBoolean("isExactNotification", true) ?: true
            n.isExactMandatory = jsonObject.getBoolean("isExactMandatory", false) ?: false
            n.cancelled = jsonObject.getBoolean("cancelled", false) ?: false

            try {
                val inboxList = jsonObject.getJSONArray("inboxList")
                if (inboxList != null) {
                    val list = ArrayList<String>()
                    for (i in 0 until inboxList.length()) {
                        list.add(inboxList.getString(i))
                    }
                    n.inboxList = list
                }
            } catch (ex: Exception) {
            }

            return n
        }

        fun getLocalNotificationPendingList(call: PluginCall): List<Int>? {
            var notifications: List<JSONObject>? = null
            try {
                notifications = call.getArray("notifications").toList()
            } catch (e: JSONException) {
            }
            if (notifications == null || notifications.isEmpty()) {
                LocalNotificationsError.INVALID_NOTIFICATIONS_ARRAY.reject(call)
                return null
            }
            val notificationsList = ArrayList<Int>(notifications.size)
            for (notificationToCancel in notifications) {
                try {
                    notificationsList.add(notificationToCancel.getInt("id"))
                } catch (e: JSONException) {
                }
            }
            return notificationsList
        }

        fun buildLocalNotificationPendingList(notifications: List<LocalNotification>): JSObject {
            val result = JSObject()
            val jsArray = JSArray()
            for (notification in notifications) {
                val jsNotification = JSObject()
                jsNotification.put("id", notification.id)
                jsNotification.put("title", notification.title)
                jsNotification.put("body", notification.body)
                val schedule = notification.schedule
                if (schedule != null) {
                    val jsSchedule = JSObject()
                    jsSchedule.put("at", schedule.at)
                    jsSchedule.put("every", schedule.every)
                    jsSchedule.put("count", schedule.count)
                    jsSchedule.put("on", schedule.onObj)
                    jsSchedule.put("repeats", schedule.isRepeating())
                    jsNotification.put("schedule", jsSchedule)
                }

                jsNotification.put("extra", notification.extra)

                notification.sound?.let { jsNotification.put("sound", it) }
                notification.badge?.let { jsNotification.put("badge", it) }
                notification.foreground?.let { jsNotification.put("foreground", it) }
                jsNotification.put("isExactNotification", notification.isExactNotification)
                jsNotification.put("isExactMandatory", notification.isExactMandatory)

                jsArray.put(jsNotification)
            }
            result.put("notifications", jsArray)
            return result
        }
    }
}
