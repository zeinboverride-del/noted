package com.capacitorjs.plugins.localnotifications

import android.text.format.DateUtils
import com.getcapacitor.JSObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class LocalNotificationSchedule {

    var at: Date? = null
    var repeats: Boolean? = null
    var every: String? = null
    var count: Int = 1
    var on: DateMatch? = null

    private var whileIdle: Boolean = false
    private var scheduleObj: JSObject? = null

    constructor()

    constructor(schedule: JSObject) {
        scheduleObj = schedule
        every = schedule.getString("every")
        count = schedule.getInteger("count", 1) ?: 1
        repeats = schedule.getBool("repeats")

        val dateString = schedule.getString("at")
        if (dateString != null) {
            val sdf = SimpleDateFormat(JS_DATE_FORMAT)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            at = sdf.parse(dateString)
        }

        val onJson = schedule.getJSObject("on")
        if (onJson != null) {
            val match = DateMatch()
            match.year = onJson.getInteger("year")
            match.month = onJson.getInteger("month")
            match.day = onJson.getInteger("day")
            match.weekday = onJson.getInteger("weekday")
            match.hour = onJson.getInteger("hour")
            match.minute = onJson.getInteger("minute")
            match.second = onJson.getInteger("second")
            on = match
        }

        whileIdle = schedule.getBoolean("allowWhileIdle", false) ?: false
    }

    val onObj: JSObject?
        get() = scheduleObj?.getJSObject("on")

    fun allowWhileIdle(): Boolean = whileIdle

    fun isRepeating(): Boolean = repeats == true

    /** Whether this schedule keeps firing indefinitely (never settles into a final "triggered" state). */
    fun isPerpetual(): Boolean = every != null || on != null || isRepeating()

    val everyInterval: Long?
        get() {
            val e = every ?: return null
            return when (e) {
                "year" -> count * DateUtils.WEEK_IN_MILLIS * 52
                "month" -> count * 30 * DateUtils.DAY_IN_MILLIS
                "two-weeks" -> count * 2 * DateUtils.WEEK_IN_MILLIS
                "week" -> count * DateUtils.WEEK_IN_MILLIS
                "day" -> count * DateUtils.DAY_IN_MILLIS
                "hour" -> count * DateUtils.HOUR_IN_MILLIS
                "minute" -> count * DateUtils.MINUTE_IN_MILLIS
                "second" -> count * DateUtils.SECOND_IN_MILLIS
                else -> null
            }
        }

    companion object {
        const val JS_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    }
}
