package com.ganeshaher.calai.data

import android.text.Html
import android.text.TextUtils

import com.ganeshaher.calai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GoogleCalendarRepository(private val token: () -> String?) : CalendarRepository {
    private val client = OkHttpClient()
    private val eventsUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events".toHttpUrl()

    private suspend fun request(builder: Request.Builder, allowConflict: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            val accessToken = checkNotNull(token()) { "Connect Google Calendar to continue." }
            val request = builder.header("Authorization", "Bearer $accessToken").build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401) error("Calendar access expired. Reconnect Google Calendar and try again.")
                if (response.code == 403) error("Calendar access was denied. Check your OAuth consent and Calendar API setup.")
                if (response.code == 412) error("This event changed in Google Calendar. Preview the command again.")
                if (!response.isSuccessful && !(allowConflict && response.code == 409)) {
                    error("Google Calendar returned HTTP ${response.code}.")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) JSONObject() else JSONObject(body)
            }
        }

    override suspend fun list(from: Long, until: Long, query: String): List<Event> {
        val events = mutableListOf<Event>()
        var page: String? = null
        do {
            val url = eventsUrl.newBuilder()
                .addQueryParameter("singleEvents", "true")
                .addQueryParameter("orderBy", "startTime")
                .addQueryParameter("timeMin", formatApi(from))
                .addQueryParameter("timeMax", formatApi(until))
                .addQueryParameter("maxResults", "250")
            if (query.isNotBlank()) url.addQueryParameter("q", query)
            page?.let { url.addQueryParameter("pageToken", it) }
            val data = request(Request.Builder().url(url.build()))
            val items = data.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val startObject = item.optJSONObject("start") ?: continue
                val endObject = item.optJSONObject("end") ?: continue
                val start = (if (startObject.has("date")) parseAllDay(startObject.optString("date"))
                    else parseApi(startObject.optString("dateTime"))) ?: continue
                val end = (if (endObject.has("date")) parseAllDay(endObject.optString("date"))
                    else parseApi(endObject.optString("dateTime"))) ?: continue
                val overrides = item.optJSONObject("reminders")?.optJSONArray("overrides")
                val popup = (0 until (overrides?.length() ?: 0))
                    .mapNotNull { overrides?.optJSONObject(it) }
                    .firstOrNull { it.optString("method") == "popup" }
                val title = item.optString("summary", "(Untitled event)")
                if (query.isBlank() || title.contains(query, ignoreCase = true)) {
                    events += Event(
                        item.getString("id"), title, start, end,
                        popup?.optInt("minutes", -1)?.takeIf { it >= 0 }, startObject.has("date"),
                        item.optString("etag").takeIf { it.isNotBlank() },
                        description = Html.fromHtml(item.optString("description"), Html.FROM_HTML_MODE_COMPACT)
                            .toString().trim()
                    )
                }
            }
            page = data.optString("nextPageToken").ifBlank { null }
        } while (page != null)
        return events
    }

    override suspend fun apply(review: Review) {
        val event = review.event
        val itemUrl = eventsUrl.newBuilder().addPathSegment(event.id).build()
        val update = Request.Builder().url(itemUrl)
        review.original?.version?.let { update.header("If-Match", it) }
        if (review.action == Action.DELETE) {
            request(update.delete())
            return
        }

        val data = JSONObject()
        if (review.action == Action.CREATE || review.action == Action.MOVE) {
            data.put("start", JSONObject().put("dateTime", formatApi(event.start)))
            data.put("end", JSONObject().put("dateTime", formatApi(event.end)))
        }
        if (review.action == Action.CREATE) {
            data.put("id", event.id)
            data.put("summary", event.title)
            if (event.description.isNotBlank()) {
                data.put("description", event.description.lineSequence().joinToString("<br>", transform = TextUtils::htmlEncode))
            }
        }
        if (review.action == Action.REMIND || review.action == Action.CREATE && event.reminder != null) {
            data.put("reminders", JSONObject().put("useDefault", false).put(
                "overrides", JSONArray().put(
                    JSONObject().put("method", "popup").put("minutes", requireNotNull(event.reminder))
                )
            ))
        }
        val body = data.toString().toRequestBody("application/json".toMediaType())
        if (review.action == Action.CREATE) {
            request(Request.Builder().url(eventsUrl).post(body), allowConflict = true)
        } else {
            request(update.patch(body))
        }
    }
}
