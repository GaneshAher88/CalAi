package com.ganeshaher.calai.domain

enum class Action { CREATE, LIST, MOVE, DELETE, REMIND, CLARIFY }

data class CalendarCommand(
    val action: Action,
    val title: String = "",
    val start: Long? = null,
    val end: Long? = null,
    val reminder: Int? = null,
    val question: String = ""
)

data class Event(
    val id: String,
    val title: String,
    val start: Long,
    val end: Long,
    val reminder: Int? = null,
    val allDay: Boolean = false,
    val version: String? = null,
    val description: String = ""
)

data class Review(
    val action: Action,
    val event: Event,
    val original: Event? = null
)

fun interface CommandParser {
    suspend fun parse(transcript: String): CalendarCommand
}

interface CalendarRepository {
    suspend fun list(from: Long, until: Long, query: String = ""): List<Event>
    suspend fun apply(review: Review)
}
