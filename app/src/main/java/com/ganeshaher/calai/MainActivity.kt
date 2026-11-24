package com.ganeshaher.calai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ganeshaher.calai.data.DemoCalendar
import com.ganeshaher.calai.data.DemoCommandParser
import com.ganeshaher.calai.data.GeminiCommandParser
import com.ganeshaher.calai.data.GoogleCalendarRepository
import com.ganeshaher.calai.ui.CalendarScreen
import com.ganeshaher.calai.ui.CalendarSplash
import com.ganeshaher.calai.ui.CalendarViewModel
import com.ganeshaher.calai.ui.theme.CalAiTheme
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
    private val geminiEnabled by lazy { FirebaseApp.getApps(applicationContext).isNotEmpty() }
    private val viewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (geminiEnabled) installAppCheck()
                val parser = if (geminiEnabled) GeminiCommandParser(getString(R.string.gemini_model))
                    else DemoCommandParser()
                val demo = DemoCalendar(getSharedPreferences("demo_calendar", MODE_PRIVATE))
                val images = getSharedPreferences("event_images", MODE_PRIVATE)
                return CalendarViewModel(parser, demo, images, ::GoogleCalendarRepository) as T
            }
        })[CalendarViewModel::class.java]
    }

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (transcript.isNullOrBlank()) viewModel.message("No speech was recognized. Try again or type your command.")
            else {
                viewModel.edit(transcript)
                viewModel.message("")
            }
        }
    }

    private val noteSpeech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (transcript.isNullOrBlank()) {
                viewModel.message("No speech was recognized. Try again or type your note.")
            } else {
                val existing = viewModel.state.value.voiceNote.trimEnd()
                viewModel.editVoiceNote(listOf(existing, transcript).filter { it.isNotBlank() }.joinToString("\n"))
                viewModel.message("")
            }
        }
    }

    private val calendarConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.connectionResult(null)
        } else {
            try {
                viewModel.connectionResult(authorizationClient.getAuthorizationResultFromIntent(result.data).accessToken)
            } catch (_: ApiException) {
                viewModel.connectionResult(null, "Google Calendar authorization failed.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xFF332449.toInt()),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xFF332449.toInt())
        )
        setContent {
            CalAiTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                if (showSplash) {
                    CalendarSplash(onFinished = { showSplash = false })
                } else {
                    CalendarScreen(viewModel, geminiEnabled, ::speak, ::dictateNote, ::connectCalendar)
                }
            }
        }
    }

    private fun speak() {
        try {
            speech.launch(speechIntent())
        } catch (_: ActivityNotFoundException) {
            viewModel.message("Speech recognition is unavailable on this device. You can type your command.")
        }
    }

    private fun dictateNote() {
        try {
            noteSpeech.launch(speechIntent().putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate your event note"))
        } catch (_: ActivityNotFoundException) {
            viewModel.message("Speech recognition is unavailable. You can type your event note.")
        }
    }

    private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    private fun connectCalendar() {
        viewModel.connecting()
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/calendar.events.owned")))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (pending == null) viewModel.connectionResult(result.accessToken)
                else calendarConsent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            }
            .addOnFailureListener {
                viewModel.connectionResult(null, "Could not connect. Check Google Play services and the Android OAuth client.")
            }
    }
}
