package com.capacitorjs.plugins.localnotifications

import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall

/**
 * OutSystems structured error codes (`OS-PLUG-LNOT-NNNN`). Identical codes and
 * messages to the Cordova plugin so both share one error contract.
 */
enum class LocalNotificationsError(val code: String, val message: String) {
    INVALID_NOTIFICATIONS_ARRAY("OS-PLUG-LNOT-0001", "Must provide a notifications array as the notifications option."),
    MISSING_IDENTIFIER("OS-PLUG-LNOT-0002", "Notification is missing an identifier."),
    NOTIFICATIONS_DISABLED("OS-PLUG-LNOT-0005", "Notifications are not enabled on this device."),
    INVALID_COLOR("OS-PLUG-LNOT-0006", "Invalid color provided. Must be a hex string (e.g. #ff0000)."),
    INVALID_NOTIFICATION_FORMAT("OS-PLUG-LNOT-0007", "Provided notification format is invalid."),
    INVALID_DATE_FORMAT("OS-PLUG-LNOT-0008", "Invalid date format sent to the plugin."),
    IDENTIFIER_NOT_INT("OS-PLUG-LNOT-0009", "The identifier must be a 32-bit integer."),
    INVALID_REMOVE_LIST("OS-PLUG-LNOT-0011", "Expected notifications to be a list of notification objects."),
    MISSING_IDS("OS-PLUG-LNOT-0012", "Must provide an ids array."),
    INVALID_JSON("OS-PLUG-LNOT-0014", "Invalid JSON object sent to the plugin."),
    CHANNEL_MISSING_IDENTIFIER("OS-PLUG-LNOT-0015", "Channel is missing an identifier."),
    CHANNEL_MISSING_NAME("OS-PLUG-LNOT-0016", "Channel is missing a name."),
    SCHEDULED_INEXACT(
        "OS-PLUG-LNOT-0017",
        "Unable to schedule an exact alarm due to lack of permissions. Scheduled as an inexact alarm instead."
    ),
    EXACT_ALARM_PERMISSION_REQUIRED(
        "OS-PLUG-LNOT-0018",
        "Unable to schedule an exact alarm due to lack of permissions."
    );

    fun reject(call: PluginCall) {
        call.reject(message, code)
    }

    fun reject(call: PluginCall, ex: Exception) {
        call.reject(message, code, ex)
    }

    fun toJson(): JSObject = JSObject().put("code", code).put("message", message)
}
