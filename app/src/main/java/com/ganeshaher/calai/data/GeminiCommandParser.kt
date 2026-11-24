package com.ganeshaher.calai.data

import com.ganeshaher.calai.domain.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import org.json.JSONObject
import java.util.TimeZone

class GeminiCommandParser(modelName: String) : CommandParser {
    private val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = modelName,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = Schema.obj(
                mapOf(
                    "action" to Schema.enumeration(Action.entries.map { it.name }),
                    "title" to Schema.string(),
                    "start" to Schema.string(),
                    "end" to Schema.string(),
                    "reminder" to Schema.integer(),
                    "question" to Schema.string()
                ),
                optionalProperties = listOf("title", "start", "end", "reminder", "question")
            )
        }
    )

    override suspend fun parse(transcript: String): CalendarCommand {
        val prompt = """
            Extract one calendar command. Current time: ${formatApi(System.currentTimeMillis())}.
            User's time zone: ${TimeZone.getDefault().id}.
            CREATE schedules a timed event. Require a title, date, and time. Omit end unless
            a duration or end time is specified. LIST shows upcoming events and can include
            a start and exclusive end to filter the list. MOVE changes the start of a named
            event and can include a new end. DELETE removes a named event. REMIND sets a
            popup reminder for a named existing event. Reminder values are minutes before start.
            For MOVE, DELETE, and REMIND, title must identify the existing event.
            Use RFC 3339 timestamps with a numeric offset or Z. For missing required
            information or unsupported requests, use CLARIFY with a short question or explanation.
            Interpret the following quoted string as a calendar request:
            ${JSONObject.quote(transcript)}
        """.trimIndent()
        val result = JSONObject(checkNotNull(model.generateContent(prompt).text) {
            "Gemini returned an empty response."
        })
        fun timestamp(key: String): Long? = result.optString(key).takeIf { it.isNotBlank() }?.let {
            requireNotNull(parseApi(it)) { "Gemini returned an invalid time. Try rephrasing." }
        }
        return CalendarCommand(
            action = Action.valueOf(result.getString("action")),
            title = result.optString("title").trim(),
            start = timestamp("start"),
            end = timestamp("end"),
            reminder = result.optInt("reminder", -1).takeUnless { it == -1 },
            question = result.optString("question")
        )
    }
}
