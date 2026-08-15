package com.capacitorjs.plugins.localnotifications

import android.content.Context
import android.content.SharedPreferences
import com.getcapacitor.JSObject
import java.text.ParseException
import org.json.JSONException

/**
 * Abstracts storage for notification data (and action-type groups).
 */
class NotificationStorage(private val context: Context) {

    fun appendNotifications(localNotifications: List<LocalNotification>) {
        val editor = getStorage(NOTIFICATION_STORE_ID).edit()
        for (request in localNotifications) {
            if (request.isScheduled()) {
                val id = request.id ?: continue
                editor.putString(id.toString(), request.source)
            }
        }
        editor.apply()
    }

    fun getSavedNotificationIds(): List<String> {
        val all = getStorage(NOTIFICATION_STORE_ID).all
        return if (all != null) ArrayList(all.keys) else ArrayList()
    }

    fun getSavedNotifications(): List<LocalNotification> {
        val all = getStorage(NOTIFICATION_STORE_ID).all ?: return ArrayList()
        val notifications = ArrayList<LocalNotification>()
        for (key in all.keys) {
            val notificationString = all[key] as? String
            val jsNotification = getNotificationFromJSONString(notificationString)
            if (jsNotification != null) {
                try {
                    notifications.add(LocalNotification.buildNotificationFromJSObject(jsNotification))
                } catch (ex: ParseException) {
                }
            }
        }
        return notifications
    }

    fun getNotificationFromJSONString(notificationString: String?): JSObject? {
        if (notificationString == null) return null
        return try {
            JSObject(notificationString)
        } catch (ex: JSONException) {
            null
        }
    }

    fun getSavedNotificationAsJSObject(key: String): JSObject? {
        val notificationString = try {
            getStorage(NOTIFICATION_STORE_ID).getString(key, null)
        } catch (ex: ClassCastException) {
            return null
        } ?: return null
        return try {
            JSObject(notificationString)
        } catch (ex: JSONException) {
            null
        }
    }

    fun getSavedNotification(key: String): LocalNotification? {
        val jsNotification = getSavedNotificationAsJSObject(key) ?: return null
        return try {
            LocalNotification.buildNotificationFromJSObject(jsNotification)
        } catch (ex: ParseException) {
            null
        }
    }

    fun deleteNotification(id: String) {
        val editor = getStorage(NOTIFICATION_STORE_ID).edit()
        editor.remove(id)
        editor.apply()
    }

    /**
     * Mark a stored notification cancelled in place, without disturbing any of
     * its other persisted fields. Used when cancel()/cancelAll() preserves a
     * still-delivered record instead of deleting it, so later classification
     * and reboot-restore can tell it's no longer actually scheduled.
     */
    fun setCancelled(id: String, cancelled: Boolean) {
        val json = getSavedNotificationAsJSObject(id) ?: return
        json.put("cancelled", cancelled)
        val editor = getStorage(NOTIFICATION_STORE_ID).edit()
        editor.putString(id, json.toString())
        editor.apply()
    }

    private fun getStorage(key: String): SharedPreferences =
        context.getSharedPreferences(key, Context.MODE_PRIVATE)

    /**
     * Writes action types (actions shown in a notification) to storage,
     * overriding previous data.
     */
    fun writeActionGroup(typesMap: Map<String, Array<NotificationAction>>?) {
        if (typesMap == null) return
        for (id in typesMap.keys) {
            val editor = getStorage(ACTION_TYPES_ID + id).edit()
            editor.clear()
            val notificationActions = typesMap[id] ?: continue
            editor.putInt("count", notificationActions.size)
            for (i in notificationActions.indices) {
                editor.putString("id$i", notificationActions[i].id)
                editor.putString("title$i", notificationActions[i].title)
                editor.putBoolean("input$i", notificationActions[i].isInput())
            }
            editor.apply()
        }
    }

    /**
     * Retrieve the array of notification actions for an action-type id.
     */
    fun getActionGroup(forId: String): Array<NotificationAction> {
        val storage = getStorage(ACTION_TYPES_ID + forId)
        val count = storage.getInt("count", 0)
        return Array(count) { i ->
            val id = storage.getString("id$i", "")
            val title = storage.getString("title$i", "")
            val input = storage.getBoolean("input$i", false)
            NotificationAction(id, title, input)
        }
    }

    companion object {
        private const val NOTIFICATION_STORE_ID = "NOTIFICATION_STORE"
        private const val ACTION_TYPES_ID = "ACTION_TYPE_STORE"
    }
}
