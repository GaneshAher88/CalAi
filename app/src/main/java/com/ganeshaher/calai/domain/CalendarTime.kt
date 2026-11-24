package com.ganeshaher.calai.domain

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val MINUTE = 60_000L

private fun parse(value: String, pattern: String): Long? {
    val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
    val position = ParsePosition(0)
    val result = formatter.parse(value, position)
    return result?.time?.takeIf { position.index == value.length }
}

fun parseLocal(value: String): Long? =
    value.trim().takeIf { it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}")) }
        ?.let { parse(it, "yyyy-MM-dd HH:mm") }

fun formatLocal(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(time))

fun formatApi(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(time))

fun parseApi(value: String): Long? =
    parse(value, "yyyy-MM-dd'T'HH:mm:ssXXX")
        ?: parse(
            value.replace(Regex("\\.(\\d+)"), { "." + it.groupValues[1].padEnd(3, '0').take(3) }),
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )

fun parseAllDay(value: String): Long? = parse(value, "yyyy-MM-dd")

fun startOfDay(daysFromToday: Int = 0): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, daysFromToday)
}.timeInMillis

fun shiftDays(time: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = time
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis
