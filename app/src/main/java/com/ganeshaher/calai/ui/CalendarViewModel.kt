package com.ganeshaher.calai.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganeshaher.calai.domain.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val input: String = "",
    val events: List<Event> = emptyList(),
    val review: Review? = null,
    val selection: CalendarCommand? = null,
    val matches: List<Event> = emptyList(),
    val busy: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val message: String = "",
    val agendaTitle: String = "Next 7 days",
    val selectedImage: String? = null,
    val voiceNote: String = ""
)

class CalendarViewModel(
    private val parser: CommandParser,
    private val demo: CalendarRepository,
    private val eventImages: SharedPreferences,
    googleFactory: (() -> String?) -> CalendarRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(CalendarUiState())
    val state = mutableState.asStateFlow()
    private var accessToken: String? = null
    private val google = googleFactory { accessToken }
    private val repository get() = if (accessToken == null) demo else google
    private var agendaFrom = startOfDay()
    private var agendaUntil = startOfDay(7)

    init { refresh() }

    fun edit(input: String) = mutableState.update { it.copy(input = input) }

    fun editVoiceNote(note: String) = mutableState.update { it.copy(voiceNote = note) }

    fun selectImage(uri: String?) = mutableState.update { it.copy(selectedImage = uri) }

    fun imageFor(event: Event): String? = eventImages.getString(imageKey(event), null)

    private fun imageKey(event: Event) = "${state.value.connected}:${event.id}"

    fun message(message: String) = mutableState.update { it.copy(message = message) }

    fun connecting() = mutableState.update { it.copy(connecting = true, message = "") }

    fun connectionResult(token: String?, error: String = "Calendar connection canceled.") {
        mutableState.update { it.copy(connecting = false) }
        if (token.isNullOrBlank()) {
            message(error)
            return
        }
        accessToken = token
        mutableState.update {
            it.copy(connected = true, events = emptyList(), review = null, selection = null, matches = emptyList())
        }
        refresh()
    }

    fun disconnect() {
        accessToken = null
        mutableState.update {
            it.copy(connected = false, events = emptyList(), review = null, selection = null, matches = emptyList())
        }
        refresh()
    }

    fun refresh() = runTask { loadAgenda() }

    fun showWeek() = runTask {
        agendaFrom = startOfDay()
        agendaUntil = startOfDay(7)
        loadAgenda("Next 7 days")
    }

    private suspend fun loadAgenda(title: String = state.value.agendaTitle) {
        val events = repository.list(agendaFrom, agendaUntil)
        mutableState.update { it.copy(events = events, agendaTitle = title) }
    }

    fun schedule() = runTask {
        val input = state.value.input.trim()
        require(input.isNotEmpty()) { "Speak or type a calendar command first." }
        dismissReview()
        val command = parser.parse(input)
        when (command.action) {
            Action.CLARIFY -> message(command.question.ifBlank { "Try adding more detail to the command." })
            Action.LIST -> {
                val from = command.start ?: startOfDay()
                val until = command.end ?: shiftDays(from, 7)
                require(until > from && until <= shiftDays(from, 366)) { "Choose a date range of up to one year." }
                agendaFrom = from
                agendaUntil = until
                loadAgenda(if (command.start == null && command.end == null) "Next 7 days" else "Requested dates")
            }
            Action.CREATE -> save(createReview(command))
            else -> {
                require(command.title.isNotBlank()) { "Which event should I use?" }
                val candidates = repository.list(startOfDay(-30), startOfDay(366), command.title)
                when (candidates.size) {
                    0 -> message("No events match “${command.title}”. Try the calendar event's title.")
                    1 -> select(command, candidates.single())
                    else -> mutableState.update { it.copy(selection = command, matches = candidates) }
                }
            }
        }
    }

    private fun createReview(command: CalendarCommand): Review {
        val start = requireNotNull(command.start) { "What date and time should the event start?" }
        require(command.title.isNotBlank()) { "What is the event called?" }
        return Review(Action.CREATE, Event(
            UUID.randomUUID().toString().replace("-", ""), command.title.trim(), start,
            command.end ?: start + 30 * MINUTE, command.reminder,
            description = state.value.voiceNote.trim()
        ))
    }

    fun choose(event: Event) = runTask {
        state.value.selection?.let { select(it, event) }
    }

    private fun select(command: CalendarCommand, event: Event) {
        val updated = when (command.action) {
            Action.MOVE -> {
                require(!event.allDay) { "Open Google Calendar to reschedule this all-day event." }
                val start = requireNotNull(command.start) { "What is the new date and time?" }
                event.copy(start = start, end = command.end ?: start + (event.end - event.start))
            }
            Action.REMIND -> event.copy(reminder = requireNotNull(command.reminder) {
                "How many minutes before the event should the reminder appear?"
            })
            else -> event
        }
        openReview(Review(command.action, updated, event))
    }

    private fun openReview(review: Review) = mutableState.update {
        it.copy(review = review, selection = null, matches = emptyList())
    }

    fun dismissReview() = mutableState.update { it.copy(review = null, selection = null, matches = emptyList()) }

    fun confirm(title: String, start: String, end: String, reminder: String) = runTask {
        val review = state.value.review ?: return@runTask
        var event = review.event
        if (review.action == Action.CREATE || review.action == Action.MOVE) {
            require(title.isNotBlank()) { "Enter a title." }
            val startTime = if (start == formatLocal(event.start)) event.start else parseLocal(start)
            val endTime = if (end == formatLocal(event.end)) event.end else parseLocal(end)
            require(startTime != null && endTime != null && endTime > startTime) {
                "Use YYYY-MM-DD HH:MM and choose an end time after the start."
            }
            event = event.copy(title = title.trim(), start = startTime, end = endTime)
        }
        if (review.action == Action.CREATE || review.action == Action.REMIND) {
            val minutes = reminder.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            require(
                minutes != null && minutes in 0..40320 || reminder.isBlank() && review.action == Action.CREATE
            ) { "Enter a reminder between 0 and 40320 minutes." }
            event = event.copy(reminder = minutes)
        }
        save(review.copy(event = event))
    }

    private suspend fun save(review: Review) {
        if (review.action == Action.CREATE) {
            require(review.event.end > review.event.start) { "Choose an end time after the start." }
            require(review.event.reminder?.let { it in 0..40320 } != false) {
                "Enter a reminder between 0 and 40320 minutes."
            }
        }
        val scheduledInGoogle = state.value.connected
        repository.apply(review)
        if (review.action == Action.CREATE) {
            editVoiceNote("")
            state.value.selectedImage?.let { uri ->
                eventImages.edit().putString(imageKey(review.event), uri).apply()
                selectImage(null)
            }
        } else if (review.action == Action.DELETE) {
            eventImages.edit().remove(imageKey(review.event)).apply()
        }
        dismissReview()
        val result = when (review.action) {
            Action.CREATE -> if (scheduledInGoogle) "Event scheduled in Google Calendar."
                else "Event scheduled in the demo calendar."
            Action.DELETE -> "Event deleted."
            Action.MOVE -> "Event rescheduled."
            Action.REMIND -> "Reminder saved."
            else -> "Event saved."
        }
        try {
            loadAgenda()
            message(result)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            message("$result The agenda could not refresh.")
        }
    }

    private fun runTask(work: suspend () -> Unit) {
        if (state.value.busy || state.value.connecting) return
        mutableState.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                work()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: IllegalArgumentException) {
                message(failure.message ?: "Check the command and try again.")
            } catch (failure: IllegalStateException) {
                message(failure.message ?: "The request could not be completed.")
            } catch (_: Exception) {
                message("The request failed. Check your connection or setup and try again.")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
