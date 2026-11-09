package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.ChannelServer
import com.example.data.model.LibraryMediaItem
import com.example.data.model.PlaybackRequest
import com.example.data.model.SeriesEpisode
import com.example.data.model.SeriesSeason
import com.example.ui.player.CinemaSubtitleParser
import com.example.ui.player.PlaybackRecoveryManager
import com.example.ui.player.PlayerDiagnosticEvent
import com.example.ui.player.PlayerDiagnostics
import com.example.ui.player.RecoveryDecision
import com.example.ui.player.SeriesEpisodeNavigator
import com.example.ui.player.StreamSourceResolver
import com.example.ui.player.StreamSourceType
import com.example.ui.player.SubtitleBackgroundMode
import com.example.ui.player.SubtitleColorPreset
import com.example.ui.player.SubtitleCueItem
import com.example.ui.player.UnifiedPlayerManager
import com.example.ui.player.VodAspectMode
import com.example.ui.player.VodPlayerPreferencesManager
import com.example.ui.player.WatchProgressEntry
import com.example.ui.player.WatchProgressManager
import com.example.ui.player.WatchdogPlayerSnapshot
import com.example.ui.theme.FoulBlackBg
import com.example.ui.theme.FoulGoldStar
import com.example.ui.theme.FoulLiveGreen
import com.example.ui.theme.FoulLiveRed
import com.example.ui.theme.FoulPurpleBright
import com.example.ui.theme.FoulPurpleGlow
import com.example.ui.theme.FoulTextMuted
import com.example.ui.theme.FoulTextPrimary
import com.example.ui.theme.FoulTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val DarkBg = FoulBlackBg
private val ElectricPurple = FoulPurpleBright
private val CyanGlow = Color(0xFF00E5FF)
private val GoldAccent = FoulGoldStar
private val LiveGreen = FoulLiveGreen
private val WarningRed = FoulLiveRed
private val TextPrimary = FoulTextPrimary
private val TextSecondary = FoulTextSecondary
private val TextMuted = FoulTextMuted

private enum class CinemaSettingsTab(val titleAr: String) {
    QUALITY("الجودة والسيرفر"),
    AUDIO_SUBTITLES("الصوت والترجمة"),
    SPEED_ASPECT("السرعة والأبعاد"),
    SEEK_PREFS("التقديم والذاكرة")
}

private data class VideoTrackQualityOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val label: String
)

private data class VodAudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val languageCode: String,
    val channelsCount: Int,
    val isSelected: Boolean
)

private data class VodSubtitleTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val languageCode: String,
    val isSelected: Boolean
)

/**
 * FOUL TV — ULTRA PREMIUM CINEMA PLAYER FOR MOVIES & SERIES.
 *
 * Strictly dedicated to Library VOD (`!playbackRequest.isLiveChannel`), powered by
 * the unified `AndroidX Media3 ExoPlayer` engine (`UnifiedPlayerManager`).
 */
@OptIn(UnstableApi::class)
@Composable
fun CinemaVodPlayerScreen(
    playbackRequest: PlaybackRequest,
    onSelectEpisode: ((SeriesEpisode, Int) -> Unit)? = null,
    onSelectRecommendedItem: ((LibraryMediaItem) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize in-memory preferences and watch progress synchronously before first composition frame
    remember(context) {
        VodPlayerPreferencesManager.initialize(context)
        WatchProgressManager.initialize(context)
        true
    }

    val vodPrefs by VodPlayerPreferencesManager.preferencesFlow.collectAsState()

    val availableServers = remember(playbackRequest) {
        if (playbackRequest.servers.isNotEmpty()) {
            playbackRequest.servers.filter { it.streamUrl.isNotBlank() }
        } else {
            playbackRequest.candidateUrls
                .filter { it.isNotBlank() }
                .mapIndexed { idx, url ->
                    val ext = url.substringBefore('?').substringAfterLast('.', "").uppercase()
                    ChannelServer(
                        id = "vod_srv_${idx + 1}",
                        name = if (ext.isNotBlank() && ext.length <= 5) {
                            "سيرفر ${idx + 1} ($ext)"
                        } else {
                            "سيرفر ${idx + 1}"
                        },
                        streamType = ext.lowercase().ifBlank { "mp4" },
                        streamUrl = url
                    )
                }
        }
    }

    val playerManager = remember(context) { UnifiedPlayerManager(context) }
    val exoPlayer = playerManager.exoPlayer
    val recoveryManager = remember { PlaybackRecoveryManager(maxRetriesPerServer = 2) }

    val initialServerIdx = remember(playbackRequest, availableServers) {
        StreamSourceResolver.selectOptimalInitialServerIndex(
            servers = availableServers,
            isLiveChannel = false,
            deviceCapabilities = playerManager.deviceCapabilities
        )
    }

    var currentServerIndex by remember(playbackRequest) { mutableIntStateOf(initialServerIdx) }
    var activeStreamType by remember(playbackRequest) { mutableStateOf(StreamSourceType.MP4) }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaybackEnded by remember(playbackRequest) { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember(playbackRequest) { mutableStateOf(false) }
    var firstFrameLatencyMs by remember(playbackRequest) { mutableLongStateOf(-1L) }
    var playbackError by remember(playbackRequest) { mutableStateOf<String?>(null) }
    var isAutoRecovering by remember(playbackRequest) { mutableStateOf(false) }
    var recoveryStatusLabel by remember(playbackRequest) { mutableStateOf("") }
    var pendingRecoveryAttempt by remember(playbackRequest) { mutableStateOf<RecoveryDecision?>(null) }

    // Timeline & Scrubbing state
    var currentPositionMs by remember(playbackRequest) { mutableLongStateOf(0L) }
    var durationMs by remember(playbackRequest) { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember(playbackRequest) { mutableLongStateOf(0L) }
    var lastKnownValidPositionMs by remember(playbackRequest) { mutableLongStateOf(0L) }
    var isUserScrubbingSlider by remember { mutableStateOf(false) }
    var sliderPreviewPositionMs by remember { mutableLongStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(true) }

    // Controls & Lock & Sheets state
    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLandscapeLocked by remember { mutableStateOf(true) }
    var aspectMode by remember { mutableStateOf(VodAspectMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var activeSettingsTab by remember { mutableStateOf<CinemaSettingsTab?>(null) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var selectedDrawerSeason by remember(playbackRequest.seasonNumber) {
        mutableIntStateOf(playbackRequest.seasonNumber.coerceAtLeast(1))
    }

    // Quality, Audio, and Subtitle tracks
    val availableVideoQualities = remember { mutableStateListOf<VideoTrackQualityOption>() }
    var isAutoQuality by remember { mutableStateOf(true) }
    var selectedQualityOption by remember { mutableStateOf<VideoTrackQualityOption?>(null) }
    var activeVideoWidth by remember { mutableIntStateOf(0) }
    var activeVideoHeight by remember { mutableIntStateOf(0) }

    val availableAudioTracks = remember { mutableStateListOf<VodAudioTrackOption>() }
    val availableSubtitleTracks = remember { mutableStateListOf<VodSubtitleTrackOption>() }

    // External & Custom Delayed Subtitles
    var externalSubtitleName by remember(playbackRequest) { mutableStateOf<String?>(null) }
    var externalSubtitleCues by remember(playbackRequest) { mutableStateOf<List<SubtitleCueItem>>(emptyList()) }
    var liveEmbeddedCues by remember { mutableStateOf<List<SubtitleCueItem>>(emptyList()) }
    var activeRenderedSubtitleText by remember { mutableStateOf("") }

    // Resume Playback Prompt — resolved synchronously from in-memory WatchProgressManager on first frame
    val initialResumeLookup = remember(playbackRequest) {
        val mediaId = playbackRequest.mediaId.ifBlank {
            playbackRequest.channelId.ifBlank { playbackRequest.title }
        }
        val existing = if (playbackRequest.isSeries) {
            WatchProgressManager.getEpisodeProgress(
                accountId = playbackRequest.accountId,
                seriesId = playbackRequest.seriesId.ifBlank { mediaId },
                seasonNumber = playbackRequest.seasonNumber,
                episodeNumber = playbackRequest.episodeNumber,
                episodeId = playbackRequest.episodeId.ifBlank { mediaId }
            )
        } else {
            WatchProgressManager.getMovieProgress(
                accountId = playbackRequest.accountId,
                movieId = mediaId
            )
        }
        if (WatchProgressManager.shouldOfferResume(existing)) existing else null
    }

    var savedResumeEntry by remember(playbackRequest) { mutableStateOf(initialResumeLookup) }
    var showResumePromptBanner by remember(playbackRequest) { mutableStateOf(initialResumeLookup != null) }
    var initialStartPositionMs by remember(playbackRequest) {
        mutableLongStateOf(initialResumeLookup?.positionMs ?: C.TIME_UNSET)
    }

    // Auto Next Episode state
    val episodeNeighbours = remember(
        playbackRequest.isSeries,
        playbackRequest.seasons,
        playbackRequest.episodesBySeason,
        playbackRequest.seasonNumber,
        playbackRequest.episodeId,
        playbackRequest.episodeNumber
    ) {
        if (playbackRequest.isSeries) {
            SeriesEpisodeNavigator.resolveNeighbours(
                seasons = playbackRequest.seasons,
                episodesBySeason = playbackRequest.episodesBySeason,
                currentSeasonNumber = playbackRequest.seasonNumber,
                currentEpisodeId = playbackRequest.episodeId,
                currentEpisodeNumber = playbackRequest.episodeNumber
            )
        } else {
            null
        }
    }
    var autoNextCancelledForEpisode by remember(playbackRequest) { mutableStateOf(false) }
    var autoNextCountdownSec by remember(playbackRequest) { mutableIntStateOf(10) }

    // Touch Gesture HUD indicators
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudSubtext by remember { mutableStateOf<String?>(null) }
    var gestureHudProgress by remember { mutableStateOf<Float?>(null) }
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) }
    var isHorizontalGestureSeeking by remember { mutableStateOf(false) }
    var horizontalSeekTargetMs by remember { mutableLongStateOf(0L) }

    // SAF File Picker for External Subtitles (.srt, .vtt, .ass)
    val subtitleDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val parsedPair = withContext(Dispatchers.IO) {
                    runCatching {
                        val contentResolver = context.contentResolver
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "external_subtitle.srt"
                        val cues = contentResolver.openInputStream(uri)?.use { stream ->
                            CinemaSubtitleParser.parseSubtitleStream(stream)
                        }.orEmpty()
                        name to cues
                    }.getOrNull()
                }
                if (parsedPair != null && parsedPair.second.isNotEmpty()) {
                    externalSubtitleName = parsedPair.first
                    externalSubtitleCues = parsedPair.second
                    liveEmbeddedCues = emptyList()
                    VodPlayerPreferencesManager.setSubtitlesEnabled(true)
                    // Disable embedded text tracks so embedded & external subtitles never render together
                    if (!playerManager.isReleased()) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    }
                    PlayerDiagnostics.record(
                        event = PlayerDiagnosticEvent.SUBTITLE_LOADED,
                        rawUrl = parsedPair.first,
                        serverIndex = currentServerIndex,
                        sourceType = activeStreamType.label,
                        details = "cuesCount=${parsedPair.second.size}"
                    )
                }
            }
        }
    }

    // Helper to save watch progress safely
    fun persistCurrentWatchProgress(posMs: Long = currentPositionMs, durMs: Long = durationMs) {
        val effectivePos = if (posMs > 0L) posMs else lastKnownValidPositionMs
        val effectiveDur = if (durMs > 0L) durMs else exoPlayer.duration.coerceAtLeast(0L)
        if (effectivePos < WatchProgressManager.MIN_SAVE_POSITION_MS) return

        val mediaId = playbackRequest.mediaId.ifBlank {
            playbackRequest.channelId.ifBlank { playbackRequest.title }
        }
        WatchProgressManager.saveProgress(
            accountId = playbackRequest.accountId,
            mediaId = mediaId,
            title = playbackRequest.title,
            posterUrl = playbackRequest.posterUrl,
            backdropUrl = playbackRequest.backdropUrl,
            containerExtension = playbackRequest.containerExtension,
            isSeries = playbackRequest.isSeries,
            seriesId = playbackRequest.seriesId.ifBlank { mediaId },
            seasonNumber = playbackRequest.seasonNumber,
            episodeNumber = playbackRequest.episodeNumber,
            episodeId = playbackRequest.episodeId,
            episodeTitle = playbackRequest.episodeTitle.ifBlank { playbackRequest.subtitle },
            positionMs = effectivePos,
            durationMs = effectiveDur
        )
    }

    // Helper to seek safely
    fun seekByDeltaMs(deltaMs: Long, feedbackSide: String? = null) {
        val dur = durationMs.takeIf { it > 0L } ?: exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val basePos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs)
        val target = (basePos + deltaMs).coerceIn(0L, dur)
        exoPlayer.seekTo(target)
        currentPositionMs = target
        lastKnownValidPositionMs = target
        if (feedbackSide != null) {
            doubleTapFeedbackSide = feedbackSide
        }
    }

    BackHandler {
        when {
            activeSettingsTab != null -> activeSettingsTab = null
            showEpisodesDrawer -> showEpisodesDrawer = false
            isScreenLocked -> {
                // Show controls briefly so the user sees the Unlock pill
                controlsVisible = true
            }
            else -> {
                persistCurrentWatchProgress()
                onBack()
            }
        }
    }

    // Record diagnostic event when saved watch progress is restored on entry
    LaunchedEffect(playbackRequest, initialResumeLookup) {
        if (initialResumeLookup != null) {
            if (lastKnownValidPositionMs <= 0L) {
                lastKnownValidPositionMs = initialResumeLookup.positionMs
            }
            PlayerDiagnostics.record(
                event = PlayerDiagnosticEvent.VOD_RESUME_RESTORED,
                rawUrl = availableServers.getOrNull(currentServerIndex)?.streamUrl.orEmpty(),
                serverIndex = currentServerIndex,
                sourceType = activeStreamType.label,
                details = "restoredPositionMs=${initialResumeLookup.positionMs}"
            )
        }
    }

    // Hide double-tap indicator after 650ms
    LaunchedEffect(doubleTapFeedbackSide) {
        if (doubleTapFeedbackSide != null) {
            delay(650L)
            doubleTapFeedbackSide = null
        }
    }

    // Hide Resume Prompt Banner automatically after 9 seconds if user doesn't interact
    LaunchedEffect(showResumePromptBanner) {
        if (showResumePromptBanner) {
            delay(9_000L)
            showResumePromptBanner = false
        }
    }

    // Immersive Fullscreen, Keep Screen On, and Orientation Management
    DisposableEffect(view, isLandscapeLocked) {
        view.keepScreenOn = true
        val activity = context as? Activity
        val window = activity?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else null

        insetsController?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        activity?.requestedOrientation = if (isLandscapeLocked) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        onDispose {
            view.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Restore window brightness to system default
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Lifecycle Observer: Save progress once per background cycle, respect manual pause state on resume, and keep PiP active
    var wasPlayingBeforeBackground by remember { mutableStateOf(true) }
    var hasHandledBackgroundTransition by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (playerManager.isReleased()) return@LifecycleEventObserver

            val activity = context as? Activity
            val isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity?.isInPictureInPictureMode == true

            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!isInPip && !hasHandledBackgroundTransition) {
                        wasPlayingBeforeBackground = exoPlayer.playWhenReady && !isPlaybackEnded
                        hasHandledBackgroundTransition = true
                        persistCurrentWatchProgress()
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!isInPip && !hasHandledBackgroundTransition) {
                        wasPlayingBeforeBackground = exoPlayer.playWhenReady && !isPlaybackEnded
                        hasHandledBackgroundTransition = true
                        persistCurrentWatchProgress()
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (hasHandledBackgroundTransition) {
                        hasHandledBackgroundTransition = false
                        if (wasPlayingBeforeBackground && !isPlaybackEnded && playbackError == null) {
                            exoPlayer.playWhenReady = true
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Release UnifiedPlayerManager once when leaving the screen
    DisposableEffect(playerManager) {
        onDispose {
            if (!playerManager.isReleased()) {
                persistCurrentWatchProgress()
            }
            playerManager.release()
        }
    }

    // Initialize recovery state when playbackRequest changes
    LaunchedEffect(playbackRequest) {
        recoveryManager.onChannelOrManualServerSelected(
            initialServerIndex = initialServerIdx,
            totalServers = availableServers.size.coerceAtLeast(1)
        )
    }

    // Load VOD stream immediately on first frame when server or playbackRequest changes
    LaunchedEffect(playbackRequest, currentServerIndex) {
        val server = availableServers.getOrNull(currentServerIndex)
        val url = server?.streamUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            isBuffering = false
            hasRenderedFirstFrame = false
            playbackError = "لا يتوفر رابط تشغيل صالح لهذا المحتوى حالياً."
            return@LaunchedEffect
        }

        playbackError = null
        isPlaybackEnded = false
        isBuffering = true
        hasRenderedFirstFrame = false

        val resumePos = if (lastKnownValidPositionMs > 0L) {
            lastKnownValidPositionMs
        } else {
            initialStartPositionMs
        }

        activeStreamType = playerManager.loadStream(
            channelId = playbackRequest.mediaId.ifBlank { playbackRequest.channelId },
            url = url,
            serverIndex = currentServerIndex,
            serverStreamTypeHint = server?.streamType.orEmpty(),
            serverNameHint = server?.name.orEmpty(),
            isLiveChannel = false,
            startPositionMs = resumePos,
            playImmediately = true,
            downgradeBitrateCap = false
        )
        if (playbackSpeed != 1.0f) {
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
        }
    }

    // Execute RecoveryDecision while strictly preserving current VOD position!
    LaunchedEffect(pendingRecoveryAttempt) {
        val decision = pendingRecoveryAttempt ?: return@LaunchedEffect
        when (decision) {
            is RecoveryDecision.None -> {
                pendingRecoveryAttempt = null
            }
            is RecoveryDecision.SeekToDefaultLiveEdge -> {
                isAutoRecovering = true
                recoveryStatusLabel = decision.reason
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
                pendingRecoveryAttempt = null
            }
            is RecoveryDecision.ReloadCurrentServer -> {
                isAutoRecovering = true
                isBuffering = true
                playbackError = null
                recoveryStatusLabel = "${decision.reason} (${decision.attemptNumber}/2)..."
                if (decision.delayMs > 0L) delay(decision.delayMs)
                val server = availableServers.getOrNull(currentServerIndex)
                val url = server?.streamUrl?.trim().orEmpty()
                if (url.isNotEmpty() && !playerManager.isReleased()) {
                    val preservedPos = exoPlayer.currentPosition
                        .takeIf { it > 0L }
                        ?: lastKnownValidPositionMs
                            .takeIf { it > 0L }
                        ?: C.TIME_UNSET
                    activeStreamType = playerManager.loadStream(
                        channelId = playbackRequest.mediaId.ifBlank { playbackRequest.channelId },
                        url = url,
                        serverIndex = currentServerIndex,
                        serverStreamTypeHint = server?.streamType.orEmpty(),
                        serverNameHint = server?.name.orEmpty(),
                        isLiveChannel = false,
                        startPositionMs = preservedPos,
                        playImmediately = true,
                        downgradeBitrateCap = decision.downgradeBitrateHint
                    )
                }
                pendingRecoveryAttempt = null
            }
            is RecoveryDecision.SwitchToNextServer -> {
                isAutoRecovering = true
                isBuffering = true
                playbackError = null
                val nextIdx = decision.nextServerIndex
                val nextServerName = availableServers.getOrNull(nextIdx)?.name ?: "سيرفر ${nextIdx + 1}"
                recoveryStatusLabel = "${decision.reason} — جارٍ التحويل إلى $nextServerName..."
                if (decision.delayMs > 0L) delay(decision.delayMs)
                val currentPos = exoPlayer.currentPosition.takeIf { it > 0L } ?: lastKnownValidPositionMs
                if (currentPos > 0L) {
                    lastKnownValidPositionMs = currentPos
                }
                recoveryManager.onAutoServerSwitched(
                    newServerIndex = nextIdx,
                    totalServers = availableServers.size.coerceAtLeast(1)
                )
                currentServerIndex = nextIdx
                pendingRecoveryAttempt = null
            }
            is RecoveryDecision.AllServersExhausted -> {
                isAutoRecovering = false
                isBuffering = false
                playbackError = decision.userMessageAr
                pendingRecoveryAttempt = null
            }
        }
    }

    // ExoPlayer Listener: Tracks, First Frame, Cues, Errors, and Playback State
    DisposableEffect(exoPlayer, availableServers) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isBuffering = false
                isAutoRecovering = false
                playbackError = null
                firstFrameLatencyMs = playerManager.lastFirstFrameLatencyMs
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        isPlaybackEnded = false
                    }
                    Player.STATE_READY -> {
                        // Separate STATE_READY from hasRenderedFirstFrame:
                        // hasRenderedFirstFrame is set exclusively inside onRenderedFirstFrame()
                        isBuffering = false
                        isPlaybackEnded = false
                        playbackError = null
                        isAutoRecovering = false
                        recoveryStatusLabel = ""
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        if (pos > 0L) {
                            currentPositionMs = pos
                            lastKnownValidPositionMs = pos
                        }
                        val activeUrl = availableServers.getOrNull(currentServerIndex)?.streamUrl.orEmpty()
                        recoveryManager.onPlaybackReady(
                            currentUrl = activeUrl,
                            sourceType = activeStreamType
                        )
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isPlaying = false
                        isPlaybackEnded = true
                        val dur = exoPlayer.duration.coerceAtLeast(durationMs)
                        if (dur > 0L) {
                            currentPositionMs = dur
                            persistCurrentWatchProgress(posMs = dur, durMs = dur)
                        }
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && currentPositionMs >= WatchProgressManager.MIN_SAVE_POSITION_MS) {
                    persistCurrentWatchProgress()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    activeVideoWidth = videoSize.width
                    activeVideoHeight = videoSize.height
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                val nowPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val extractedText = cueGroup.cues
                    .mapNotNull { it.text?.toString()?.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")

                if (vodPrefs.subtitleDelayOffsetMs == 0L && externalSubtitleCues.isEmpty()) {
                    activeRenderedSubtitleText = if (vodPrefs.subtitlesEnabled) extractedText else ""
                } else if (extractedText.isNotEmpty()) {
                    // Buffer live embedded cue with start timestamp so positive delay offsets work
                    val updated = liveEmbeddedCues.toMutableList()
                    updated.add(
                        SubtitleCueItem(
                            index = updated.size + 1,
                            startTimeMs = nowPos,
                            endTimeMs = nowPos + 3_500L,
                            text = extractedText
                        )
                    )
                    if (updated.size > 80) updated.removeAt(0)
                    liveEmbeddedCues = updated
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val videoOpts = mutableListOf<VideoTrackQualityOption>()
                val audioOpts = mutableListOf<VodAudioTrackOption>()
                val subOpts = mutableListOf<VodSubtitleTrackOption>()

                val supports4k = playerManager.deviceCapabilities.supportsHardware4k
                val supportsHevc = playerManager.deviceCapabilities.supportsHardwareHevc

                tracks.groups.forEachIndexed { groupIdx, group ->
                    when (group.type) {
                        C.TRACK_TYPE_VIDEO -> {
                            for (i in 0 until group.length) {
                                if (!group.isTrackSupported(i)) continue
                                val format = group.getTrackFormat(i)
                                val w = format.width
                                val h = format.height
                                val br = if (format.bitrate != C.INDEX_UNSET) format.bitrate else 0
                                val mime = format.sampleMimeType.orEmpty().lowercase()

                                // Filter out hardware-unsupported 4K or HEVC tracks
                                if (h >= 2160 && !supports4k) continue
                                if ((mime.contains("hevc") || mime.contains("hvc") || mime.contains("dolby")) && !supportsHevc) continue

                                if (h > 0) {
                                    val resBadge = when {
                                        h >= 2160 -> "4K UHD (2160p)"
                                        h >= 1440 -> "QHD (1440p)"
                                        h >= 1080 -> "FHD (1080p)"
                                        h >= 720 -> "HD (720p)"
                                        h >= 480 -> "SD (480p)"
                                        h >= 360 -> "360p"
                                        else -> "${h}p"
                                    }
                                    val mbps = if (br > 0) {
                                        String.format(Locale.US, " • %.1f Mbps", br / 1_000_000f)
                                    } else ""
                                    videoOpts.add(
                                        VideoTrackQualityOption(
                                            groupIndex = groupIdx,
                                            trackIndex = i,
                                            width = w,
                                            height = h,
                                            bitrate = br,
                                            label = "$resBadge$mbps"
                                        )
                                    )
                                }
                            }
                        }
                        C.TRACK_TYPE_AUDIO -> {
                            for (i in 0 until group.length) {
                                if (!group.isTrackSupported(i)) continue
                                val format = group.getTrackFormat(i)
                                val langCode = format.language.orEmpty()
                                val langName = formatLanguageNameAr(langCode, format.label, i + 1)
                                val chCount = if (format.channelCount != C.INDEX_UNSET) format.channelCount else 2
                                val chLabel = when {
                                    chCount >= 6 -> "5.1 محيطي"
                                    chCount == 2 -> "Stereo"
                                    else -> "${chCount}ch"
                                }
                                audioOpts.add(
                                    VodAudioTrackOption(
                                        groupIndex = groupIdx,
                                        trackIndex = i,
                                        label = "$langName ($chLabel)",
                                        languageCode = langCode,
                                        channelsCount = chCount,
                                        isSelected = group.isTrackSelected(i)
                                    )
                                )
                            }
                        }
                        C.TRACK_TYPE_TEXT -> {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val langCode = format.language.orEmpty()
                                val langName = formatLanguageNameAr(langCode, format.label, i + 1)
                                subOpts.add(
                                    VodSubtitleTrackOption(
                                        groupIndex = groupIdx,
                                        trackIndex = i,
                                        label = langName,
                                        languageCode = langCode,
                                        isSelected = group.isTrackSelected(i)
                                    )
                                )
                            }
                        }
                    }
                }

                val distinctVideo = videoOpts
                    .distinctBy { "${it.height}_${it.bitrate / 200_000}" }
                    .sortedWith(compareByDescending<VideoTrackQualityOption> { it.height }.thenByDescending { it.bitrate })
                availableVideoQualities.clear()
                availableVideoQualities.addAll(distinctVideo)

                availableAudioTracks.clear()
                availableAudioTracks.addAll(audioOpts)

                availableSubtitleTracks.clear()
                availableSubtitleTracks.addAll(subOpts)
            }

            override fun onPlayerError(error: PlaybackException) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (pos > 0L) {
                    lastKnownValidPositionMs = pos
                }
                val activeUrl = availableServers.getOrNull(currentServerIndex)?.streamUrl.orEmpty()
                val decision = recoveryManager.onPlayerError(
                    error = error,
                    currentUrl = activeUrl,
                    sourceType = activeStreamType,
                    isLiveChannel = false,
                    totalServers = availableServers.size.coerceAtLeast(1)
                )
                pendingRecoveryAttempt = decision
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Periodic Progress Tracker, Subtitle Sync Evaluator, WatchProgress Saver, and Non-Destructive Watchdog
    LaunchedEffect(exoPlayer, currentServerIndex, availableServers, vodPrefs, externalSubtitleCues) {
        var tickCount = 0
        while (isActive) {
            delay(500L)
            tickCount++

            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            val buf = exoPlayer.bufferedPosition.coerceAtLeast(pos)

            if (!isUserScrubbingSlider && !isHorizontalGestureSeeking) {
                currentPositionMs = pos
            }
            if (pos > 0L) {
                lastKnownValidPositionMs = pos
            }
            if (dur > 0L) {
                durationMs = dur
            }
            bufferedPositionMs = buf

            // Evaluate synchronized subtitle text (External file or Delayed embedded cues)
            if (!vodPrefs.subtitlesEnabled) {
                activeRenderedSubtitleText = ""
            } else if (externalSubtitleCues.isNotEmpty()) {
                activeRenderedSubtitleText = CinemaSubtitleParser.findActiveCueText(
                    cues = externalSubtitleCues,
                    playbackPositionMs = pos,
                    subtitleDelayOffsetMs = vodPrefs.subtitleDelayOffsetMs
                )
            } else if (vodPrefs.subtitleDelayOffsetMs != 0L && liveEmbeddedCues.isNotEmpty()) {
                activeRenderedSubtitleText = CinemaSubtitleParser.findActiveCueText(
                    cues = liveEmbeddedCues,
                    playbackPositionMs = pos,
                    subtitleDelayOffsetMs = vodPrefs.subtitleDelayOffsetMs
                )
            }

            // Save watch progress every 4 seconds (8 ticks of 500ms)
            if (tickCount % 8 == 0 && isPlaying && pos >= WatchProgressManager.MIN_SAVE_POSITION_MS) {
                persistCurrentWatchProgress(posMs = pos, durMs = dur)
            }

            // Run Watchdog every 1.0s (2 ticks) if not paused or ended
            if (tickCount % 2 == 0 && playbackError == null && !isPlaybackEnded && pendingRecoveryAttempt == null) {
                val activeUrl = availableServers.getOrNull(currentServerIndex)?.streamUrl.orEmpty()
                val snapshot = WatchdogPlayerSnapshot(
                    playbackState = exoPlayer.playbackState,
                    playWhenReady = exoPlayer.playWhenReady,
                    isPlaying = exoPlayer.isPlaying,
                    isLoading = exoPlayer.isLoading,
                    currentPositionMs = pos,
                    bufferedPositionMs = buf,
                    isLiveChannel = false,
                    currentServerIndex = currentServerIndex,
                    totalServersCount = availableServers.size.coerceAtLeast(1),
                    currentUrl = activeUrl,
                    sourceType = activeStreamType,
                    nowRealtimeMs = System.currentTimeMillis()
                )
                val decision = recoveryManager.evaluateWatchdogTick(snapshot)
                if (decision !is RecoveryDecision.None) {
                    pendingRecoveryAttempt = decision
                }
            }
        }
    }

    // Auto-Next-Episode 10-second countdown controller
    val showAutoNextCard = remember(
        playbackRequest.isSeries,
        vodPrefs.autoNextEpisodeEnabled,
        episodeNeighbours,
        autoNextCancelledForEpisode,
        currentPositionMs,
        durationMs,
        isPlaybackEnded
    ) {
        SeriesEpisodeNavigator.shouldShowAutoNextPrompt(
            isSeries = playbackRequest.isSeries,
            autoNextEnabled = vodPrefs.autoNextEpisodeEnabled,
            nextEpisode = episodeNeighbours?.nextEpisode,
            isCancelledForCurrentEpisode = autoNextCancelledForEpisode,
            positionMs = currentPositionMs,
            durationMs = durationMs,
            isPlaybackEnded = isPlaybackEnded
        )
    }

    LaunchedEffect(
        showAutoNextCard,
        isPlaying,
        isPlaybackEnded,
        activeSettingsTab,
        showEpisodesDrawer
    ) {
        if (!showAutoNextCard) {
            autoNextCountdownSec = 10
            return@LaunchedEffect
        }
        val nextEp = episodeNeighbours?.nextEpisode ?: return@LaunchedEffect
        val nextSeason = episodeNeighbours.nextSeasonNumber ?: playbackRequest.seasonNumber

        while (isActive && showAutoNextCard) {
            val shouldTick = SeriesEpisodeNavigator.shouldAdvanceCountdownTick(
                isPromptVisible = showAutoNextCard,
                isPlaying = isPlaying,
                isPlaybackEnded = isPlaybackEnded,
                isMenuOrSheetOpen = (activeSettingsTab != null || showEpisodesDrawer)
            )
            if (shouldTick) {
                if (autoNextCountdownSec <= 0) {
                    PlayerDiagnostics.record(
                        event = PlayerDiagnosticEvent.AUTO_NEXT_EPISODE,
                        rawUrl = nextEp.id,
                        serverIndex = currentServerIndex,
                        sourceType = activeStreamType.label,
                        details = "autoTransition to S${nextSeason}E${nextEp.episodeNum}"
                    )
                    persistCurrentWatchProgress(posMs = durationMs, durMs = durationMs)
                    onSelectEpisode?.invoke(nextEp, nextSeason)
                    break
                }
                delay(1_000L)
                autoNextCountdownSec = (autoNextCountdownSec - 1).coerceAtLeast(0)
            } else {
                delay(400L)
            }
        }
    }

    // Auto-hide controls after 4.2 seconds of inactivity while playing
    LaunchedEffect(
        controlsVisible,
        isPlaying,
        isBuffering,
        isUserScrubbingSlider,
        activeSettingsTab,
        showEpisodesDrawer
    ) {
        if (
            controlsVisible &&
            isPlaying &&
            !isBuffering &&
            !isUserScrubbingSlider &&
            activeSettingsTab == null &&
            !showEpisodesDrawer &&
            playbackError == null &&
            !isPlaybackEnded
        ) {
            delay(4_200L)
            controlsVisible = false
        }
    }

    val resizeModeInt = remember(aspectMode) {
        when (aspectMode) {
            VodAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            VodAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            VodAspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            VodAspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            VodAspectMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
        }
    }

    val currentQualityBadge = remember(isAutoQuality, selectedQualityOption, activeVideoHeight) {
        when {
            !isAutoQuality && selectedQualityOption != null -> {
                val h = selectedQualityOption!!.height
                when {
                    h >= 2160 -> "4K"
                    h >= 1440 -> "1440p"
                    h >= 1080 -> "1080p"
                    h >= 720 -> "720p"
                    else -> "${h}p"
                }
            }
            activeVideoHeight >= 2160 -> "Auto (4K)"
            activeVideoHeight >= 1080 -> "Auto (1080p)"
            activeVideoHeight >= 720 -> "Auto (720p)"
            activeVideoHeight > 0 -> "Auto (${activeVideoHeight}p)"
            else -> "تلقائي Auto"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("cinema_vod_player_root")
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                            controlsVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            exoPlayer.playWhenReady = true
                            controlsVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            exoPlayer.playWhenReady = false
                            controlsVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            if (!isScreenLocked) {
                                seekByDeltaMs(vodPrefs.forwardSeekMs, "right")
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            if (!isScreenLocked) {
                                seekByDeltaMs(-vodPrefs.rewindSeekMs, "left")
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!controlsVisible && !isScreenLocked) {
                                // In RTL Arabic layout, Left/Right seek intuition:
                                seekByDeltaMs(vodPrefs.forwardSeekMs, "right")
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!controlsVisible && !isScreenLocked) {
                                seekByDeltaMs(-vodPrefs.rewindSeekMs, "left")
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // 1. Media3 Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = resizeModeInt
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Configure embedded SubtitleView style
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                    }
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.resizeMode = resizeModeInt

                // Hide built-in SubtitleView when custom subtitle overlay is active (external file or non-zero delay) or disabled
                val useBuiltInSubView = vodPrefs.subtitlesEnabled &&
                    externalSubtitleCues.isEmpty() &&
                    vodPrefs.subtitleDelayOffsetMs == 0L
                playerView.subtitleView?.apply {
                    visibility = if (useBuiltInSubView) android.view.View.VISIBLE else android.view.View.GONE
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, vodPrefs.subtitleFontSizeSp)
                    val edgeType = if (vodPrefs.subtitleBackgroundMode == SubtitleBackgroundMode.NONE_OUTLINE) {
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE
                    } else {
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                    }
                    setStyle(
                        CaptionStyleCompat(
                            vodPrefs.subtitleColorPreset.colorArgb,
                            vodPrefs.subtitleBackgroundMode.backgroundColorArgb,
                            vodPrefs.subtitleBackgroundMode.windowColorArgb,
                            edgeType,
                            android.graphics.Color.BLACK,
                            Typeface.DEFAULT_BOLD
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .scale(
                    scaleX = if (aspectMode == VodAspectMode.ZOOM) 1.14f else 1f,
                    scaleY = if (aspectMode == VodAspectMode.ZOOM) 1.14f else 1f
                )
        )

        // 2. Custom Synchronized / External Subtitle Overlay
        if (vodPrefs.subtitlesEnabled && activeRenderedSubtitleText.isNotBlank() &&
            (externalSubtitleCues.isNotEmpty() || vodPrefs.subtitleDelayOffsetMs != 0L)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (controlsVisible && !isScreenLocked) 128.dp else 36.dp,
                        start = 32.dp,
                        end = 32.dp
                    )
            ) {
                val bgColor = when (vodPrefs.subtitleBackgroundMode) {
                    SubtitleBackgroundMode.SEMI_TRANSPARENT -> Color.Black.copy(alpha = 0.72f)
                    SubtitleBackgroundMode.SOLID_BLACK -> Color.Black
                    SubtitleBackgroundMode.NONE_OUTLINE -> Color.Transparent
                }
                Text(
                    text = activeRenderedSubtitleText,
                    color = Color(vodPrefs.subtitleColorPreset.colorArgb),
                    fontSize = vodPrefs.subtitleFontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = (vodPrefs.subtitleFontSizeSp * 1.28f).sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // 3. Central Gesture Zone (Strictly padded away from Top Bar and Bottom Slider so gestures never conflict)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (controlsVisible) 76.dp else 24.dp,
                    bottom = if (controlsVisible) 136.dp else 24.dp
                )
                .pointerInput(isScreenLocked, vodPrefs.forwardSeekSeconds, vodPrefs.rewindSeekSeconds) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                        },
                        onDoubleTap = { offset ->
                            if (isScreenLocked) {
                                controlsVisible = true
                                return@detectTapGestures
                            }
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            when {
                                offset.x < width * 0.36f -> {
                                    seekByDeltaMs(-vodPrefs.rewindSeekMs, "left")
                                }
                                offset.x > width * 0.64f -> {
                                    seekByDeltaMs(vodPrefs.forwardSeekMs, "right")
                                }
                                else -> {
                                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                    controlsVisible = true
                                }
                            }
                        }
                    )
                }
                .pointerInput(isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    var dragStartX = 0f
                    var accumulatedDy = 0f
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                    var initialVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVol / 2)
                    var initialBrightness = 0.55f

                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            dragStartX = startOffset.x
                            accumulatedDy = 0f
                            initialVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVol / 2)
                            val activity = context as? Activity
                            val currentAttrBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            initialBrightness = if (currentAttrBrightness in 0.05f..1.0f) currentAttrBrightness else 0.55f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDy -= dragAmount
                            val height = size.height.toFloat().coerceAtLeast(240f)
                            val deltaRatio = (accumulatedDy / (height * 0.72f)).coerceIn(-1f, 1f)
                            val isRightSide = dragStartX >= size.width * 0.5f

                            if (isRightSide) {
                                // Volume control
                                val targetVolFloat = (initialVolume.toFloat() / maxVol.toFloat() + deltaRatio)
                                    .coerceIn(0f, 1f)
                                val targetVolInt = (targetVolFloat * maxVol).toInt().coerceIn(0, maxVol)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)
                                val pct = ((targetVolInt.toFloat() / maxVol.toFloat()) * 100).toInt()
                                gestureHudText = "مستوى الصوت $pct%"
                                gestureHudSubtext = if (pct == 0) "صامت" else "سحب عمودي للتحكم بالصوت"
                                gestureHudProgress = targetVolInt.toFloat() / maxVol.toFloat()
                            } else {
                                // Screen Brightness control
                                val targetBrightness = (initialBrightness + deltaRatio).coerceIn(0.05f, 1.0f)
                                val activity = context as? Activity
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = targetBrightness
                                    win.attributes = lp
                                }
                                val pct = (targetBrightness * 100).toInt()
                                gestureHudText = "إضاءة الشاشة $pct%"
                                gestureHudSubtext = "سحب عمودي للتحكم بالسطوع"
                                gestureHudProgress = targetBrightness
                            }
                        },
                        onDragEnd = {
                            gestureHudText = null
                            gestureHudSubtext = null
                            gestureHudProgress = null
                        },
                        onDragCancel = {
                            gestureHudText = null
                            gestureHudSubtext = null
                            gestureHudProgress = null
                        }
                    )
                }
                .pointerInput(isScreenLocked, durationMs) {
                    if (isScreenLocked || durationMs <= 0L) return@pointerInput
                    var startSeekPosMs = 0L
                    var accumulatedDx = 0f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            startSeekPosMs = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs)
                            accumulatedDx = 0f
                            isHorizontalGestureSeeking = true
                            horizontalSeekTargetMs = startSeekPosMs
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDx += dragAmount
                            val width = size.width.toFloat().coerceAtLeast(300f)
                            // Full width swipe = up to 180 seconds (3 minutes) of smooth scrubbing
                            val deltaMs = ((accumulatedDx / width) * 180_000f).roundToLong()
                            val targetMs = (startSeekPosMs + deltaMs).coerceIn(0L, durationMs)
                            horizontalSeekTargetMs = targetMs
                            currentPositionMs = targetMs
                            val diffSec = (targetMs - startSeekPosMs) / 1000L
                            val sign = if (diffSec >= 0) "+" else ""
                            gestureHudText = formatCinemaTime(targetMs)
                            gestureHudSubtext = "$sign${diffSec} ثانية • ${formatCinemaTime(durationMs)}"
                            gestureHudProgress = (targetMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (isHorizontalGestureSeeking) {
                                exoPlayer.seekTo(horizontalSeekTargetMs)
                                lastKnownValidPositionMs = horizontalSeekTargetMs
                            }
                            isHorizontalGestureSeeking = false
                            gestureHudText = null
                            gestureHudSubtext = null
                            gestureHudProgress = null
                        },
                        onDragCancel = {
                            isHorizontalGestureSeeking = false
                            gestureHudText = null
                            gestureHudSubtext = null
                            gestureHudProgress = null
                        }
                    )
                }
        )

        // 4. Double-Tap Left/Right Visual Ripple Indicator
        AnimatedVisibility(
            visible = doubleTapFeedbackSide != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (doubleTapFeedbackSide == "left") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 42.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.FastRewind, contentDescription = null, tint = CyanGlow)
                            Text(
                                text = "-${vodPrefs.rewindSeekSeconds} ثوانٍ",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (doubleTapFeedbackSide == "right") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 42.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "+${vodPrefs.forwardSeekSeconds} ثوانٍ",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = CyanGlow)
                        }
                    }
                }
            }
        }

        // 5. Gesture HUD Pill (Volume / Brightness / Horizontal Seek Preview)
        AnimatedVisibility(
            visible = gestureHudText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp)
        ) {
            Surface(
                color = Color(0xEB0A0915),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.5f)),
                modifier = Modifier.widthIn(min = 210.dp, max = 300.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = gestureHudText.orEmpty(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    if (!gestureHudSubtext.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = gestureHudSubtext.orEmpty(),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    gestureHudProgress?.let { progress ->
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            color = CyanGlow,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // 6. Resume Watch Progress Banner ("متابعة المشاهدة" vs "البدء من البداية")
        AnimatedVisibility(
            visible = showResumePromptBanner && savedResumeEntry != null && !isScreenLocked,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (controlsVisible) 76.dp else 20.dp, start = 20.dp, end = 20.dp)
        ) {
            val entry = savedResumeEntry
            if (entry != null) {
                Surface(
                    color = Color(0xF20D0C1D),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .testTag("vod_resume_prompt_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تم استئناف المشاهدة من ${formatCinemaTime(entry.positionMs)}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "يمكنك المتابعة أو البدء من أول العرض",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                showResumePromptBanner = false
                                lastKnownValidPositionMs = 0L
                                currentPositionMs = 0L
                                exoPlayer.seekTo(0L)
                                exoPlayer.playWhenReady = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("vod_start_from_beginning_btn")
                        ) {
                            Text("من البداية", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showResumePromptBanner = false },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("vod_continue_watching_btn")
                        ) {
                            Text("متابعة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // 7. Fast-Startup / Buffering Overlay (Immediately disappears on first rendered frame unless buffering)
        if ((isBuffering || !hasRenderedFirstFrame || isAutoRecovering) && playbackError == null && !isPlaybackEnded) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xCC070610))
                    .border(1.dp, ElectricPurple.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = CyanGlow,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = playbackRequest.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAutoRecovering && recoveryStatusLabel.isNotBlank()) {
                            recoveryStatusLabel
                        } else if (!hasRenderedFirstFrame) {
                            "جارٍ تحضير العرض السينمائي بأعلى سرعة..."
                        } else {
                            "جارٍ مزامنة التدفق..."
                        },
                        color = CyanGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 8. Error State Overlay with Position-Preserving Retry & Server Switch
        if (playbackError != null) {
            Surface(
                color = Color(0xF20B0A16),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, WarningRed.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 500.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = WarningRed,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "تعذر تشغيل المصدر الحالي",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playbackError.orEmpty(),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (lastKnownValidPositionMs >= WatchProgressManager.MIN_SAVE_POSITION_MS) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تم حفظ موضع المشاهدة عند ${formatCinemaTime(lastKnownValidPositionMs)}",
                            color = CyanGlow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                playbackError = null
                                isBuffering = true
                                recoveryManager.onChannelOrManualServerSelected(
                                    initialServerIndex = currentServerIndex,
                                    totalServers = availableServers.size.coerceAtLeast(1)
                                )
                                val srv = availableServers.getOrNull(currentServerIndex)
                                val url = srv?.streamUrl?.trim().orEmpty()
                                if (url.isNotEmpty()) {
                                    activeStreamType = playerManager.loadStream(
                                        channelId = playbackRequest.mediaId.ifBlank { playbackRequest.channelId },
                                        url = url,
                                        serverIndex = currentServerIndex,
                                        serverStreamTypeHint = srv?.streamType.orEmpty(),
                                        serverNameHint = srv?.name.orEmpty(),
                                        isLiveChannel = false,
                                        startPositionMs = lastKnownValidPositionMs.takeIf { it > 0L } ?: C.TIME_UNSET,
                                        playImmediately = true
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("vod_retry_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                        }

                        if (availableServers.size > 1) {
                            OutlinedButton(
                                onClick = {
                                    playbackError = null
                                    val nextIdx = (currentServerIndex + 1) % availableServers.size
                                    recoveryManager.onChannelOrManualServerSelected(
                                        initialServerIndex = nextIdx,
                                        totalServers = availableServers.size
                                    )
                                    currentServerIndex = nextIdx
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.6f)),
                                modifier = Modifier.testTag("vod_switch_server_btn")
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تبديل السيرفر", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                persistCurrentWatchProgress()
                                onBack()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Text("رجوع", color = TextSecondary)
                        }
                    }
                }
            }
        }

        // 9. Professional End-of-Movie Screen (When a Movie finishes playing)
        AnimatedVisibility(
            visible = isPlaybackEnded && !playbackRequest.isSeries && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.86f))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 720.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "انتهى عرض الفيلم • ${playbackRequest.title}",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                isPlaybackEnded = false
                                lastKnownValidPositionMs = 0L
                                currentPositionMs = 0L
                                exoPlayer.seekTo(0L)
                                exoPlayer.playWhenReady = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("vod_replay_movie_btn")
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إعادة التشغيل من البداية", fontWeight = FontWeight.ExtraBold)
                        }
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text("العودة للمكتبة", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (playbackRequest.recommendedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(22.dp))
                        Text(
                            text = "أفلام مقترحة لك",
                            color = CyanGlow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(playbackRequest.recommendedItems.take(10), key = { it.id }) { recItem ->
                                Surface(
                                    onClick = {
                                        onSelectRecommendedItem?.invoke(recItem)
                                    },
                                    color = Color(0xFF141226),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                    modifier = Modifier.width(132.dp)
                                ) {
                                    Column {
                                        AsyncImage(
                                            model = recItem.posterUrl,
                                            contentDescription = recItem.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                        )
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = recItem.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (recItem.rating.isNotBlank()) {
                                                Text(
                                                    text = "★ ${recItem.rating}",
                                                    color = GoldAccent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 10. Auto-Next-Episode Floating Card (Series Only)
        AnimatedVisibility(
            visible = showAutoNextCard && episodeNeighbours?.nextEpisode != null && !isScreenLocked,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = if (controlsVisible) 136.dp else 28.dp,
                    end = 24.dp,
                    start = 24.dp
                )
        ) {
            val nextEp = episodeNeighbours?.nextEpisode
            val nextSeason = episodeNeighbours?.nextSeasonNumber ?: playbackRequest.seasonNumber
            if (nextEp != null) {
                Surface(
                    color = Color(0xF50E0C1D),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, CyanGlow.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .testTag("vod_auto_next_episode_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        if (nextEp.coverUrl.isNotBlank() || playbackRequest.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = nextEp.coverUrl.ifBlank { playbackRequest.posterUrl },
                                contentDescription = nextEp.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 86.dp, height = 58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الحلقة التالية خلال $autoNextCountdownSec ثوانٍ",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "الموسم $nextSeason • الحلقة ${nextEp.episodeNum}: ${nextEp.title}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        persistCurrentWatchProgress(posMs = durationMs, durMs = durationMs)
                                        onSelectEpisode?.invoke(nextEp, nextSeason)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vod_auto_next_play_now_btn")
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تشغيل الآن", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { autoNextCancelledForEpisode = true },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vod_auto_next_cancel_btn")
                                ) {
                                    Text("إلغاء", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 11. Screen Locked Pill (When Screen Lock is active)
        AnimatedVisibility(
            visible = isScreenLocked && controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        ) {
            Surface(
                onClick = { isScreenLocked = false },
                color = Color(0xE6100E20),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, GoldAccent),
                modifier = Modifier.testTag("vod_unlock_screen_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "إلغاء قفل الشاشة", tint = GoldAccent)
                    Text(
                        text = "الشاشة مقفلة • اضغط لفتح القفل",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 12. Full Cinema Ultra Controls Overlay (Top Bar + Center Transport + Bottom Scrubbing & Actions)
        AnimatedVisibility(
            visible = controlsVisible && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Scrim Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.88f),
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom Scrim Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.94f)
                                )
                            )
                        )
                )

                // TOP BAR
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CinemaIconPillButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            onClick = {
                                persistCurrentWatchProgress()
                                onBack()
                            },
                            testTag = "vod_back_btn"
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = playbackRequest.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (playbackRequest.isSeries) {
                                    Surface(
                                        color = ElectricPurple.copy(alpha = 0.28f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.65f))
                                    ) {
                                        Text(
                                            text = "S${playbackRequest.seasonNumber} • E${playbackRequest.episodeNumber}",
                                            color = CyanGlow,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            val subtitleLine = when {
                                playbackRequest.isSeries && playbackRequest.episodeTitle.isNotBlank() ->
                                    "الموسم ${playbackRequest.seasonNumber} • الحلقة ${playbackRequest.episodeNumber}: ${playbackRequest.episodeTitle}"
                                playbackRequest.subtitle.isNotBlank() ->
                                    playbackRequest.subtitle
                                else ->
                                    listOfNotNull(
                                        playbackRequest.releaseYear.takeIf { it.isNotBlank() },
                                        playbackRequest.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                                        activeStreamType.label
                                    ).joinToString(" • ")
                            }
                            if (subtitleLine.isNotBlank()) {
                                Text(
                                    text = subtitleLine,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Top-Right Action Icons: PiP, Orientation, Screen Lock, Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canEnterPictureInPicture(context)) {
                            CinemaIconPillButton(
                                icon = Icons.Default.PictureInPictureAlt,
                                contentDescription = "نافذة عائمة PiP",
                                onClick = { enterCinemaPipMode(context, activeVideoWidth, activeVideoHeight) },
                                testTag = "vod_pip_btn"
                            )
                        }

                        CinemaIconPillButton(
                            icon = Icons.Default.ScreenRotation,
                            contentDescription = "تدوير الشاشة",
                            onClick = { isLandscapeLocked = !isLandscapeLocked },
                            testTag = "vod_orientation_btn"
                        )

                        CinemaIconPillButton(
                            icon = Icons.Default.Lock,
                            contentDescription = "قفل الشاشة",
                            onClick = { isScreenLocked = true },
                            testTag = "vod_lock_btn"
                        )

                        CinemaIconPillButton(
                            icon = Icons.Default.Settings,
                            contentDescription = "إعدادات المشغل",
                            onClick = { activeSettingsTab = CinemaSettingsTab.QUALITY },
                            testTag = "vod_settings_btn"
                        )
                    }
                }

                // CENTER TRANSPORT CONTROLS (Rewind -10s, Play/Pause, Fast-Forward +10s)
                if (playbackError == null && !isPlaybackEnded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        // Rewind button
                        CinemaTransportCircleButton(
                            icon = Icons.Default.FastRewind,
                            badgeText = "-${vodPrefs.rewindSeekSeconds}s",
                            contentDescription = "إرجاع ${vodPrefs.rewindSeekSeconds} ثوانٍ",
                            onClick = { seekByDeltaMs(-vodPrefs.rewindSeekMs, "left") },
                            testTag = "vod_center_rewind_btn"
                        )

                        // Primary Play/Pause button
                        Surface(
                            onClick = {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.playWhenReady = true
                                } else {
                                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                }
                            },
                            color = ElectricPurple.copy(alpha = 0.88f),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, CyanGlow.copy(alpha = 0.8f)),
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("vod_center_play_pause_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        // Fast-Forward button
                        CinemaTransportCircleButton(
                            icon = Icons.Default.FastForward,
                            badgeText = "+${vodPrefs.forwardSeekSeconds}s",
                            contentDescription = "تقديم ${vodPrefs.forwardSeekSeconds} ثوانٍ",
                            onClick = { seekByDeltaMs(vodPrefs.forwardSeekMs, "right") },
                            testTag = "vod_center_forward_btn"
                        )
                    }
                }

                // BOTTOM CONTROL BAR (Progress Slider + Action Chips)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Scrubbing Time Preview Bubble when dragging slider
                    AnimatedVisibility(
                        visible = isUserScrubbingSlider,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Surface(
                            color = Color(0xF0121024),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyanGlow),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "الانتقال إلى ${formatCinemaTime(sliderPreviewPositionMs)} / ${formatCinemaTime(durationMs)}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Progress Slider Row
                    val effectiveSliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    val effectiveSliderValue = (if (isUserScrubbingSlider) sliderPreviewPositionMs else currentPositionMs)
                        .coerceIn(0L, durationMs.coerceAtLeast(1L))
                        .toFloat()
                    val bufferedFraction = if (durationMs > 0L) {
                        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatCinemaTime(if (isUserScrubbingSlider) sliderPreviewPositionMs else currentPositionMs),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            // Buffered track indicator behind Slider
                            LinearProgressIndicator(
                                progress = { bufferedFraction },
                                color = Color.White.copy(alpha = 0.32f),
                                trackColor = Color.White.copy(alpha = 0.10f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )

                            Slider(
                                value = effectiveSliderValue,
                                onValueChange = { newVal ->
                                    isUserScrubbingSlider = true
                                    sliderPreviewPositionMs = newVal.roundToLong()
                                },
                                onValueChangeFinished = {
                                    val target = sliderPreviewPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                    lastKnownValidPositionMs = target
                                    isUserScrubbingSlider = false
                                },
                                valueRange = 0f..effectiveSliderMax,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyanGlow,
                                    activeTrackColor = ElectricPurple,
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vod_progress_slider")
                            )
                        }

                        val rightTimeLabel = if (showRemainingTime && durationMs > currentPositionMs) {
                            "-${formatCinemaTime(durationMs - currentPositionMs)}"
                        } else {
                            formatCinemaTime(durationMs)
                        }
                        Text(
                            text = rightTimeLabel,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showRemainingTime = !showRemainingTime }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom Action Buttons Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Play/Pause small pill
                            CinemaActionChip(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                label = if (isPlaying) "إيقاف" else "تشغيل",
                                isHighlighted = true,
                                onClick = { exoPlayer.playWhenReady = !exoPlayer.playWhenReady },
                                testTag = "vod_bottom_play_pause_btn"
                            )

                            // Series Controls: Previous Episode, Next Episode, Seasons & Episodes Drawer
                            if (playbackRequest.isSeries) {
                                val prevEp = episodeNeighbours?.previousEpisode
                                val prevSeason = episodeNeighbours?.previousSeasonNumber ?: playbackRequest.seasonNumber
                                if (prevEp != null) {
                                    CinemaActionChip(
                                        icon = Icons.Default.SkipPrevious,
                                        label = "السابقة",
                                        onClick = {
                                            persistCurrentWatchProgress()
                                            onSelectEpisode?.invoke(prevEp, prevSeason)
                                        },
                                        testTag = "vod_prev_episode_btn"
                                    )
                                }

                                val nextEp = episodeNeighbours?.nextEpisode
                                val nextSeason = episodeNeighbours?.nextSeasonNumber ?: playbackRequest.seasonNumber
                                if (nextEp != null) {
                                    CinemaActionChip(
                                        icon = Icons.Default.SkipNext,
                                        label = "التالية (E${nextEp.episodeNum})",
                                        onClick = {
                                            persistCurrentWatchProgress()
                                            onSelectEpisode?.invoke(nextEp, nextSeason)
                                        },
                                        testTag = "vod_next_episode_btn"
                                    )
                                }

                                if (playbackRequest.episodesBySeason.isNotEmpty()) {
                                    CinemaActionChip(
                                        icon = Icons.Default.VideoLibrary,
                                        label = "الحلقات",
                                        onClick = {
                                            selectedDrawerSeason = playbackRequest.seasonNumber.coerceAtLeast(1)
                                            showEpisodesDrawer = true
                                        },
                                        testTag = "vod_episodes_drawer_btn"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Audio & Subtitles button
                            val subBadge = when {
                                !vodPrefs.subtitlesEnabled -> "إيقاف"
                                externalSubtitleName != null -> "خارجية"
                                availableSubtitleTracks.any { it.isSelected } -> "مفعلة"
                                else -> "الصوت والترجمة"
                            }
                            CinemaActionChip(
                                icon = Icons.Default.Subtitles,
                                label = subBadge,
                                onClick = { activeSettingsTab = CinemaSettingsTab.AUDIO_SUBTITLES },
                                testTag = "vod_audio_subtitles_btn"
                            )

                            // Playback Speed button
                            CinemaActionChip(
                                icon = Icons.Default.Speed,
                                label = "${playbackSpeed}x",
                                onClick = { activeSettingsTab = CinemaSettingsTab.SPEED_ASPECT },
                                testTag = "vod_speed_btn"
                            )

                            // Quality & Server button
                            CinemaActionChip(
                                icon = Icons.Default.HighQuality,
                                label = currentQualityBadge,
                                onClick = { activeSettingsTab = CinemaSettingsTab.QUALITY },
                                testTag = "vod_quality_btn"
                            )

                            // Aspect Ratio cycle button
                            CinemaActionChip(
                                icon = Icons.Default.AspectRatio,
                                label = aspectMode.arabicLabel,
                                onClick = { aspectMode = aspectMode.next() },
                                testTag = "vod_aspect_btn"
                            )
                        }
                    }
                }
            }
        }

        // 13. In-Player Seasons & Episodes Browser Sheet (For Series)
        if (showEpisodesDrawer && playbackRequest.isSeries) {
            CinemaEpisodesBrowserModal(
                playbackRequest = playbackRequest,
                selectedSeason = selectedDrawerSeason,
                onSelectSeason = { selectedDrawerSeason = it },
                onSelectEpisode = { ep, seasonNum ->
                    showEpisodesDrawer = false
                    persistCurrentWatchProgress()
                    onSelectEpisode?.invoke(ep, seasonNum)
                },
                onDismiss = { showEpisodesDrawer = false }
            )
        }

        // 14. Advanced In-Player Settings Modal (Quality/Server, Audio/Subtitles + ±0.25s Delay + External File, Speed/Aspect, Seek Step)
        if (activeSettingsTab != null) {
            CinemaAdvancedSettingsModal(
                activeTab = activeSettingsTab!!,
                onSelectTab = { activeSettingsTab = it },
                availableServers = availableServers,
                currentServerIndex = currentServerIndex,
                onSelectServer = { idx ->
                    if (idx != currentServerIndex && idx in availableServers.indices) {
                        val pos = exoPlayer.currentPosition.takeIf { it > 0L } ?: lastKnownValidPositionMs
                        if (pos > 0L) lastKnownValidPositionMs = pos
                        recoveryManager.onChannelOrManualServerSelected(
                            initialServerIndex = idx,
                            totalServers = availableServers.size
                        )
                        currentServerIndex = idx
                    }
                },
                availableVideoQualities = availableVideoQualities,
                isAutoQuality = isAutoQuality,
                selectedQualityOption = selectedQualityOption,
                onSelectAutoQuality = {
                    isAutoQuality = true
                    selectedQualityOption = null
                    playerManager.applyQualityMode(isAutoQuality = true, isDataSaver = false)
                },
                onSelectManualQuality = { option ->
                    isAutoQuality = false
                    selectedQualityOption = option
                    val groups = exoPlayer.currentTracks.groups
                    if (option.groupIndex in groups.indices) {
                        val mediaTrackGroup = groups[option.groupIndex].mediaTrackGroup
                        playerManager.applyQualityMode(
                            isAutoQuality = false,
                            isDataSaver = false,
                            manualTrackModification = { builder ->
                                builder.clearVideoSizeConstraints()
                                builder.setMaxVideoBitrate(Int.MAX_VALUE)
                                builder.addOverride(
                                    TrackSelectionOverride(
                                        mediaTrackGroup,
                                        listOf(option.trackIndex)
                                    )
                                )
                            }
                        )
                    }
                },
                availableAudioTracks = availableAudioTracks,
                onSelectAudioTrack = { audioOpt ->
                    val groups = exoPlayer.currentTracks.groups
                    if (audioOpt.groupIndex in groups.indices) {
                        val mediaGroup = groups[audioOpt.groupIndex].mediaTrackGroup
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .addOverride(TrackSelectionOverride(mediaGroup, listOf(audioOpt.trackIndex)))
                            .build()
                    }
                },
                availableSubtitleTracks = availableSubtitleTracks,
                externalSubtitleName = externalSubtitleName,
                vodPrefs = vodPrefs,
                onSelectEmbeddedSubtitle = { subOpt ->
                    externalSubtitleName = null
                    externalSubtitleCues = emptyList()
                    VodPlayerPreferencesManager.setSubtitlesEnabled(true)
                    val groups = exoPlayer.currentTracks.groups
                    if (subOpt.groupIndex in groups.indices) {
                        val mediaGroup = groups[subOpt.groupIndex].mediaTrackGroup
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .addOverride(TrackSelectionOverride(mediaGroup, listOf(subOpt.trackIndex)))
                            .build()
                    }
                },
                onDisableSubtitles = {
                    VodPlayerPreferencesManager.setSubtitlesEnabled(false)
                    activeRenderedSubtitleText = ""
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                },
                onOpenExternalSubtitlePicker = {
                    subtitleDocumentLauncher.launch(
                        arrayOf(
                            "application/x-subrip",
                            "text/vtt",
                            "text/plain",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                playbackSpeed = playbackSpeed,
                onSelectPlaybackSpeed = { speed ->
                    playbackSpeed = speed
                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                },
                aspectMode = aspectMode,
                onSelectAspectMode = { aspectMode = it },
                firstFrameLatencyMs = firstFrameLatencyMs,
                activeStreamType = activeStreamType,
                onDismiss = { activeSettingsTab = null }
            )
        }
    }
}

@Composable
private fun CinemaIconPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = if (isFocused) ElectricPurple.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.55f),
        shape = CircleShape,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) CyanGlow else Color.White.copy(alpha = 0.22f)
        ),
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CinemaTransportCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = if (isFocused) ElectricPurple.copy(alpha = 0.48f) else Color.Black.copy(alpha = 0.62f),
        shape = CircleShape,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.2.dp,
            color = if (isFocused) CyanGlow else Color.White.copy(alpha = 0.30f)
        ),
        modifier = Modifier
            .size(58.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .testTag(testTag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = badgeText,
                color = CyanGlow,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun CinemaActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = when {
            isFocused -> ElectricPurple.copy(alpha = 0.55f)
            isHighlighted -> ElectricPurple.copy(alpha = 0.34f)
            else -> Color(0xCC110F22)
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> CyanGlow
                isHighlighted -> ElectricPurple.copy(alpha = 0.8f)
                else -> Color.White.copy(alpha = 0.18f)
            }
        ),
        modifier = Modifier
            .height(42.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isHighlighted || isFocused) CyanGlow else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CinemaEpisodesBrowserModal(
    playbackRequest: PlaybackRequest,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (SeriesEpisode, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val seasons = remember(playbackRequest.seasons, playbackRequest.episodesBySeason) {
        if (playbackRequest.seasons.isNotEmpty()) {
            playbackRequest.seasons
        } else {
            playbackRequest.episodesBySeason.keys.sorted().map { num ->
                SeriesSeason(
                    seasonNumber = num,
                    name = "الموسم $num",
                    episodeCount = playbackRequest.episodesBySeason[num]?.size ?: 0,
                    coverUrl = playbackRequest.posterUrl
                )
            }
        }
    }
    val episodes = playbackRequest.episodesBySeason[selectedSeason].orEmpty()

    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            color = Color(0xFA0C0B18),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.45f)),
            modifier = Modifier
                .widthIn(min = 340.dp, max = 460.dp)
                .fillMaxHeight()
                .clickable(enabled = false) {}
                .testTag("vod_episodes_modal")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "المواسم والحلقات",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = playbackRequest.title,
                            color = CyanGlow,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Seasons Horizontal Selector
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { it.seasonNumber }) { season ->
                        val isSelected = season.seasonNumber == selectedSeason
                        Surface(
                            onClick = { onSelectSeason(season.seasonNumber) },
                            color = if (isSelected) ElectricPurple else Color(0xFF17152B),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CyanGlow else Color.White.copy(alpha = 0.14f)
                            )
                        ) {
                            Text(
                                text = season.name.ifBlank { "الموسم ${season.seasonNumber}" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                // Episodes List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(episodes, key = { it.id.ifBlank { "${it.seasonNum}_${it.episodeNum}" } }) { ep ->
                        val isCurrentEpisode = (ep.id.isNotBlank() && ep.id == playbackRequest.episodeId) ||
                            (selectedSeason == playbackRequest.seasonNumber && ep.episodeNum == playbackRequest.episodeNumber)
                        val epProgress = WatchProgressManager.getEpisodeProgress(
                            accountId = playbackRequest.accountId,
                            seriesId = playbackRequest.seriesId.ifBlank { playbackRequest.mediaId },
                            seasonNumber = selectedSeason,
                            episodeNumber = ep.episodeNum,
                            episodeId = ep.id
                        )

                        Surface(
                            onClick = { onSelectEpisode(ep, selectedSeason) },
                            color = if (isCurrentEpisode) ElectricPurple.copy(alpha = 0.30f) else Color(0xFF141225),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isCurrentEpisode) 1.5.dp else 1.dp,
                                color = if (isCurrentEpisode) CyanGlow else Color.White.copy(alpha = 0.10f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = if (isCurrentEpisode) CyanGlow else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "E${ep.episodeNum}",
                                            color = if (isCurrentEpisode) Color.Black else Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ep.title.ifBlank { "الحلقة ${ep.episodeNum}" },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (ep.duration.isNotBlank()) {
                                            Text(
                                                text = ep.duration,
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    if (isCurrentEpisode) {
                                        Text(
                                            text = "يعرض الآن",
                                            color = LiveGreen,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        )
                                    } else if (epProgress?.isCompleted == true) {
                                        Text(
                                            text = "تمت المشاهدة ✓",
                                            color = CyanGlow,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (epProgress != null && epProgress.progressFraction > 0.02f) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { epProgress.progressFraction },
                                        color = if (epProgress.isCompleted) LiveGreen else CyanGlow,
                                        trackColor = Color.White.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaAdvancedSettingsModal(
    activeTab: CinemaSettingsTab,
    onSelectTab: (CinemaSettingsTab) -> Unit,
    availableServers: List<ChannelServer>,
    currentServerIndex: Int,
    onSelectServer: (Int) -> Unit,
    availableVideoQualities: List<VideoTrackQualityOption>,
    isAutoQuality: Boolean,
    selectedQualityOption: VideoTrackQualityOption?,
    onSelectAutoQuality: () -> Unit,
    onSelectManualQuality: (VideoTrackQualityOption) -> Unit,
    availableAudioTracks: List<VodAudioTrackOption>,
    onSelectAudioTrack: (VodAudioTrackOption) -> Unit,
    availableSubtitleTracks: List<VodSubtitleTrackOption>,
    externalSubtitleName: String?,
    vodPrefs: com.example.ui.player.VodPlayerPreferences,
    onSelectEmbeddedSubtitle: (VodSubtitleTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onOpenExternalSubtitlePicker: () -> Unit,
    playbackSpeed: Float,
    onSelectPlaybackSpeed: (Float) -> Unit,
    aspectMode: VodAspectMode,
    onSelectAspectMode: (VodAspectMode) -> Unit,
    firstFrameLatencyMs: Long,
    activeStreamType: StreamSourceType,
    onDismiss: () -> Unit
) {
    val speeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            color = Color(0xFA0C0B1A),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.55f)),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .clickable(enabled = false) {}
                .testTag("vod_advanced_settings_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header + Tabs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "إعدادات المشغل السينمائي الاحترافي",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                // Tab Strip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    CinemaSettingsTab.entries.forEach { tab ->
                        val isSelected = tab == activeTab
                        Surface(
                            onClick = { onSelectTab(tab) },
                            color = if (isSelected) ElectricPurple else Color(0xFF17152B),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CyanGlow else Color.White.copy(alpha = 0.14f)
                            )
                        ) {
                            Text(
                                text = tab.titleAr,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (activeTab) {
                        CinemaSettingsTab.QUALITY -> {
                            // 1. Video Qualities
                            Text(
                                text = "جودة الفيديو المتاحة في المصدر",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                CinemaSelectablePill(
                                    label = "تلقائي ذكي (Auto)",
                                    selected = isAutoQuality,
                                    onClick = onSelectAutoQuality
                                )
                                availableVideoQualities.forEach { qOpt ->
                                    CinemaSelectablePill(
                                        label = qOpt.label,
                                        selected = !isAutoQuality && selectedQualityOption == qOpt,
                                        onClick = { onSelectManualQuality(qOpt) }
                                    )
                                }
                            }
                            if (availableVideoQualities.isEmpty()) {
                                Text(
                                    text = "المصدر الحالي (${activeStreamType.label}) يعمل ببث مباشر أحادي المسار بأعلى دقة متوفرة.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // 2. Servers / Container Extensions
                            if (availableServers.size > 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "سيرفرات وصيغ التشغيل البديلة (مع حفظ الدقيقة الحالية)",
                                    color = CyanGlow,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    availableServers.forEachIndexed { idx, srv ->
                                        CinemaSelectablePill(
                                            label = srv.name,
                                            selected = idx == currentServerIndex,
                                            onClick = { onSelectServer(idx) }
                                        )
                                    }
                                }
                            }

                            if (firstFrameLatencyMs >= 0L) {
                                Text(
                                    text = "زمن استجابة أول إطار: ${firstFrameLatencyMs}ms • الصيغة: ${activeStreamType.label}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        CinemaSettingsTab.AUDIO_SUBTITLES -> {
                            // 1. Audio Tracks
                            Text(
                                text = "المسارات الصوتية (Audio Tracks)",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            if (availableAudioTracks.isEmpty()) {
                                Text(
                                    text = "المسار الصوتي الافتراضي للمصدر نشط.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    availableAudioTracks.forEach { aOpt ->
                                        CinemaSelectablePill(
                                            label = aOpt.label,
                                            selected = aOpt.isSelected,
                                            onClick = { onSelectAudioTrack(aOpt) }
                                        )
                                    }
                                }
                            }

                            // 2. Subtitle Tracks & External Subtitle Loader
                            Text(
                                text = "الترجمة (Subtitles & External SRT/VTT)",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                CinemaSelectablePill(
                                    label = "إيقاف الترجمة",
                                    selected = !vodPrefs.subtitlesEnabled,
                                    onClick = onDisableSubtitles
                                )
                                availableSubtitleTracks.forEach { sOpt ->
                                    CinemaSelectablePill(
                                        label = sOpt.label,
                                        selected = vodPrefs.subtitlesEnabled && externalSubtitleName == null && sOpt.isSelected,
                                        onClick = { onSelectEmbeddedSubtitle(sOpt) }
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenExternalSubtitlePicker,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CyanGlow),
                                    modifier = Modifier.testTag("vod_load_external_subtitle_btn")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = externalSubtitleName?.let { "ملف: $it" } ?: "تحميل ملف ترجمة خارجي (.srt/.vtt)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 3. Subtitle Delay Sync (±0.25s steps)
                            val delaySecFormatted = String.format(Locale.US, "%+.2f ثانية", vodPrefs.subtitleDelayOffsetMs / 1000.0)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "مزامنة توقيت الترجمة: $delaySecFormatted",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CinemaSelectablePill(
                                        label = "-0.25s",
                                        selected = false,
                                        onClick = { VodPlayerPreferencesManager.adjustSubtitleDelayByStep(-1) }
                                    )
                                    CinemaSelectablePill(
                                        label = "إعادة ضبط 0.0s",
                                        selected = vodPrefs.subtitleDelayOffsetMs == 0L,
                                        onClick = { VodPlayerPreferencesManager.resetSubtitleDelay() }
                                    )
                                    CinemaSelectablePill(
                                        label = "+0.25s",
                                        selected = false,
                                        onClick = { VodPlayerPreferencesManager.adjustSubtitleDelayByStep(1) }
                                    )
                                }
                            }

                            // 4. Subtitle Styling (Font Size, Text Color, Background)
                            Text(
                                text = "حجم ولون وخلفية الترجمة",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                VodPlayerPreferencesManager.AVAILABLE_FONT_SIZES_SP.forEach { sizeSp ->
                                    CinemaSelectablePill(
                                        label = "${sizeSp.toInt()}sp",
                                        selected = abs(vodPrefs.subtitleFontSizeSp - sizeSp) < 0.5f,
                                        onClick = { VodPlayerPreferencesManager.updateSubtitleFontSize(sizeSp) }
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                SubtitleColorPreset.entries.forEach { preset ->
                                    CinemaSelectablePill(
                                        label = preset.arabicLabel,
                                        selected = vodPrefs.subtitleColorPreset == preset,
                                        onClick = { VodPlayerPreferencesManager.updateSubtitleColor(preset) }
                                    )
                                }
                                SubtitleBackgroundMode.entries.forEach { bgMode ->
                                    CinemaSelectablePill(
                                        label = bgMode.arabicLabel,
                                        selected = vodPrefs.subtitleBackgroundMode == bgMode,
                                        onClick = { VodPlayerPreferencesManager.updateSubtitleBackground(bgMode) }
                                    )
                                }
                            }
                        }

                        CinemaSettingsTab.SPEED_ASPECT -> {
                            Text(
                                text = "سرعة التشغيل (Playback Speed)",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                speeds.forEach { spd ->
                                    CinemaSelectablePill(
                                        label = if (spd == 1.0f) "1.0x (عادي)" else "${spd}x",
                                        selected = abs(playbackSpeed - spd) < 0.03f,
                                        onClick = { onSelectPlaybackSpeed(spd) }
                                    )
                                }
                            }

                            Text(
                                text = "أبعاد شاشة العرض (Aspect Ratio)",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                VodAspectMode.entries.forEach { mode ->
                                    CinemaSelectablePill(
                                        label = "${mode.arabicLabel} (${mode.englishLabel})",
                                        selected = aspectMode == mode,
                                        onClick = { onSelectAspectMode(mode) }
                                    )
                                }
                            }
                        }

                        CinemaSettingsTab.SEEK_PREFS -> {
                            Text(
                                text = "مقدار التقديم والتأخير السريع المزدوج (+${vodPrefs.forwardSeekSeconds}s / -${vodPrefs.rewindSeekSeconds}s)",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                VodPlayerPreferencesManager.AVAILABLE_SEEK_SECONDS.forEach { sec ->
                                    CinemaSelectablePill(
                                        label = "±$sec ثوانٍ (+$sec / -$sec)",
                                        selected = vodPrefs.forwardSeekSeconds == sec && vodPrefs.rewindSeekSeconds == sec,
                                        onClick = {
                                            VodPlayerPreferencesManager.updateUnifiedSeekStep(sec)
                                        }
                                    )
                                }
                            }

                            Text(
                                text = "التخزين المؤقت الذكي للأفلام والمسلسلات (VOD Cache)",
                                color = CyanGlow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                VodPlayerPreferencesManager.AVAILABLE_CACHE_SIZES_MB.forEach { mb ->
                                    CinemaSelectablePill(
                                        label = if (mb == 0) "إيقاف" else "$mb MB",
                                        selected = vodPrefs.vodCacheSizeMb == mb,
                                        onClick = { VodPlayerPreferencesManager.setVodCacheSizeMb(mb) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaSelectablePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) ElectricPurple else Color(0xFF17152B),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) CyanGlow else Color.White.copy(alpha = 0.15f)
        )
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private fun formatCinemaTime(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatLanguageNameAr(langCode: String, trackLabel: String?, index: Int): String {
    val cleanLabel = trackLabel?.trim().orEmpty()
    val lower = langCode.trim().lowercase()
    val langAr = when {
        lower.startsWith("ar") -> "العربية"
        lower.startsWith("en") -> "الإنجليزية"
        lower.startsWith("fr") -> "الفرنسية"
        lower.startsWith("es") -> "الإسبانية"
        lower.startsWith("tr") -> "التركية"
        lower.startsWith("de") -> "الألمانية"
        lower.startsWith("it") -> "الإيطالية"
        lower.startsWith("ru") -> "الروسية"
        lower.startsWith("hi") -> "الهندية"
        lower.startsWith("ko") -> "الكورية"
        lower.startsWith("ja") -> "اليابانية"
        lower.isNotEmpty() && lower != "und" -> langCode.uppercase()
        else -> "مسار $index"
    }
    return if (cleanLabel.isNotEmpty() && !cleanLabel.equals(langAr, ignoreCase = true)) {
        "$langAr • $cleanLabel"
    } else {
        langAr
    }
}

private fun canEnterPictureInPicture(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}

private fun enterCinemaPipMode(context: Context, videoWidth: Int, videoHeight: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = context as? Activity ?: return
    runCatching {
        val ratio = if (videoWidth > 0 && videoHeight > 0) {
            Rational(videoWidth.coerceIn(100, 2390), videoHeight.coerceIn(100, 1000))
        } else {
            Rational(16, 9)
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .build()
        activity.enterPictureInPictureMode(params)
    }
}
