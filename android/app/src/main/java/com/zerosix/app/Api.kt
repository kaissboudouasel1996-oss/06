package com.zerosix.app

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

data class League(val id: String, val tab: String, val name: String)

/** TheSportsDB league ids. */
val LEAGUES = listOf(
    League("4328", "Premier League", "English Premier League"),
    League("4334", "Ligue 1", "French Ligue 1"),
    League("4335", "La Liga", "Spanish La Liga"),
    League("4332", "Serie A", "Italian Serie A"),
    League("4331", "Bundesliga", "German Bundesliga"),
)

data class Match(
    val id: String,
    val title: String,
    /** Kick-off as UTC epoch millis, or null when the date is not known. */
    val kickoffMs: Long?,
    /** False when only the date is known (no kick-off time yet). */
    val hasTime: Boolean,
    val round: String?,
)

data class Channel(val country: String, val name: String)

/** Blocking calls: always invoke from a background dispatcher. */
object Api {
    private const val BASE = "https://www.thesportsdb.com/api/v1/json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(ctx: Context, path: String): JSONObject {
        val key = Prefs.apiKey(ctx)
        val req = Request.Builder()
            .url("$BASE/$key/$path")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 429) throw IOException("Too many requests. Wait a minute, then refresh.")
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Server error (HTTP ${resp.code})")
            if (body.isBlank()) return JSONObject()
            return try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IOException("Unexpected answer from the API. Check your API key in Settings.")
            }
        }
    }

    private fun str(o: JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "").trim()

    /**
     * Upcoming matches of one league. With a premium key this is every remaining match of the
     * season; the free key can only see the next one (TheSportsDB limits it).
     */
    fun nextMatches(ctx: Context, leagueId: String): List<Match> {
        if (!Prefs.isFreeKey(Prefs.apiKey(ctx))) {
            val season = seasonMatches(ctx, leagueId)
            if (season.isNotEmpty()) return season
        }
        return parseEvents(get(ctx, "eventsnextleague.php?id=$leagueId"))
    }

    /** European seasons run August to May, so July onwards belongs to the season starting that year. */
    private fun currentSeason(): String {
        val today = LocalDate.now(ZoneOffset.UTC)
        val start = if (today.monthValue >= 7) today.year else today.year - 1
        return "$start-${start + 1}"
    }

    private fun seasonMatches(ctx: Context, leagueId: String): List<Match> {
        val cutoff = System.currentTimeMillis() - 3 * 60 * 60 * 1000 // keep matches that just started
        return parseEvents(get(ctx, "eventsseason.php?id=$leagueId&s=${currentSeason()}"))
            .filter { (it.kickoffMs ?: Long.MAX_VALUE) >= cutoff }
            .sortedBy { it.kickoffMs ?: Long.MAX_VALUE }
    }

    private fun parseEvents(root: JSONObject): List<Match> {
        val arr = root.optJSONArray("events") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseMatch(arr.optJSONObject(it)) }
    }

    private fun parseMatch(o: JSONObject?): Match? {
        if (o == null) return null
        val id = str(o, "idEvent")
        if (id.isEmpty()) return null

        val home = str(o, "strHomeTeam")
        val away = str(o, "strAwayTeam")
        val title = if (home.isNotEmpty() && away.isNotEmpty()) "$home vs $away" else str(o, "strEvent")

        // TheSportsDB times are UTC. Prefer the full timestamp, fall back to date + time.
        var ms: Long? = null
        var hasTime = false
        val ts = str(o, "strTimestamp").replace(' ', 'T')
        if (ts.length >= 19) {
            ms = runCatching { LocalDateTime.parse(ts.take(19)).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            hasTime = ms != null
        }
        if (ms == null) {
            val date = runCatching { LocalDate.parse(str(o, "dateEvent")) }.getOrNull()
            if (date != null) {
                val time = runCatching { LocalTime.parse(str(o, "strTime").take(8)) }.getOrNull()
                val dt = if (time != null) LocalDateTime.of(date, time) else date.atStartOfDay()
                ms = dt.toInstant(ZoneOffset.UTC).toEpochMilli()
                hasTime = time != null
            }
        }

        val round = str(o, "intRound").takeIf { it.isNotEmpty() && it != "0" }
        return Match(id = id, title = title.ifEmpty { "Match $id" }, kickoffMs = ms, hasTime = hasTime, round = round)
    }

    /** Every TV channel listed for one match, with the country it broadcasts in. */
    fun channels(ctx: Context, eventId: String): List<Channel> {
        val root = get(ctx, "lookuptv.php?id=$eventId")

        // The listing sits in the first array of the answer; do not depend on its exact key name.
        var arr: JSONArray? = null
        val keys = root.keys()
        while (keys.hasNext()) {
            val a = root.optJSONArray(keys.next())
            if (a != null) {
                arr = a
                break
            }
        }
        if (arr == null) return emptyList()

        val seen = LinkedHashSet<Channel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = str(o, "strChannel")
            if (name.isEmpty()) continue
            val country = str(o, "strCountry").ifEmpty { "Other" }
            seen.add(Channel(country, name))
        }
        return seen.toList()
    }
}
