package com.ganeshaher.calai.ui

import androidx.compose.animation.AnimatedVisibility

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ganeshaher.calai.domain.Action
import com.ganeshaher.calai.domain.Event
import com.ganeshaher.calai.domain.Review
import com.ganeshaher.calai.domain.formatLocal
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    geminiEnabled: Boolean,
    speak: () -> Unit,
    dictateNote: () -> Unit,
    connect: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val available = !state.busy && !state.connecting
    val focusManager = LocalFocusManager.current
    val resolver = LocalContext.current.contentResolver
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.selectImage(uri.toString())
            } catch (_: SecurityException) {
                viewModel.message("CalAi couldn't keep access to this photo. Try another image.")
            }
        }
    }
    val addInteraction = remember { MutableInteractionSource() }
    val addPressed by addInteraction.collectIsPressedAsState()
    val addScale by animateFloatAsState(if (addPressed) 0.97f else 1f, label = "Add event press")
    Scaffold(
        modifier = Modifier.background(
            Brush.verticalGradient(
                0f to Color(0xFFF9F6FD),
                0.48f to Color(0xFFECE1FA),
                0.78f to MaterialTheme.colorScheme.background
            )
        ),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("CalAi", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Voice calendar assistant", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                if (state.connected) "Google Calendar" else "Demo calendar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (state.connected) "Changes apply to your primary calendar."
                                else "Events stay on this device. Reminders are saved without notifications.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(
                                onClick = if (state.connected) viewModel::disconnect else connect,
                                enabled = available
                            ) {
                                Text(if (state.connected) "Switch to demo" else "Connect Google Calendar")
                            }
                            if (state.connected) TextButton(onClick = connect, enabled = available) {
                                Text("Reconnect")
                            }
                        }
                    }
                }

                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Create event",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                VoiceWaveform(emphasized = !available)
                            }
                            GalleryPanel(
                                imageUri = state.selectedImage,
                                enabled = available,
                                pick = { gallery.launch(arrayOf("image/*")) },
                                remove = { viewModel.selectImage(null) }
                            )
                            OutlinedTextField(
                                value = state.input,
                                onValueChange = viewModel::edit,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Calendar command") },
                                placeholder = { Text("Schedule Dentist tomorrow at 4 PM") },
                                shape = MaterialTheme.shapes.medium,
                                colors = eventFieldColors(),
                                enabled = available,
                                minLines = 2,
                                maxLines = 4
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = speak,
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                    enabled = available
                                ) { Text("Speak") }
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.schedule()
                                    },
                                    modifier = Modifier.weight(1.35f).heightIn(min = 52.dp).graphicsLayer {
                                        scaleX = addScale
                                        scaleY = addScale
                                    },
                                    interactionSource = addInteraction,
                                    enabled = available && state.input.isNotBlank()
                                ) {
                                    Text("Add event", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            VoiceNoteEditor(
                                note = state.voiceNote,
                                enabled = available,
                                edit = viewModel::editVoiceNote,
                                dictate = {
                                    focusManager.clearFocus()
                                    dictateNote()
                                }
                            )
                            Text(
                                if (geminiEnabled) "Your command is processed by Gemini."
                                else "Try “Schedule Dentist tomorrow at 4 PM”.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!available) item(key = "operation-progress") {
                    LinearProgressIndicator(Modifier.fillMaxWidth().animateItem())
                }

                if (state.message.isNotBlank()) item(key = "operation-message") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().animateItem().semantics { liveRegion = LiveRegionMode.Polite },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(state.message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (state.matches.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Which event?", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = viewModel::dismissReview, enabled = available) { Text("Cancel") }
                        }
                    }
                    items(state.matches, key = { "match-${it.id}" }) { event ->
                        OutlinedButton(
                            onClick = { viewModel.choose(event) },
                            modifier = Modifier.animateItem(),
                            enabled = available
                        ) {
                            Text("${event.title} · ${formatLocal(event.start)}")
                        }
                    }
                }

                state.review?.let { review ->
                    item(key = "event-review") {
                        Box(Modifier.animateItem()) {
                            key(review) {
                                EventReview(review, available, state.connected, viewModel::dismissReview, viewModel::confirm)
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Agenda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(state.agendaTitle, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = if (state.agendaTitle == "Next 7 days") viewModel::refresh else viewModel::showWeek,
                            enabled = available
                        ) {
                            Text(if (state.agendaTitle == "Next 7 days") "Refresh" else "Show next 7 days")
                        }
                    }
                }

                if (state.events.isEmpty() && available) item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text("No events in this date range.", Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(state.events, key = { "agenda-${it.id}" }) { event ->
                    OutlinedCard(Modifier.fillMaxWidth().animateItem()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            viewModel.imageFor(event)?.let { uri ->
                                EventImage(uri, Modifier.fillMaxWidth().height(132.dp).clip(MaterialTheme.shapes.medium))
                            }
                            Text(
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(event.start)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            EventSummary(event, showDate = false)
                            if (event.description.isNotBlank()) {
                                Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            event.reminder?.let {
                                Text("Reminder · $it min before", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteEditor(note: String, enabled: Boolean, edit: (String) -> Unit, dictate: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            TextButton(
                onClick = {
                    if (expanded || note.isNotBlank()) dictate()
                    else expanded = true
                },
                enabled = enabled
            ) {
                VoiceWaveform(emphasized = expanded || note.isNotBlank())
                Spacer(Modifier.width(8.dp))
                Text(if (expanded || note.isNotBlank()) "Dictate note" else "Add voice note")
            }
            AnimatedVisibility(expanded || note.isNotBlank()) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = edit,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Event note") },
                        placeholder = { Text("Dictate or type a note") },
                        colors = eventFieldColors(),
                        shape = MaterialTheme.shapes.medium,
                        enabled = enabled,
                        maxLines = 5
                    )
                    Text(
                        "Saved as text in the event description.",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = { edit(""); expanded = false },
                        enabled = enabled
                    ) { Text("Remove note") }
                }
            }
        }
    }
}

@Composable
private fun GalleryPanel(imageUri: String?, enabled: Boolean, pick: () -> Unit, remove: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(148.dp).clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(Color(0xFFDBECFF), Color(0xFFE5D4FF), Color(0xFFF7DAEC))))
            .clickable(enabled = enabled, onClickLabel = if (imageUri == null) "Choose event image" else "Change event image", onClick = pick),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri != null) {
            EventImage(imageUri, Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Text("+", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text("Choose event image", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (imageUri != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Photo stays on this device", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = remove, enabled = enabled) { Text("Remove") }
        }
    }
}

@Composable
private fun EventImage(uriString: String, modifier: Modifier) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uriString, resolver) {
        value = null
        value = withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inJustDecodeBounds = false
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1200) {
                    options.inSampleSize *= 2
                }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            } catch (_: java.io.IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = "Event photo", modifier = modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun VoiceWaveform(emphasized: Boolean) {
    val progress = rememberInfiniteTransition(label = "Voice waveform").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "Waveform phase"
    )
    val color = MaterialTheme.colorScheme.secondary
    val strength by animateFloatAsState(if (emphasized) 1f else 0.64f, label = "Waveform strength")
    Canvas(Modifier.size(width = 34.dp, height = 24.dp)) {
        repeat(5) { index ->
            val x = size.width * (index + 1) / 6
            val amplitude = .23f - .045f * kotlin.math.abs(index - 2)
            val halfHeight = size.height * (.12f + amplitude * strength * (.5f + .5f * sin(progress.value + index)))
            drawLine(
                color = color,
                start = Offset(x, center.y - halfHeight),
                end = Offset(x, center.y + halfHeight),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun eventFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLabelColor = MaterialTheme.colorScheme.secondary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.secondary,
    unfocusedBorderColor = Color.Transparent
)

@Composable
private fun EventSummary(event: Event, showDate: Boolean = true) {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val start = Date(event.start)
    val end = Date(event.end)
    val startDate = date.format(start)
    val endDate = date.format(end)
    val timing = when {
        event.allDay && showDate -> "$startDate · All day"
        event.allDay -> "All day"
        startDate != endDate -> "$startDate, ${time.format(start)} – $endDate, ${time.format(end)}"
        showDate -> "$startDate · ${time.format(start)} – ${time.format(end)}"
        else -> "${time.format(start)} – ${time.format(end)}"
    }
    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(timing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EventReview(
    review: Review,
    enabled: Boolean,
    connected: Boolean,
    cancel: () -> Unit,
    confirm: (String, String, String, String) -> Unit
) {
    val event = review.event
    var title by rememberSaveable { mutableStateOf(event.title) }
    var start by rememberSaveable { mutableStateOf(formatLocal(event.start)) }
    var end by rememberSaveable { mutableStateOf(formatLocal(event.end)) }
    var reminder by rememberSaveable { mutableStateOf(event.reminder?.toString().orEmpty()) }
    val heading = when (review.action) {
        Action.MOVE -> "Review new time"
        Action.REMIND -> "Review reminder"
        Action.DELETE -> "Delete this event?"
        else -> "Review new event"
    }
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (review.action == Action.CREATE) {
                Text(
                    if (connected) "Adds to Google Calendar" else "Adds to the demo calendar on this device",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            review.original?.let { EventSummary(it) }
            if (review.action == Action.CREATE) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(), enabled = enabled,
                    shape = MaterialTheme.shapes.medium, colors = eventFieldColors(),
                    label = { Text("Event title") }, singleLine = true
                )
            }
            if (review.action == Action.CREATE || review.action == Action.MOVE) {
                OutlinedTextField(
                    start, { start = it }, Modifier.fillMaxWidth(), enabled = enabled,
                    shape = MaterialTheme.shapes.medium, colors = eventFieldColors(),
                    label = { Text("Start · YYYY-MM-DD HH:MM") }, singleLine = true
                )
                OutlinedTextField(
                    end, { end = it }, Modifier.fillMaxWidth(), enabled = enabled,
                    shape = MaterialTheme.shapes.medium, colors = eventFieldColors(),
                    label = { Text("End · YYYY-MM-DD HH:MM") }, singleLine = true
                )
            }
            if (review.action == Action.CREATE || review.action == Action.REMIND) {
                OutlinedTextField(
                    reminder, { reminder = it }, Modifier.fillMaxWidth(), enabled = enabled,
                    shape = MaterialTheme.shapes.medium, colors = eventFieldColors(),
                    label = { Text("Popup reminder, minutes before") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    if (review.action == Action.CREATE) "Leave blank to use calendar defaults."
                    else "This replaces the event’s existing reminders.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = cancel, enabled = enabled) { Text("Cancel") }
                Button(
                    onClick = { confirm(title, start, end, reminder) },
                    enabled = enabled,
                    colors = if (review.action == Action.DELETE) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Text(if (review.action == Action.DELETE) "Delete event" else "Save")
                }
            }
        }
    }
}
