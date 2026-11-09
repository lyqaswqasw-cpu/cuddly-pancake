package com.example.ui.player

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.data.model.SeriesEpisode
import com.example.data.model.SeriesSeason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Aspect Ratio modes for the Cinema Ultra VOD Player.
 */
enum class VodAspectMode(
    val id: String,
    val arabicLabel: String,
    val englishLabel: String
) {
    FIT("fit", "ملاءمة الشاشة", "Fit"),
    FILL("fill", "ملء الشاشة", "Fill"),
    ZOOM("zoom", "تكبير", "Zoom"),
    STRETCH("stretch", "تمديد", "Stretch"),
    ORIGINAL("original", "الحجم الأصلي", "Original");

    fun next(): VodAspectMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * Subtitle background modes for custom styling.
 */
enum class SubtitleBackgroundMode(
    val id: String,
    val arabicLabel: String,
    val windowColorArgb: Int,
    val backgroundColorArgb: Int
) {
    SEMI_TRANSPARENT(
        id = "semi",
        arabicLabel = "شبه شفاف",
        windowColorArgb = 0x00000000,
        backgroundColorArgb = 0xB9080810.toInt()
    ),
    SOLID_BLACK(
        id = "solid",
        arabicLabel = "أسود داكن",
        windowColorArgb = 0x00000000,
        backgroundColorArgb = 0xF5000000.toInt()
    ),
    NONE_OUTLINE(
        id = "outline",
        arabicLabel = "بدون خلفية (حواف)",
        windowColorArgb = 0x00000000,
        backgroundColorArgb = 0x00000000
    );

    companion object {
        fun fromId(id: String): SubtitleBackgroundMode =
            entries.firstOrNull { it.id == id } ?: SEMI_TRANSPARENT
    }
}

/**
 * Subtitle text color presets.
 */
enum class SubtitleColorPreset(
    val id: String,
    val arabicLabel: String,
    val colorArgb: Int
) {
    WHITE("white", "أبيض نقي", 0xFFFFFFFF.toInt()),
    GOLD("gold", "أصفر سينمائي", 0xFFFFD700.toInt()),
    CYAN("cyan", "سماوي واضح", 0xFF00E5FF.toInt()),
    GREEN("green", "أخضر مريح", 0xFF69F0AE.toInt());

    companion object {
        fun fromId(id: String): SubtitleColorPreset =
            entries.firstOrNull { it.id == id } ?: WHITE
    }
}

/**
 * Persistent preferences for the Cinema Ultra VOD Player.
 */
data class VodPlayerPreferences(
    val forwardSeekSeconds: Int = 10,
    val rewindSeekSeconds: Int = 10,
    val subtitleFontSizeSp: Float = 20f,
    val subtitleColorPreset: SubtitleColorPreset = SubtitleColorPreset.WHITE,
    val subtitleBackgroundMode: SubtitleBackgroundMode = SubtitleBackgroundMode.SEMI_TRANSPARENT,
    val subtitleDelayOffsetMs: Long = 0L,
    val subtitlesEnabled: Boolean = true,
    val vodCacheSizeMb: Int = 128,
    val autoNextEpisodeEnabled: Boolean = true
) {
    val forwardSeekMs: Long get() = forwardSeekSeconds.coerceIn(5, 60) * 1000L
    val rewindSeekMs: Long get() = rewindSeekSeconds.coerceIn(5, 60) * 1000L
}

object VodPlayerPreferencesManager {
    private const val PREFS_NAME = "foul_tv_vod_player_prefs"
    val AVAILABLE_SEEK_SECONDS = listOf(5, 10, 15, 20, 30)
    val AVAILABLE_FONT_SIZES_SP = listOf(16f, 20f, 24f, 28f, 32f)
    val AVAILABLE_CACHE_SIZES_MB = listOf(0, 64, 128, 256)
    const val SUBTITLE_DELAY_STEP_MS = 250L

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sharedPreferences: SharedPreferences? = null

    private val _preferencesFlow = MutableStateFlow(VodPlayerPreferences())
    val preferencesFlow: StateFlow<VodPlayerPreferences> = _preferencesFlow.asStateFlow()

    fun initialize(context: Context) {
        if (sharedPreferences != null) return
        val appContext = context.applicationContext
        ioScope.launch {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPreferences = prefs
            _preferencesFlow.value = VodPlayerPreferences(
                forwardSeekSeconds = prefs.getInt("forward_seek_sec", 10).coerceIn(5, 60),
                rewindSeekSeconds = prefs.getInt("rewind_seek_sec", 10).coerceIn(5, 60),
                subtitleFontSizeSp = prefs.getFloat("subtitle_font_sp", 20f).coerceIn(14f, 36f),
                subtitleColorPreset = SubtitleColorPreset.fromId(
                    prefs.getString("subtitle_color", SubtitleColorPreset.WHITE.id).orEmpty()
                ),
                subtitleBackgroundMode = SubtitleBackgroundMode.fromId(
                    prefs.getString("subtitle_bg", SubtitleBackgroundMode.SEMI_TRANSPARENT.id).orEmpty()
                ),
                subtitleDelayOffsetMs = prefs.getLong("subtitle_delay_ms", 0L).coerceIn(-30_000L, 30_000L),
                subtitlesEnabled = prefs.getBoolean("subtitles_enabled", true),
                vodCacheSizeMb = prefs.getInt("vod_cache_mb", 128),
                autoNextEpisodeEnabled = prefs.getBoolean("auto_next_episode", true)
            )
        }
    }

    fun updateUnifiedSeekStep(seconds: Int) {
        val clamped = seconds.coerceIn(5, 60)
        updateSeekSteps(forwardSeconds = clamped, rewindSeconds = clamped)
    }

    fun updateSeekSteps(forwardSeconds: Int, rewindSeconds: Int) {
        val f = forwardSeconds.coerceIn(5, 60)
        val r = rewindSeconds.coerceIn(5, 60)
        val updated = _preferencesFlow.value.copy(
            forwardSeekSeconds = f,
            rewindSeekSeconds = r
        )
        _preferencesFlow.value = updated
        persistAsync {
            putInt("unified_seek_sec", f)
            putInt("forward_seek_sec", f)
            putInt("rewind_seek_sec", r)
        }
    }

    fun updateSubtitleFontSize(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(14f, 36f)
        _preferencesFlow.value = _preferencesFlow.value.copy(subtitleFontSizeSp = clamped)
        persistAsync { putFloat("subtitle_font_sp", clamped) }
    }

    fun updateSubtitleColor(preset: SubtitleColorPreset) {
        _preferencesFlow.value = _preferencesFlow.value.copy(subtitleColorPreset = preset)
        persistAsync { putString("subtitle_color", preset.id) }
    }

    fun updateSubtitleBackground(mode: SubtitleBackgroundMode) {
        _preferencesFlow.value = _preferencesFlow.value.copy(subtitleBackgroundMode = mode)
        persistAsync { putString("subtitle_bg", mode.id) }
    }

    fun adjustSubtitleDelayByStep(steps: Int) {
        val next = (_preferencesFlow.value.subtitleDelayOffsetMs + steps * SUBTITLE_DELAY_STEP_MS)
            .coerceIn(-30_000L, 30_000L)
        _preferencesFlow.value = _preferencesFlow.value.copy(subtitleDelayOffsetMs = next)
        persistAsync { putLong("subtitle_delay_ms", next) }
    }

    fun resetSubtitleDelay() {
        _preferencesFlow.value = _preferencesFlow.value.copy(subtitleDelayOffsetMs = 0L)
        persistAsync { putLong("subtitle_delay_ms", 0L) }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        _preferencesFlow.value = _preferencesFlow.value.copy(subtitlesEnabled = enabled)
        persistAsync { putBoolean("subtitles_enabled", enabled) }
    }

    fun setVodCacheSizeMb(sizeMb: Int) {
        val valid = if (sizeMb in AVAILABLE_CACHE_SIZES_MB) sizeMb else 128
        _preferencesFlow.value = _preferencesFlow.value.copy(vodCacheSizeMb = valid)
        persistAsync { putInt("vod_cache_mb", valid) }
    }

    fun setAutoNextEpisodeEnabled(enabled: Boolean) {
        _preferencesFlow.value = _preferencesFlow.value.copy(autoNextEpisodeEnabled = enabled)
        persistAsync { putBoolean("auto_next_episode", enabled) }
    }

    private fun persistAsync(block: SharedPreferences.Editor.() -> Unit) {
        val prefs = sharedPreferences ?: return
        ioScope.launch {
            prefs.edit().apply(block).apply()
        }
    }
}

/**
 * Watch progress entry for a Movie or a Series Episode.
 */
data class WatchProgressEntry(
    val key: String,
    val accountId: String,
    val mediaId: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val containerExtension: String,
    val isSeries: Boolean,
    val seriesId: String = "",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val episodeId: String = "",
    val episodeTitle: String = "",
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
    val isCompleted: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val remainingMs: Long
        get() = if (durationMs > positionMs) durationMs - positionMs else 0L
}

/**
 * Manages saving, restoring, and querying watch progress for Movies and Series Episodes.
 * All disk writes run asynchronously on Dispatchers.IO while keeping an in-memory map
 * for instant UI reads and deterministic unit testing.
 */
object WatchProgressManager {
    private const val PREFS_NAME = "foul_tv_watch_progress_store"
    private const val KEY_ENTRIES_JSON = "entries_v1"

    /** Ignore saving progress during the first 15 seconds so accidental opens don't clutter history. */
    const val MIN_SAVE_POSITION_MS = 15_000L

    /** Consider a movie/episode finished when >= 93% watched or within the final 30 seconds. */
    const val COMPLETION_THRESHOLD_RATIO = 0.93f
    const val COMPLETION_REMAINING_MS = 30_000L

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sharedPreferences: SharedPreferences? = null
    private val entriesMap = ConcurrentHashMap<String, WatchProgressEntry>()

    private val _continueWatchingFlow = MutableStateFlow<List<WatchProgressEntry>>(emptyList())
    val continueWatchingFlow: StateFlow<List<WatchProgressEntry>> = _continueWatchingFlow.asStateFlow()

    fun buildMovieKey(accountId: String, movieId: String): String =
        "movie_${accountId.ifBlank { "default" }}_${movieId.trim()}"

    fun buildEpisodeKey(
        accountId: String,
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: String
    ): String {
        val cleanEpId = episodeId.trim().ifBlank { "s${seasonNumber}e${episodeNumber}" }
        return "episode_${accountId.ifBlank { "default" }}_${seriesId.trim()}_s${seasonNumber}_e${episodeNumber}_$cleanEpId"
    }

    fun initialize(context: Context) {
        if (sharedPreferences != null) return
        val appContext = context.applicationContext
        ioScope.launch {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPreferences = prefs
            val raw = prefs.getString(KEY_ENTRIES_JSON, null)
            if (!raw.isNullOrBlank()) {
                runCatching {
                    val array = JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val entry = fromJson(obj)
                        if (entry.key.isNotBlank()) {
                            entriesMap[entry.key] = entry
                        }
                    }
                    refreshFlow()
                }
            }
        }
    }

    /**
     * Evaluates whether a given playback position and duration should be considered completed.
     */
    fun isNearEnd(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L || positionMs <= 0L) return false
        val ratio = positionMs.toFloat() / durationMs.toFloat()
        val remaining = durationMs - positionMs
        return ratio >= COMPLETION_THRESHOLD_RATIO || (durationMs > 90_000L && remaining <= COMPLETION_REMAINING_MS)
    }

    /**
     * Evaluates whether the player should prompt the user to resume or start from beginning.
     */
    fun shouldOfferResume(entry: WatchProgressEntry?): Boolean {
        if (entry == null || entry.isCompleted) return false
        if (entry.positionMs < MIN_SAVE_POSITION_MS) return false
        if (entry.durationMs > 0L && isNearEnd(entry.positionMs, entry.durationMs)) return false
        return true
    }

    /**
     * Records playback progress for a movie or episode.
     * - Ignores positions < 15s unless an existing entry already exists and isn't being reset.
     * - Marks completed if >= 93% or within last 30s.
     */
    fun saveProgress(
        accountId: String,
        mediaId: String,
        title: String,
        posterUrl: String,
        backdropUrl: String = "",
        containerExtension: String = "mp4",
        isSeries: Boolean = false,
        seriesId: String = "",
        seasonNumber: Int = 1,
        episodeNumber: Int = 1,
        episodeId: String = "",
        episodeTitle: String = "",
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): WatchProgressEntry? {
        if (mediaId.isBlank() && seriesId.isBlank()) return null
        val key = if (isSeries) {
            buildEpisodeKey(
                accountId = accountId,
                seriesId = seriesId.ifBlank { mediaId },
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeId = episodeId.ifBlank { mediaId }
            )
        } else {
            buildMovieKey(accountId = accountId, movieId = mediaId)
        }

        // Do not save in the first 15 seconds if there was no prior saved progress
        if (positionMs < MIN_SAVE_POSITION_MS && !entriesMap.containsKey(key)) {
            return null
        }

        val completed = isNearEnd(positionMs, durationMs)
        val safePosition = if (completed) {
            durationMs.coerceAtLeast(positionMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }

        val entry = WatchProgressEntry(
            key = key,
            accountId = accountId.ifBlank { "default" },
            mediaId = mediaId,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            containerExtension = containerExtension,
            isSeries = isSeries,
            seriesId = if (isSeries) seriesId.ifBlank { mediaId } else "",
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeId = if (isSeries) episodeId.ifBlank { mediaId } else "",
            episodeTitle = episodeTitle,
            positionMs = safePosition,
            durationMs = max(durationMs, safePosition),
            updatedAtEpochMs = nowEpochMs,
            isCompleted = completed
        )

        entriesMap[key] = entry
        refreshFlow()
        persistAllAsync()
        return entry
    }

    fun getMovieProgress(accountId: String, movieId: String): WatchProgressEntry? {
        val key = buildMovieKey(accountId, movieId)
        return entriesMap[key]
    }

    fun getEpisodeProgress(
        accountId: String,
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: String
    ): WatchProgressEntry? {
        val key = buildEpisodeKey(accountId, seriesId, seasonNumber, episodeNumber, episodeId)
        return entriesMap[key]
    }

    fun getLastWatchedEpisodeForSeries(accountId: String, seriesId: String): WatchProgressEntry? {
        val normalizedAccount = accountId.ifBlank { "default" }
        return entriesMap.values
            .filter { it.isSeries && it.accountId == normalizedAccount && it.seriesId == seriesId }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    fun clearProgressForKey(key: String) {
        entriesMap.remove(key)
        refreshFlow()
        persistAllAsync()
    }

    fun clearAllForTesting() {
        entriesMap.clear()
        _continueWatchingFlow.value = emptyList()
    }

    private fun refreshFlow() {
        // Keep most recent items first; for a series, show only the most recently watched episode in Continue Watching
        val activeEntries = entriesMap.values
            .filter { !it.isCompleted && it.positionMs >= MIN_SAVE_POSITION_MS }
            .sortedByDescending { it.updatedAtEpochMs }

        val deduplicated = mutableListOf<WatchProgressEntry>()
        val seenSeries = HashSet<String>()
        for (entry in activeEntries) {
            if (entry.isSeries) {
                val seriesGroupKey = "${entry.accountId}_${entry.seriesId}"
                if (seenSeries.add(seriesGroupKey)) {
                    deduplicated.add(entry)
                }
            } else {
                deduplicated.add(entry)
            }
        }
        _continueWatchingFlow.value = deduplicated.take(30)
    }

    private fun persistAllAsync() {
        val prefs = sharedPreferences ?: return
        val snapshot = entriesMap.values.sortedByDescending { it.updatedAtEpochMs }.take(250)
        ioScope.launch {
            runCatching {
                val array = JSONArray()
                for (entry in snapshot) {
                    array.put(toJson(entry))
                }
                prefs.edit().putString(KEY_ENTRIES_JSON, array.toString()).apply()
            }
        }
    }

    private fun toJson(entry: WatchProgressEntry): JSONObject = JSONObject().apply {
        put("key", entry.key)
        put("accountId", entry.accountId)
        put("mediaId", entry.mediaId)
        put("title", entry.title)
        put("posterUrl", entry.posterUrl)
        put("backdropUrl", entry.backdropUrl)
        put("containerExtension", entry.containerExtension)
        put("isSeries", entry.isSeries)
        put("seriesId", entry.seriesId)
        put("seasonNumber", entry.seasonNumber)
        put("episodeNumber", entry.episodeNumber)
        put("episodeId", entry.episodeId)
        put("episodeTitle", entry.episodeTitle)
        put("positionMs", entry.positionMs)
        put("durationMs", entry.durationMs)
        put("updatedAtEpochMs", entry.updatedAtEpochMs)
        put("isCompleted", entry.isCompleted)
    }

    private fun fromJson(obj: JSONObject): WatchProgressEntry = WatchProgressEntry(
        key = obj.optString("key"),
        accountId = obj.optString("accountId", "default"),
        mediaId = obj.optString("mediaId"),
        title = obj.optString("title"),
        posterUrl = obj.optString("posterUrl"),
        backdropUrl = obj.optString("backdropUrl"),
        containerExtension = obj.optString("containerExtension", "mp4"),
        isSeries = obj.optBoolean("isSeries", false),
        seriesId = obj.optString("seriesId"),
        seasonNumber = obj.optInt("seasonNumber", 1),
        episodeNumber = obj.optInt("episodeNumber", 1),
        episodeId = obj.optString("episodeId"),
        episodeTitle = obj.optString("episodeTitle"),
        positionMs = obj.optLong("positionMs", 0L),
        durationMs = obj.optLong("durationMs", 0L),
        updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
        isCompleted = obj.optBoolean("isCompleted", false)
    )
}

/**
 * Parsed subtitle cue with start/end timestamps in milliseconds and text lines.
 */
data class SubtitleCueItem(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

/**
 * Parser and synchronizer for external `.srt`, `.vtt`, and `.ass`/`.ssa` subtitle files.
 * Also supports applying a user-configured delay offset (`±250ms` steps).
 */
object CinemaSubtitleParser {

    private val SRT_TIMESTAMP_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )
    private val VTT_SHORT_TIMESTAMP_REGEX = Regex(
        """(\d{2}):(\d{2})\.(\d{1,3})\s*-->\s*(\d{2}):(\d{2})\.(\d{1,3})"""
    )
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
    private val ASS_TAG_REGEX = Regex("""\{[^}]*\}""")

    private const val MAX_SUBTITLE_STREAM_CHARS = 2 * 1024 * 1024 // 2 MB safety cap
    private const val MAX_PARSED_CUES = 12_000

    fun parseSubtitleStream(inputStream: InputStream): List<SubtitleCueItem> {
        val text = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            val buffer = CharArray(8192)
            var totalRead = 0
            while (true) {
                val read = reader.read(buffer)
                if (read <= 0) break
                val allowed = min(read, MAX_SUBTITLE_STREAM_CHARS - totalRead)
                if (allowed > 0) {
                    sb.append(buffer, 0, allowed)
                    totalRead += allowed
                }
                if (totalRead >= MAX_SUBTITLE_STREAM_CHARS) break
            }
            sb.toString()
        }
        return parseSubtitleText(text)
    }

    fun parseSubtitleText(rawText: String): List<SubtitleCueItem> {
        if (rawText.isBlank()) return emptyList()
        val normalized = rawText.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")

        // Check if ASS/SSA format
        if (normalized.contains("[Events]", ignoreCase = true) &&
            normalized.contains("Dialogue:", ignoreCase = true)
        ) {
            return parseAssDialogue(normalized)
        }

        val lines = normalized.lines()
        val cues = ArrayList<SubtitleCueItem>(min(lines.size / 3 + 1, MAX_PARSED_CUES))
        var i = 0
        var cueIndex = 1

        while (i < lines.size && cues.size < MAX_PARSED_CUES) {
            val line = lines[i].trim()
            if (
                line.isEmpty() ||
                line.startsWith("WEBVTT", ignoreCase = true) ||
                line.startsWith("Kind:", ignoreCase = true) ||
                line.startsWith("Language:", ignoreCase = true)
            ) {
                i++
                continue
            }
            if (
                line.equals("STYLE", ignoreCase = true) ||
                line.startsWith("STYLE ", ignoreCase = true) ||
                line.equals("NOTE", ignoreCase = true) ||
                line.startsWith("NOTE ", ignoreCase = true)
            ) {
                i++
                while (i < lines.size && lines[i].trim().isNotEmpty()) {
                    i++
                }
                continue
            }

            var timestampMatch = SRT_TIMESTAMP_REGEX.find(line)
            var shortVttMatch = if (timestampMatch == null) VTT_SHORT_TIMESTAMP_REGEX.find(line) else null

            if (timestampMatch == null && shortVttMatch == null && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                timestampMatch = SRT_TIMESTAMP_REGEX.find(nextLine)
                shortVttMatch = if (timestampMatch == null) VTT_SHORT_TIMESTAMP_REGEX.find(nextLine) else null
                if (timestampMatch != null || shortVttMatch != null) {
                    i++
                }
            }

            if (timestampMatch != null) {
                val startMs = toMillis(
                    timestampMatch.groupValues[1],
                    timestampMatch.groupValues[2],
                    timestampMatch.groupValues[3],
                    timestampMatch.groupValues[4]
                )
                val endMs = toMillis(
                    timestampMatch.groupValues[5],
                    timestampMatch.groupValues[6],
                    timestampMatch.groupValues[7],
                    timestampMatch.groupValues[8]
                )
                i++
                val textLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().isNotEmpty()) {
                    val cleaned = cleanSubtitleLine(lines[i])
                    if (cleaned.isNotEmpty()) textLines.add(cleaned)
                    i++
                }
                if (textLines.isNotEmpty() && endMs > startMs) {
                    cues.add(
                        SubtitleCueItem(
                            index = cueIndex++,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            text = textLines.joinToString("\n")
                        )
                    )
                }
            } else if (shortVttMatch != null) {
                val startMs = toMillis(
                    "0",
                    shortVttMatch.groupValues[1],
                    shortVttMatch.groupValues[2],
                    shortVttMatch.groupValues[3]
                )
                val endMs = toMillis(
                    "0",
                    shortVttMatch.groupValues[4],
                    shortVttMatch.groupValues[5],
                    shortVttMatch.groupValues[6]
                )
                i++
                val textLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().isNotEmpty()) {
                    val cleaned = cleanSubtitleLine(lines[i])
                    if (cleaned.isNotEmpty()) textLines.add(cleaned)
                    i++
                }
                if (textLines.isNotEmpty() && endMs > startMs) {
                    cues.add(
                        SubtitleCueItem(
                            index = cueIndex++,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            text = textLines.joinToString("\n")
                        )
                    )
                }
            } else {
                i++
            }
        }
        return cues.sortedBy { it.startTimeMs }
    }

    private fun parseAssDialogue(content: String): List<SubtitleCueItem> {
        val cues = mutableListOf<SubtitleCueItem>()
        var idx = 1
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("Dialogue:", ignoreCase = true)) {
                val parts = line.substringAfter(":").split(",", limit = 10)
                if (parts.size >= 10) {
                    val startMs = parseAssTime(parts[1].trim())
                    val endMs = parseAssTime(parts[2].trim())
                    val rawText = parts[9].replace("\\N", "\n").replace("\\n", "\n")
                    val clean = cleanSubtitleLine(rawText)
                    if (clean.isNotEmpty() && endMs > startMs) {
                        cues.add(
                            SubtitleCueItem(
                                index = idx++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = clean
                            )
                        )
                    }
                }
            }
        }
        return cues.sortedBy { it.startTimeMs }
    }

    private fun parseAssTime(timeStr: String): Long {
        val segs = timeStr.split(":", ".")
        if (segs.size < 4) return 0L
        val h = segs[0].toLongOrNull() ?: 0L
        val m = segs[1].toLongOrNull() ?: 0L
        val s = segs[2].toLongOrNull() ?: 0L
        val cs = segs[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((h * 3600L) + (m * 60L) + s) * 1000L + cs
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        val minutes = m.toLongOrNull() ?: 0L
        val seconds = s.toLongOrNull() ?: 0L
        val millis = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
    }

    private fun cleanSubtitleLine(line: String): String =
        line.replace(HTML_TAG_REGEX, "")
            .replace(ASS_TAG_REGEX, "")
            .trim()

    /**
     * Returns active subtitle text for [playbackPositionMs] taking [subtitleDelayOffsetMs] into account
     * in both directions (`+` positive delay and `-` negative delay) using O(log N) binary search.
     *
     * Note: If user sets a positive delay (+0.50s = +500ms), subtitles appear 500ms later
     * (`shiftedStart = cue.startTimeMs + 500ms`), which is equivalent to looking up
     * `targetCueTimeMs = playbackPositionMs - subtitleDelayOffsetMs` in the unshifted sorted cue list.
     */
    fun findActiveCueText(
        cues: List<SubtitleCueItem>,
        playbackPositionMs: Long,
        subtitleDelayOffsetMs: Long = 0L
    ): String {
        if (cues.isEmpty()) return ""
        val targetCueTimeMs = playbackPositionMs - subtitleDelayOffsetMs
        if (targetCueTimeMs < 0L) return ""

        // Binary search for the last cue whose startTimeMs <= targetCueTimeMs
        var low = 0
        var high = cues.size - 1
        var candidateIdx = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startTimeMs <= targetCueTimeMs) {
                candidateIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidateIdx < 0) return ""

        // Scan a small window around candidateIdx to support overlapping dual-speaker cues
        val startScan = (candidateIdx - 4).coerceAtLeast(0)
        val activeLines = mutableListOf<String>()
        for (idx in startScan..candidateIdx) {
            val cue = cues[idx]
            if (targetCueTimeMs in cue.startTimeMs..cue.endTimeMs) {
                activeLines.add(cue.text)
            }
        }
        return activeLines.joinToString("\n")
    }

    fun inferMimeTypeFromFileName(fileNameOrUri: String): String {
        val lower = fileNameOrUri.lowercase()
        return when {
            lower.endsWith(".vtt") -> "text/vtt"
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> "text/x-ssa"
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> "application/ttml+xml"
            else -> "application/x-subrip"
        }
    }
}

/**
 * Navigation helper and Auto-Next-Episode state controller for Series playback.
 */
data class EpisodeNavigationNeighbours(
    val currentSeasonNumber: Int,
    val currentEpisode: SeriesEpisode?,
    val previousSeasonNumber: Int?,
    val previousEpisode: SeriesEpisode?,
    val nextSeasonNumber: Int?,
    val nextEpisode: SeriesEpisode?
)

object SeriesEpisodeNavigator {

    /**
     * Resolves the current, previous, and next episode across seasons in order.
     */
    fun resolveNeighbours(
        seasons: List<SeriesSeason>,
        episodesBySeason: Map<Int, List<SeriesEpisode>>,
        currentSeasonNumber: Int,
        currentEpisodeId: String,
        currentEpisodeNumber: Int
    ): EpisodeNavigationNeighbours {
        val orderedSeasonNumbers = if (seasons.isNotEmpty()) {
            seasons.map { it.seasonNumber }.distinct().sorted()
        } else {
            episodesBySeason.keys.sorted()
        }

        data class FlatEpisode(val seasonNumber: Int, val episode: SeriesEpisode)

        val flatList = mutableListOf<FlatEpisode>()
        for (seasonNum in orderedSeasonNumbers) {
            val eps = episodesBySeason[seasonNum].orEmpty()
                .sortedWith(compareBy<SeriesEpisode> { it.episodeNum }.thenBy { it.id })
            for (ep in eps) {
                flatList.add(FlatEpisode(seasonNum, ep))
            }
        }

        if (flatList.isEmpty()) {
            return EpisodeNavigationNeighbours(
                currentSeasonNumber = currentSeasonNumber,
                currentEpisode = null,
                previousSeasonNumber = null,
                previousEpisode = null,
                nextSeasonNumber = null,
                nextEpisode = null
            )
        }

        var currentIndex = flatList.indexOfFirst {
            it.episode.id.isNotBlank() && it.episode.id == currentEpisodeId
        }
        if (currentIndex < 0) {
            currentIndex = flatList.indexOfFirst {
                it.seasonNumber == currentSeasonNumber && it.episode.episodeNum == currentEpisodeNumber
            }
        }

        if (currentIndex < 0) {
            return EpisodeNavigationNeighbours(
                currentSeasonNumber = currentSeasonNumber,
                currentEpisode = null,
                previousSeasonNumber = null,
                previousEpisode = null,
                nextSeasonNumber = null,
                nextEpisode = null
            )
        }

        val current = flatList[currentIndex]
        val prev = flatList.getOrNull(currentIndex - 1)
        val next = flatList.getOrNull(currentIndex + 1)

        return EpisodeNavigationNeighbours(
            currentSeasonNumber = current.seasonNumber,
            currentEpisode = current.episode,
            previousSeasonNumber = prev?.seasonNumber,
            previousEpisode = prev?.episode,
            nextSeasonNumber = next?.seasonNumber,
            nextEpisode = next?.episode
        )
    }

    /**
     * Evaluates whether the Auto-Next-Episode prompt should be displayed and whether its countdown should tick.
     * - Triggered when a valid `nextEpisode` exists AND either playback has ended (`isEnded`)
     *   or playback is within the final 20 seconds (`remainingMs in 1..20_000L` for episodes longer than 90s).
     * - Countdown pauses when `isPaused` or `isSettingsOrDrawerOpen` is true.
     * - Never triggers if `isCancelledForCurrentEpisode` is true or `nextEpisode == null`.
     */
    fun shouldShowAutoNextPrompt(
        isSeries: Boolean,
        autoNextEnabled: Boolean,
        nextEpisode: SeriesEpisode?,
        isCancelledForCurrentEpisode: Boolean,
        positionMs: Long,
        durationMs: Long,
        isPlaybackEnded: Boolean
    ): Boolean {
        if (!isSeries || !autoNextEnabled || nextEpisode == null || isCancelledForCurrentEpisode) {
            return false
        }
        if (isPlaybackEnded) return true
        if (durationMs < 90_000L || positionMs <= 0L) return false
        val remainingMs = durationMs - positionMs
        return remainingMs in 1L..20_000L
    }

    fun shouldAdvanceCountdownTick(
        isPromptVisible: Boolean,
        isPlaying: Boolean,
        isPlaybackEnded: Boolean,
        isMenuOrSheetOpen: Boolean
    ): Boolean {
        if (!isPromptVisible || isMenuOrSheetOpen) return false
        return isPlaying || isPlaybackEnded
    }
}

/**
 * Bounded, resilient Media3 SimpleCache exclusively for VOD (Movies & Series).
 * - Never wraps Live TV / IPTV streams.
 * - Uses LeastRecentlyUsedCacheEvictor bounded by user setting (default 128 MB).
 * - Uses credential-scrubbed cache keys so expiring Xtream tokens or server mirrors share safely or don't leak secrets.
 * - Enables FLAG_IGNORE_CACHE_ON_ERROR so any corrupted cache block falls back transparently to network.
 */
@OptIn(UnstableApi::class)
object VodMediaCacheManager {
    private const val CACHE_DIR_NAME = "foul_tv_vod_media_cache"

    @Volatile
    private var simpleCache: SimpleCache? = null

    private val sha256CacheKeyFactory = CacheKeyFactory { dataSpec: DataSpec ->
        dataSpec.key ?: computeSha256CacheKey(dataSpec.uri.toString())
    }

    /**
     * Generates a collision-free SHA-256 hex cache key from the full URL (including path and query parameters).
     * - Protects credentials/tokens from ever being stored in plain text in cache index files.
     * - Guarantees two different accounts, servers, or URLs never collide in the cache.
     */
    fun computeSha256CacheKey(rawUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawUrl.trim().toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            hex.append(String.format("%02x", b))
        }
        return "vod_sha256_$hex"
    }

    fun sanitizeCacheKey(uri: Uri): String = computeSha256CacheKey(uri.toString())

    @Synchronized
    fun wrapVodDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory,
        isLiveChannel: Boolean,
        cacheSizeMb: Int = VodPlayerPreferencesManager.preferencesFlow.value.vodCacheSizeMb
    ): DataSource.Factory {
        // Strict rule: NEVER use SimpleCache for Live TV / IPTV streams
        if (isLiveChannel || cacheSizeMb <= 0) {
            return upstreamFactory
        }

        val desiredMaxBytes = min(max(cacheSizeMb.toLong(), 32L), 512L) * 1024L * 1024L
        return runCatching {
            val cache = getOrCreateSingletonCache(context.applicationContext, desiredMaxBytes)
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheKeyFactory(sha256CacheKeyFactory)
                .setFlags(
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or
                        CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS
                )
        }.getOrElse {
            upstreamFactory
        }
    }

    /**
     * Returns the process-lifetime singleton [SimpleCache] instance.
     * Never calls `SimpleCache.release()` while a player or DataSource may still be active,
     * preventing Media3 `SimpleCache` lock/release crashes during playback.
     */
    @Synchronized
    private fun getOrCreateSingletonCache(appContext: Context, maxBytes: Long): SimpleCache {
        val existing = simpleCache
        if (existing != null) {
            return existing
        }
        val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val dbProvider = StandaloneDatabaseProvider(appContext)
        return SimpleCache(cacheDir, evictor, dbProvider).also {
            simpleCache = it
        }
    }

    @Synchronized
    fun clearCache(context: Context) {
        runCatching {
            val existing = simpleCache
            if (existing != null) {
                val keys = existing.keys.toList()
                for (key in keys) {
                    runCatching { existing.removeResource(key) }
                }
            } else {
                File(context.applicationContext.cacheDir, CACHE_DIR_NAME).deleteRecursively()
            }
        }
    }
}
