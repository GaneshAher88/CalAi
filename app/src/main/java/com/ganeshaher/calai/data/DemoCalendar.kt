package com.ganeshaher.calai.data

import android.content.SharedPreferences
import com.ganeshaher.calai.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class DemoCalendar(private val preferences: SharedPreferences) : CalendarRepository {
    private fun events(): List<Event> {
        val array = JSONArray(preferences.getString("events", "[]"))
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Event(
                item.getString("id"), item.getString("title"), item.getLong("start"),
                item.getLong("end"), item.optInt("reminder", -1).takeIf { it >= 0 },
                description = item.optString("description")
            )
        }
    }

    override suspend fun list(from: Long, until: Long, query: String): List<Event> =
        events().filter { it.end > from && it.start < until && it.title.contains(query, true) }
            .sortedBy { it.start }

    override suspend fun apply(review: Review) {
        val events = events().filterNot { it.id == review.event.id }.toMutableList()
        if (review.action != Action.DELETE) events.add(review.event)
        val array = JSONArray()
        for (event in events) {
            array.put(JSONObject().apply {
                put("id", event.id)
                put("title", event.title)
                put("start", event.start)
                put("end", event.end)
                put("reminder", event.reminder)
                put("description", event.description)
            })
        }
        preferences.edit().putString("events", array.toString()).apply()
    }
}

class DemoCommandParser : CommandParser {
    override suspend fun parse(transcript: String): CalendarCommand {
        val input = transcript.trim().trimEnd('.', '!', '?')
        val clock = Regex(
            """\b(today|tomorrow)\s+at\s+(\d{1,2})(?::(\d{2}))?\s*([ap]m)?\b""",
            RegexOption.IGNORE_CASE
        )
        val reminder = Regex("""\b(\d+)\s+minutes? before\b""", RegexOption.IGNORE_CASE)
            .find(input)?.groupValues?.get(1)?.toIntOrNull()
        val match = clock.find(input)
        val start = match?.let {
            val (day, hour, minute, meridiem) = it.destructured
            val hours = hour.toInt()
            val minutes = minute.ifEmpty { "0" }.toInt()
            if (minutes > 59 || hours !in (if (meridiem.isBlank()) 0..23 else 1..12)) {
                return CalendarCommand(Action.CLARIFY, question = "Enter a valid time, such as tomorrow at 4 PM.")
            }
            val hourOfDay = if (meridiem.isBlank()) hours
                else hours % 12 + if (meridiem.equals("pm", true)) 12 else 0
            Calendar.getInstance().apply {
                timeInMillis = startOfDay(if (day.equals("tomorrow", true)) 1 else 0)
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minutes)
            }.timeInMillis
        }
        fun after(prefix: String) = input.substring(prefix.length).trim()
        return when {
            input.startsWith("list ", true) || input.startsWith("show ", true) ->
                CalendarCommand(Action.LIST)
            input.startsWith("delete ", true) ->
                CalendarCommand(Action.DELETE, title = after("delete "))
            input.startsWith("move ", true) && match != null -> CalendarCommand(
                Action.MOVE, input.substring(5, match.range.first).trim().removeSuffix(" to"), start
            )
            input.startsWith("remind me about ", true) && reminder != null -> CalendarCommand(
                Action.REMIND, after("remind me about ").replace(
                    Regex("""\s+\d+\s+minutes? before$""", RegexOption.IGNORE_CASE), ""
                ), reminder = reminder
            )
            match != null && (input.startsWith("schedule ", true) || input.startsWith("create ", true)) ->
                CalendarCommand(Action.CREATE, input.substringBefore(' ')
                    .let { input.substring(it.length, match.range.first).trim() }, start, reminder = reminder)
            else -> CalendarCommand(
                Action.CLARIFY, question = "Try “Schedule Dentist tomorrow at 4 PM” or “Show this week”."
            )
        }
    }
}
