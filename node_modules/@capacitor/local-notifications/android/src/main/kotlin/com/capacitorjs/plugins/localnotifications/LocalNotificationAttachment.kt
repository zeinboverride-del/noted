package com.capacitorjs.plugins.localnotifications

import com.getcapacitor.JSObject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class LocalNotificationAttachment {

    var id: String? = null
    var url: String? = null
    var options: JSONObject? = null

    companion object {
        fun getAttachments(notification: JSObject): List<LocalNotificationAttachment> {
            val attachmentsList = ArrayList<LocalNotificationAttachment>()
            var attachments: JSONArray? = null
            try {
                attachments = notification.getJSONArray("attachments")
            } catch (e: Exception) {
            }
            if (attachments != null) {
                for (i in 0 until attachments.length()) {
                    val newAttachment = LocalNotificationAttachment()
                    var jsonObject: JSONObject? = null
                    try {
                        jsonObject = attachments.getJSONObject(i)
                    } catch (e: JSONException) {
                    }
                    if (jsonObject != null) {
                        var jsObject: JSObject? = null
                        try {
                            jsObject = JSObject.fromJSONObject(jsonObject)
                        } catch (e: JSONException) {
                        }
                        newAttachment.id = jsObject?.getString("id")
                        newAttachment.url = jsObject?.getString("url")
                        try {
                            newAttachment.options = jsObject?.getJSONObject("options")
                        } catch (e: JSONException) {
                        }
                        attachmentsList.add(newAttachment)
                    }
                }
            }
            return attachmentsList
        }
    }
}
