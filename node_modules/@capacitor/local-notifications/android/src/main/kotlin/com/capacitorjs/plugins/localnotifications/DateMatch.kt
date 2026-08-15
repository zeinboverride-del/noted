package com.capacitorjs.plugins.localnotifications

import java.util.Calendar
import java.util.Date

/**
 * Holds logic for "on" triggers (matching a specific set of date components).
 */
class DateMatch {

    var year: Int? = null
    var month: Int? = null
    var day: Int? = null
    var weekday: Int? = null
    var hour: Int? = null
    var minute: Int? = null
    var second: Int? = null

    // One of the Calendar constants; -1 until the finest supplied unit is known.
    var unit: Int = -1

    private fun buildCalendar(date: Date): Calendar {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    fun nextTrigger(date: Date): Long {
        val current = buildCalendar(date)
        val next = buildNextTriggerTime(date)
        return postponeTriggerIfNeeded(current, next)
    }

    private fun postponeTriggerIfNeeded(current: Calendar, next: Calendar): Long {
        if (next.timeInMillis <= current.timeInMillis && unit != -1) {
            val incrementUnit = when (unit) {
                Calendar.YEAR, Calendar.MONTH -> Calendar.YEAR
                Calendar.DAY_OF_MONTH -> Calendar.MONTH
                Calendar.DAY_OF_WEEK -> Calendar.WEEK_OF_MONTH
                Calendar.HOUR_OF_DAY -> Calendar.DAY_OF_MONTH
                Calendar.MINUTE -> Calendar.HOUR_OF_DAY
                Calendar.SECOND -> Calendar.MINUTE
                else -> -1
            }
            if (incrementUnit != -1) {
                next.set(incrementUnit, next.get(incrementUnit) + 1)
            }
        }
        return next.timeInMillis
    }

    private fun buildNextTriggerTime(date: Date): Calendar {
        val next = buildCalendar(date)
        year?.let { next.set(Calendar.YEAR, it); if (unit == -1) unit = Calendar.YEAR }
        month?.let { next.set(Calendar.MONTH, it); if (unit == -1) unit = Calendar.MONTH }
        day?.let { next.set(Calendar.DAY_OF_MONTH, it); if (unit == -1) unit = Calendar.DAY_OF_MONTH }
        weekday?.let { next.set(Calendar.DAY_OF_WEEK, it); if (unit == -1) unit = Calendar.DAY_OF_WEEK }
        hour?.let { next.set(Calendar.HOUR_OF_DAY, it); if (unit == -1) unit = Calendar.HOUR_OF_DAY }
        minute?.let { next.set(Calendar.MINUTE, it); if (unit == -1) unit = Calendar.MINUTE }
        second?.let { next.set(Calendar.SECOND, it); if (unit == -1) unit = Calendar.SECOND }
        return next
    }

    fun toMatchString(): String =
        listOf(year, month, day, weekday, hour, minute, second, unit)
            .joinToString(separator) { it?.toString() ?: "*" }

    companion object {
        private const val separator = " "

        fun fromMatchString(matchString: String): DateMatch {
            val date = DateMatch()
            val split = matchString.split(separator)
            if (split.size == 7) {
                date.year = valueFromCronElement(split[0])
                date.month = valueFromCronElement(split[1])
                date.day = valueFromCronElement(split[2])
                date.weekday = valueFromCronElement(split[3])
                date.hour = valueFromCronElement(split[4])
                date.minute = valueFromCronElement(split[5])
                date.unit = valueFromCronElement(split[6]) ?: -1
            }
            if (split.size == 8) {
                date.year = valueFromCronElement(split[0])
                date.month = valueFromCronElement(split[1])
                date.day = valueFromCronElement(split[2])
                date.weekday = valueFromCronElement(split[3])
                date.hour = valueFromCronElement(split[4])
                date.minute = valueFromCronElement(split[5])
                date.second = valueFromCronElement(split[6])
                date.unit = valueFromCronElement(split[7]) ?: -1
            }
            return date
        }

        private fun valueFromCronElement(token: String): Int? =
            try {
                token.toInt()
            } catch (e: NumberFormatException) {
                null
            }
    }
}
