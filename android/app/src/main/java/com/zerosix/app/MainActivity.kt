package com.zerosix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                darkColorScheme(primary = Color(0xFF4CC577))
            } else {
                lightColorScheme(primary = Color(0xFF14793A))
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize()) { HomeScreen() }
            }
        }
    }
}

private fun formatKickoff(m: Match): String {
    val ms = m.kickoffMs ?: return "Date to be confirmed"
    val instant = Instant.ofEpochMilli(ms)
    val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val text = if (m.hasTime) {
        val local = instant.atZone(ZoneId.systemDefault())
        day.format(local) + " · " + DateTimeFormatter.ofPattern("HH:mm").format(local)
    } else {
        day.format(instant.atZone(ZoneOffset.UTC))
    }
    return if (m.round != null) "$text · Round ${m.round}" else text
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(Prefs.apiKey(ctx)) }
    var selected by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    // Matches per league id.
    val matches = remember { mutableStateMapOf<String, List<Match>>() }
    val leagueLoading = remember { mutableStateMapOf<String, Boolean>() }
    val leagueError = remember { mutableStateMapOf<String, String>() }

    // Channels per match id, loaded when a match is opened.
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val channelMap = remember { mutableStateMapOf<String, List<Channel>>() }
    val channelLoading = remember { mutableStateMapOf<String, Boolean>() }
    val channelError = remember { mutableStateMapOf<String, String>() }

    fun loadLeague(league: League) {
        if (leagueLoading[league.id] == true) return
        scope.launch {
            leagueLoading[league.id] = true
            try {
                matches[league.id] = withContext(Dispatchers.IO) { Api.nextMatches(ctx, league.id) }
                leagueError.remove(league.id)
            } catch (e: Exception) {
                leagueError[league.id] = e.message ?: "Network error"
            }
            leagueLoading[league.id] = false
        }
    }

    fun loadChannels(m: Match) {
        if (channelLoading[m.id] == true) return
        scope.launch {
            channelLoading[m.id] = true
            try {
                channelMap[m.id] = withContext(Dispatchers.IO) { Api.channels(ctx, m.id) }
                channelError.remove(m.id)
            } catch (e: Exception) {
                channelError[m.id] = e.message ?: "Network error"
            }
            channelLoading[m.id] = false
        }
    }

    fun refreshAll() {
        matches.clear()
        leagueError.clear()
        open.clear()
        channelMap.clear()
        channelError.clear()
        loadLeague(LEAGUES[selected])
    }

    LaunchedEffect(selected) {
        val league = LEAGUES[selected]
        if (matches[league.id] == null && leagueError[league.id] == null) loadLeague(league)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("06") },
                actions = {
                    IconButton(onClick = { refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                LEAGUES.forEachIndexed { i, league ->
                    Tab(
                        selected = selected == i,
                        onClick = { selected = i },
                        text = { Text(league.tab, maxLines = 1) },
                    )
                }
            }

            val league = LEAGUES[selected]
            val list = matches[league.id]
            val freeKey = Prefs.isFreeKey(apiKey)

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (freeKey) {
                    item {
                        Text(
                            "You are using TheSportsDB's free key, which limits how many matches and channels it returns. " +
                                "Add a premium key in Settings to see everything.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val error = leagueError[league.id]
                when {
                    leagueLoading[league.id] == true && list == null -> item {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                    error != null -> item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    list != null && list.isEmpty() -> item {
                        Text(
                            "No upcoming matches listed for ${league.name}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(list ?: emptyList(), key = { it.id }) { m ->
                    MatchCard(
                        m = m,
                        isOpen = open[m.id] == true,
                        loading = channelLoading[m.id] == true,
                        error = channelError[m.id],
                        channels = channelMap[m.id],
                        freeKey = freeKey,
                        onClick = {
                            val nowOpen = open[m.id] != true
                            open[m.id] = nowOpen
                            if (nowOpen && channelMap[m.id] == null) loadChannels(m)
                        },
                    )
                }
            }
        }
    }

    if (showSettings) {
        var key by remember { mutableStateOf(if (Prefs.isFreeKey(apiKey)) "" else apiKey) }

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        label = { Text("TheSportsDB API key") },
                        placeholder = { Text("Leave empty for the free key") },
                    )
                    Text(
                        "Match data and TV channels come from thesportsdb.com. Their free key returns only a few " +
                            "results; a premium key from their site shows all matches and channels.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.saveApiKey(ctx, key)
                    apiKey = Prefs.apiKey(ctx)
                    showSettings = false
                    refreshAll()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun MatchCard(
    m: Match,
    isOpen: Boolean,
    loading: Boolean,
    error: String?,
    channels: List<Channel>?,
    freeKey: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                formatKickoff(m),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(m.title, style = MaterialTheme.typography.titleMedium)

            if (!isOpen) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to see all channels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    channels != null && channels.isEmpty() -> Text(
                        if (freeKey) {
                            "No channels returned. The free API key hides most listings."
                        } else {
                            "No channels listed for this match yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    channels != null -> {
                        val byCountry = channels.groupBy { it.country }.toSortedMap()
                        Text(
                            "${channels.size} channels in ${byCountry.size} countries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        byCountry.forEach { (country, list) ->
                            Spacer(Modifier.height(8.dp))
                            Text(country, style = MaterialTheme.typography.labelLarge)
                            Text(
                                list.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
