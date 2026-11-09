package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.RepeatModeUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.ChannelEngagementStats
import com.example.data.model.PlaybackRequest
import com.example.data.model.PlayerGuideCategory
import com.example.data.model.PlayerGuideChannel
import com.example.ui.player.PlaybackRecoveryManager
import com.example.ui.player.PlayerDiagnosticEvent
import com.example.ui.player.PlayerDiagnostics
import com.example.ui.player.RecoveryDecision
import com.example.ui.player.StreamSourceResolver
import com.example.ui.player.UnifiedPlayerManager
import com.example.ui.player.WatchdogPlayerSnapshot
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG_CUSTOM_VOLUME_BTN = "exo_custom_volume_btn"
private const val TAG_CUSTOM_PIP_BTN = "exo_custom_pip_btn"
private const val TAG_CUSTOM_ASPECT_BTN = "exo_custom_aspect_btn"
private const val TAG_CUSTOM_MULTIVIEW_BTN = "exo_custom_multiview_btn"

@OptIn(UnstableApi::class)
@Composable
fun LibraryVideoPlayerScreen(
    playbackRequest: PlaybackRequest,
    channelStats: ChannelEngagementStats? = null,
    showChannelViewsAndLikes: Boolean = true,
    guideCategories: List<PlayerGuideCategory> = emptyList(),
    guideChannels: List<PlayerGuideChannel> = emptyList(),
    onSelectGuideChannel: (PlayerGuideChannel) -> Unit = {},
    onToggleChannelLike: () -> Unit = {},
    onSelectVodEpisode: ((com.example.data.model.SeriesEpisode, Int) -> Unit)? = null,
    onSelectVodRecommendedItem: ((com.example.data.model.LibraryMediaItem) -> Unit)? = null,
    onBack: () -> Unit
) {
    // Route Library Movies & Series (!isLiveChannel) to the dedicated Cinema VOD Player
    if (!playbackRequest.isLiveChannel) {
        CinemaVodPlayerScreen(
            playbackRequest = playbackRequest,
            onSelectEpisode = onSelectVodEpisode,
            onSelectRecommendedItem = onSelectVodRecommendedItem,
            onBack = onBack
        )
        return
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lock into Landscape mode, keep screen awake, and hide system bars (Immersive Fullscreen)
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else null
        insetsController?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalOrientation
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Unified Media3 ExoPlayer instance + Automatic Recovery Manager
    val playerManager = remember(context) { UnifiedPlayerManager(context) }
    val exoPlayer = playerManager.exoPlayer
    val recoveryManager = remember { PlaybackRecoveryManager(maxRetriesPerServer = 2) }
    val coroutineScope = rememberCoroutineScope()
    var scheduledRecoveryJob by remember { mutableStateOf<Job?>(null) }

    val candidateUrls = playbackRequest.candidateUrls
    val initialServerIndex = remember(playbackRequest, playerManager.deviceCapabilities) {
        StreamSourceResolver.selectOptimalInitialServerIndex(
            servers = playbackRequest.servers,
            isLiveChannel = playbackRequest.isLiveChannel,
            deviceCapabilities = playerManager.deviceCapabilities
        )
    }
    var currentUrlIndex by remember(playbackRequest) { mutableIntStateOf(initialServerIndex) }
    var reloadTrigger by remember(playbackRequest) { mutableIntStateOf(0) }
    var downgradeBitrateOnReload by remember(playbackRequest, currentUrlIndex) { mutableStateOf(false) }
    var allExtensionsFailed by remember(playbackRequest) { mutableStateOf(false) }
    var errorDescriptionAr by remember(playbackRequest) { mutableStateOf("تعذر تشغيل هذا المصدر حالياً") }

    var isMuted by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showServerSelectorDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (showServerSelectorDialog) {
            showServerSelectorDialog = false
        } else {
            onBack()
        }
    }

    val resolvedActiveStreamUrl = remember(candidateUrls, currentUrlIndex) {
        if (currentUrlIndex !in candidateUrls.indices) {
            ""
        } else {
            candidateUrls[currentUrlIndex].trim()
        }
    }
    val activeServerTypeHint = remember(playbackRequest.servers, currentUrlIndex) {
        playbackRequest.servers.getOrNull(currentUrlIndex)?.streamType.orEmpty()
    }
    val activeServerNameHint = remember(playbackRequest.servers, currentUrlIndex) {
        playbackRequest.servers.getOrNull(currentUrlIndex)?.name.orEmpty()
    }
    val activeSourceType = remember(
        resolvedActiveStreamUrl,
        activeServerTypeHint,
        activeServerNameHint,
        playbackRequest.isLiveChannel
    ) {
        StreamSourceResolver.detectStreamSourceType(
            url = resolvedActiveStreamUrl,
            serverStreamTypeHint = activeServerTypeHint,
            serverNameHint = activeServerNameHint,
            isLiveChannel = playbackRequest.isLiveChannel
        )
    }

    LaunchedEffect(playbackRequest) {
        scheduledRecoveryJob?.cancel()
        recoveryManager.onChannelOrManualServerSelected(
            initialServerIndex = initialServerIndex,
            totalServers = candidateUrls.size.coerceAtLeast(1)
        )
    }

    val selectServerManually: (Int) -> Unit = { targetIndex ->
        if (targetIndex in candidateUrls.indices) {
            scheduledRecoveryJob?.cancel()
            allExtensionsFailed = false
            downgradeBitrateOnReload = false
            recoveryManager.onChannelOrManualServerSelected(
                initialServerIndex = targetIndex,
                totalServers = candidateUrls.size.coerceAtLeast(1)
            )
            if (currentUrlIndex == targetIndex) {
                reloadTrigger += 1
            } else {
                currentUrlIndex = targetIndex
            }
        }
    }

    LaunchedEffect(resolvedActiveStreamUrl, reloadTrigger, allExtensionsFailed, playbackRequest) {
        if (playerManager.isReleased()) return@LaunchedEffect
        if (!allExtensionsFailed && resolvedActiveStreamUrl.isNotBlank()) {
            playerManager.applyQualityMode(
                isAutoQuality = true,
                isDataSaver = false
            )
            val normalizedUrl = if (resolvedActiveStreamUrl.startsWith("/")) {
                Uri.fromFile(File(resolvedActiveStreamUrl)).toString()
            } else {
                resolvedActiveStreamUrl
            }
            playerManager.loadStream(
                url = normalizedUrl,
                serverIndex = currentUrlIndex,
                serverStreamTypeHint = activeServerTypeHint,
                serverNameHint = activeServerNameHint,
                isLiveChannel = true,
                playImmediately = true,
                downgradeBitrateCap = downgradeBitrateOnReload,
                channelId = playbackRequest.channelId
            )
        } else if (candidateUrls.isEmpty()) {
            allExtensionsFailed = true
        }
    }

    val executeRecoveryDecision: (RecoveryDecision) -> Unit = { decision ->
        when (decision) {
            is RecoveryDecision.None -> Unit

            is RecoveryDecision.SeekToDefaultLiveEdge -> {
                scheduledRecoveryJob?.cancel()
                playerManager.seekToLiveEdgeNonDestructive()
            }

            is RecoveryDecision.ReloadCurrentServer -> {
                scheduledRecoveryJob?.cancel()
                scheduledRecoveryJob = coroutineScope.launch {
                    if (decision.delayMs > 0L) delay(decision.delayMs)
                    if (!playerManager.isReleased()) {
                        recoveryManager.onRecoveryDispatched()
                        downgradeBitrateOnReload = decision.downgradeBitrateHint
                        reloadTrigger += 1
                    }
                }
            }

            is RecoveryDecision.SwitchToNextServer -> {
                scheduledRecoveryJob?.cancel()
                scheduledRecoveryJob = coroutineScope.launch {
                    if (decision.delayMs > 0L) delay(decision.delayMs)
                    if (!playerManager.isReleased()) {
                        recoveryManager.onAutoServerSwitched(
                            newServerIndex = decision.nextServerIndex,
                            totalServers = candidateUrls.size.coerceAtLeast(1)
                        )
                        downgradeBitrateOnReload = false
                        currentUrlIndex = decision.nextServerIndex
                    }
                }
            }

            is RecoveryDecision.AllServersExhausted -> {
                scheduledRecoveryJob?.cancel()
                errorDescriptionAr = decision.userMessageAr
                allExtensionsFailed = true
            }
        }
    }

    val latestPlaybackRequest by rememberUpdatedState(playbackRequest)
    val latestCandidateUrls by rememberUpdatedState(candidateUrls)
    val latestCurrentUrlIndex by rememberUpdatedState(currentUrlIndex)
    val latestResolvedUrl by rememberUpdatedState(resolvedActiveStreamUrl)
    val latestSourceType by rememberUpdatedState(activeSourceType)
    val latestExecuteRecovery by rememberUpdatedState(executeRecoveryDecision)
    val latestGuideChannels by rememberUpdatedState(guideChannels)
    val latestOnSelectGuideChannel by rememberUpdatedState(onSelectGuideChannel)

    // Lifecycle handling (keep playing in Picture-in-Picture, pause when backgrounded)
    DisposableEffect(lifecycleOwner, playerManager) {
        var wasPlayingBeforePause = true
        val observer = LifecycleEventObserver { _, event ->
            if (playerManager.isReleased()) return@LifecycleEventObserver
            val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!inPip) {
                        wasPlayingBeforePause = exoPlayer.playWhenReady
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!inPip) {
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforePause) {
                        exoPlayer.playWhenReady = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(playerManager) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playerManager.isReleased()) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        PlayerDiagnostics.record(
                            event = PlayerDiagnosticEvent.BUFFERING_STARTED,
                            rawUrl = latestResolvedUrl,
                            serverIndex = latestCurrentUrlIndex,
                            sourceType = latestSourceType.label
                        )
                    }
                    Player.STATE_READY -> {
                        scheduledRecoveryJob?.cancel()
                        recoveryManager.onPlaybackReady(
                            currentUrl = latestResolvedUrl,
                            sourceType = latestSourceType
                        )
                        allExtensionsFailed = false
                        PlayerDiagnostics.record(
                            event = PlayerDiagnosticEvent.BUFFERING_ENDED,
                            rawUrl = latestResolvedUrl,
                            serverIndex = latestCurrentUrlIndex,
                            sourceType = latestSourceType.label
                        )
                        PlayerDiagnostics.record(
                            event = PlayerDiagnosticEvent.PLAYBACK_READY,
                            rawUrl = latestResolvedUrl,
                            serverIndex = latestCurrentUrlIndex,
                            sourceType = latestSourceType.label,
                            details = "video=${exoPlayer.videoSize.width}x${exoPlayer.videoSize.height}"
                        )
                    }
                    Player.STATE_ENDED -> {
                        val snapshot = WatchdogPlayerSnapshot(
                            playbackState = playbackState,
                            playWhenReady = exoPlayer.playWhenReady,
                            isPlaying = exoPlayer.isPlaying,
                            isLoading = exoPlayer.isLoading,
                            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
                            isLiveChannel = true,
                            currentServerIndex = latestCurrentUrlIndex,
                            totalServersCount = latestCandidateUrls.size.coerceAtLeast(1),
                            currentUrl = latestResolvedUrl,
                            sourceType = latestSourceType,
                            nowRealtimeMs = System.currentTimeMillis()
                        )
                        latestExecuteRecovery(recoveryManager.evaluateWatchdogTick(snapshot))
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (playerManager.isReleased()) return
                val decision = recoveryManager.onPlayerError(
                    error = error,
                    currentUrl = latestResolvedUrl,
                    sourceType = latestSourceType,
                    isLiveChannel = latestPlaybackRequest.isLiveChannel,
                    totalServers = latestCandidateUrls.size.coerceAtLeast(1)
                )
                latestExecuteRecovery(decision)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            scheduledRecoveryJob?.cancel()
            exoPlayer.removeListener(listener)
            playerManager.release()
        }
    }

    // Periodic Non-Destructive Watchdog
    LaunchedEffect(playerManager, playbackRequest, currentUrlIndex, allExtensionsFailed) {
        if (allExtensionsFailed || candidateUrls.isEmpty()) return@LaunchedEffect
        while (!playerManager.isReleased() && !allExtensionsFailed) {
            delay(1_000L)
            if (playerManager.isReleased() || allExtensionsFailed) break
            if (scheduledRecoveryJob?.isActive == true) continue

            val snapshot = WatchdogPlayerSnapshot(
                playbackState = exoPlayer.playbackState,
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                isLoading = exoPlayer.isLoading,
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
                isLiveChannel = true,
                currentServerIndex = currentUrlIndex,
                totalServersCount = candidateUrls.size.coerceAtLeast(1),
                currentUrl = resolvedActiveStreamUrl,
                sourceType = activeSourceType,
                nowRealtimeMs = System.currentTimeMillis()
            )
            val decision = recoveryManager.evaluateWatchdogTick(snapshot)
            if (decision !is RecoveryDecision.None) {
                executeRecoveryDecision(decision)
            }
        }
    }

    val switchRelativeChannel: (Int) -> Unit = { offset ->
        val list = latestGuideChannels
        if (list.size > 1) {
            val currentIdx = list.indexOfFirst { it.id == latestPlaybackRequest.channelId }
                .takeIf { it >= 0 } ?: 0
            val nextIdx = (currentIdx + offset + list.size) % list.size
            latestOnSelectGuideChannel(list[nextIdx])
        } else {
            exoPlayer.seekToDefaultPosition()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("media3_styled_player_root")
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("media3_styled_player_view"),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 4500
                    controllerHideOnTouch = true
                    this.resizeMode = resizeMode
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    setBackgroundColor(AndroidColor.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)

                    // Enable standard Media3 StyledPlayerView controls shown in screenshot
                    setShowShuffleButton(true)
                    setRepeatToggleModes(
                        RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE or RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL
                    )
                    setShowSubtitleButton(true)
                    setShowPreviousButton(true)
                    setShowNextButton(true)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)

                    setFullscreenButtonClickListener {
                        resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        this.resizeMode = resizeMode
                        activity?.window?.let { win ->
                            WindowCompat.getInsetsController(win, win.decorView)
                                .hide(WindowInsetsCompat.Type.systemBars())
                        }
                    }

                    configureStyledPlayerViewControls(
                        playerView = this,
                        isMutedProvider = { isMuted },
                        onToggleMute = { btn ->
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                            btn.setImageResource(
                                if (isMuted) R.drawable.ic_exo_volume_off else R.drawable.ic_exo_volume_on
                            )
                        },
                        onEnterPip = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                runCatching {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity.enterPictureInPictureMode(params)
                                }
                            }
                        },
                        onCycleAspectRatio = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            this.resizeMode = resizeMode
                            val modeLabel = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ملء الشاشة"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "تكبير"
                                else -> "الوضع الأصلي"
                            }
                            Toast.makeText(ctx, modeLabel, Toast.LENGTH_SHORT).show()
                        },
                        onMultiViewOrServerClick = {
                            if (latestPlaybackRequest.servers.size > 1 || latestGuideChannels.isNotEmpty()) {
                                showServerSelectorDialog = true
                            } else {
                                reloadTrigger += 1
                            }
                        },
                        onPreviousClicked = {
                            switchRelativeChannel(-1)
                        },
                        onNextClicked = {
                            switchRelativeChannel(1)
                        }
                    )
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                if (playerView.resizeMode != resizeMode) {
                    playerView.resizeMode = resizeMode
                }
                val volumeBtn = playerView.findViewWithTag<ImageButton>(TAG_CUSTOM_VOLUME_BTN)
                volumeBtn?.setImageResource(
                    if (isMuted) R.drawable.ic_exo_volume_off else R.drawable.ic_exo_volume_on
                )
            }
        )

        if (allExtensionsFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = errorDescriptionAr,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                allExtensionsFailed = false
                                recoveryManager.onChannelOrManualServerSelected(
                                    initialServerIndex = currentUrlIndex,
                                    totalServers = candidateUrls.size.coerceAtLeast(1)
                                )
                                reloadTrigger += 1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("live_player_retry_button")
                        ) {
                            Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                        }
                        if (playbackRequest.servers.size > 1) {
                            Button(
                                onClick = { showServerSelectorDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF222222),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("تغيير السيرفر", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        TextButton(onClick = onBack) {
                            Text("رجوع", color = Color.White)
                        }
                    }
                }
            }
        }

        if (showServerSelectorDialog) {
            Dialog(onDismissRequest = { showServerSelectorDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141414),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 380.dp)
                        .heightIn(max = 360.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (playbackRequest.servers.size > 1) "السيرفرات المتاحة" else "القنوات",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (playbackRequest.servers.isNotEmpty()) {
                                itemsIndexed(playbackRequest.servers) { index, server ->
                                    val isSelected = index == currentUrlIndex
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color.White else Color(0xFF232323),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showServerSelectorDialog = false
                                                selectServerManually(index)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = cleanServerDisplayName(server.name, index),
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (isSelected) {
                                                Text(
                                                    text = "متصل",
                                                    color = Color.Black,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showServerSelectorDialog = false }) {
                                Text("إغلاق", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Configures the internal AndroidX Media3 [PlayerView] (`StyledPlayerView`) controls
 * to match the exact visual layout and icon order requested:
 * - Center controls: Previous (`|<<`), Rewind (`↺`), White Circle Play/Pause, Fast-Forward (`↻`), Next (`>>|`)
 * - Full-width thin `DefaultTimeBar` above the bottom bar
 * - Bottom bar left: `00:00 • 00:00` (`••:•• • ••:••` on live streams)
 * - Bottom bar right: `[Shuffle] [Repeat] [Volume] [Settings] [PiP] [CC] [Aspect Ratio <->] [Multi-view] [Fullscreen]`
 */
@OptIn(UnstableApi::class)
private fun configureStyledPlayerViewControls(
    playerView: PlayerView,
    isMutedProvider: () -> Boolean,
    onToggleMute: (ImageButton) -> Unit,
    onEnterPip: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onMultiViewOrServerClick: () -> Unit,
    onPreviousClicked: () -> Unit,
    onNextClicked: () -> Unit
) {
    val context = playerView.context

    // 1. Style the full-width thin DefaultTimeBar
    playerView.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
        setPlayedColor(AndroidColor.WHITE)
        setScrubberColor(AndroidColor.WHITE)
        setBufferedColor(0x66FFFFFF)
        setUnplayedColor(0x33FFFFFF)
    }

    // 2. Style the Center Transport Controls (Prev, Rewind, White Circle Play/Pause, FastForward, Next)
    val playPauseBtn = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
    playPauseBtn?.apply {
        background = ContextCompat.getDrawable(context, R.drawable.bg_exo_play_pause_circle)
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(AndroidColor.BLACK))
        scaleType = ImageView.ScaleType.FIT_CENTER
        val sizePx = dpToPx(context, 54)
        val padPx = dpToPx(context, 13)
        setPadding(padPx, padPx, padPx, padPx)
        layoutParams = (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            width = sizePx
            height = sizePx
            marginStart = dpToPx(context, 18)
            marginEnd = dpToPx(context, 18)
        } ?: LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER
            marginStart = dpToPx(context, 18)
            marginEnd = dpToPx(context, 18)
        }
    }

    // Style Rewind & FastForward as clean circular arrows without numbers inside
    val rewBtn = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_rew_with_amount)
        ?: playerView.findViewById(androidx.media3.ui.R.id.exo_rew)
    rewBtn?.apply {
        if (this is TextView) {
            text = ""
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 0f)
            setTextColor(AndroidColor.TRANSPARENT)
            setCompoundDrawables(null, null, null, null)
        }
        background = ContextCompat.getDrawable(context, R.drawable.ic_exo_rewind_clean)
        val sizePx = dpToPx(context, 34)
        layoutParams = (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            width = sizePx
            height = sizePx
            marginStart = dpToPx(context, 14)
            marginEnd = dpToPx(context, 14)
        } ?: LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER
        }
    }

    val ffwdBtn = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_ffwd_with_amount)
        ?: playerView.findViewById(androidx.media3.ui.R.id.exo_ffwd)
    ffwdBtn?.apply {
        if (this is TextView) {
            text = ""
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 0f)
            setTextColor(AndroidColor.TRANSPARENT)
            setCompoundDrawables(null, null, null, null)
        }
        background = ContextCompat.getDrawable(context, R.drawable.ic_exo_fastforward_clean)
        val sizePx = dpToPx(context, 34)
        layoutParams = (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            width = sizePx
            height = sizePx
            marginStart = dpToPx(context, 14)
            marginEnd = dpToPx(context, 14)
        } ?: LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER
        }
    }

    val prevBtn = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_prev)
    prevBtn?.apply {
        visibility = View.VISIBLE
        isEnabled = true
        alpha = 1.0f
        setOnClickListener { onPreviousClicked() }
    }

    val nextBtn = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_next)
    nextBtn?.apply {
        visibility = View.VISIBLE
        setOnClickListener { onNextClicked() }
    }

    // 3. Configure Bottom Bar Right Icons in exact screenshot order:
    // [Shuffle] [Repeat] [Volume] [Settings] [PiP] [CC] [Aspect Ratio <->] [Multi-view] [Fullscreen]
    val basicControls = playerView.findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)
        ?: return

    val shuffleBtn = basicControls.findViewById<View>(androidx.media3.ui.R.id.exo_shuffle)
    val repeatBtn = basicControls.findViewById<View>(androidx.media3.ui.R.id.exo_repeat_toggle)
    val settingsBtn = basicControls.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
    val subtitleBtn = basicControls.findViewById<View>(androidx.media3.ui.R.id.exo_subtitle)
    val fullscreenBtn = basicControls.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)
    val vrBtn = basicControls.findViewById<View>(androidx.media3.ui.R.id.exo_vr)
    val overflowShowBtn = basicControls.findViewById<View>(androidx.media3.ui.R.id.exo_overflow_show)

    vrBtn?.visibility = View.GONE
    overflowShowBtn?.visibility = View.GONE

    val volumeBtn = createBottomBarActionButton(
        context = context,
        tagName = TAG_CUSTOM_VOLUME_BTN,
        drawableRes = if (isMutedProvider()) R.drawable.ic_exo_volume_off else R.drawable.ic_exo_volume_on,
        contentDescription = "الصوت"
    ).apply {
        setOnClickListener { onToggleMute(this) }
    }

    val pipBtn = createBottomBarActionButton(
        context = context,
        tagName = TAG_CUSTOM_PIP_BTN,
        drawableRes = R.drawable.ic_exo_pip,
        contentDescription = "نافذة عائمة"
    ).apply {
        setOnClickListener { onEnterPip() }
    }

    val aspectBtn = createBottomBarActionButton(
        context = context,
        tagName = TAG_CUSTOM_ASPECT_BTN,
        drawableRes = R.drawable.ic_exo_aspect_ratio,
        contentDescription = "أبعاد الشاشة"
    ).apply {
        setOnClickListener { onCycleAspectRatio() }
    }

    val multiviewBtn = createBottomBarActionButton(
        context = context,
        tagName = TAG_CUSTOM_MULTIVIEW_BTN,
        drawableRes = R.drawable.ic_exo_multiview,
        contentDescription = "تبديل السيرفر"
    ).apply {
        setOnClickListener { onMultiViewOrServerClick() }
    }

    val orderedButtons = listOfNotNull(
        shuffleBtn,
        repeatBtn,
        volumeBtn,
        settingsBtn,
        pipBtn,
        subtitleBtn,
        aspectBtn,
        multiviewBtn,
        fullscreenBtn
    )

    val btnWidthPx = dpToPx(context, 42)
    val btnHeightPx = dpToPx(context, 40)
    val btnPadPx = dpToPx(context, 9)

    val enforceBottomBarOrder = {
        overflowShowBtn?.visibility = View.GONE
        vrBtn?.visibility = View.GONE

        orderedButtons.forEachIndexed { targetIndex, view ->
            val parentGroup = view.parent as? ViewGroup
            if (parentGroup != null && parentGroup !== basicControls) {
                parentGroup.removeView(view)
            }
            if (view.parent == null) {
                basicControls.addView(
                    view,
                    targetIndex.coerceAtMost(basicControls.childCount),
                    LinearLayout.LayoutParams(btnWidthPx, btnHeightPx).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                )
            } else {
                val currentIdx = basicControls.indexOfChild(view)
                if (currentIdx != targetIndex && currentIdx >= 0) {
                    basicControls.removeView(view)
                    basicControls.addView(
                        view,
                        targetIndex.coerceAtMost(basicControls.childCount),
                        LinearLayout.LayoutParams(btnWidthPx, btnHeightPx).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    )
                }
            }

            view.visibility = View.VISIBLE
            view.setPadding(btnPadPx, btnPadPx, btnPadPx, btnPadPx)
            view.layoutParams = (view.layoutParams as? LinearLayout.LayoutParams)?.apply {
                width = btnWidthPx
                height = btnHeightPx
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 0
                marginEnd = 0
            } ?: LinearLayout.LayoutParams(btnWidthPx, btnHeightPx).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }

        // Keep center Prev/PlayPause buttons styled consistently across player state updates
        prevBtn?.apply {
            visibility = View.VISIBLE
            isEnabled = true
            alpha = 1.0f
        }
        rewBtn?.apply {
            if (this is TextView && text.isNotEmpty()) {
                text = ""
            }
        }
        ffwdBtn?.apply {
            if (this is TextView && text.isNotEmpty()) {
                text = ""
            }
        }
    }

    enforceBottomBarOrder()

    val bottomBar = playerView.findViewById<FrameLayout>(androidx.media3.ui.R.id.exo_bottom_bar)
    bottomBar?.setBackgroundColor(0x99000000.toInt())
    bottomBar?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        bottomBar.post { enforceBottomBarOrder() }
    }
}

private fun createBottomBarActionButton(
    context: Context,
    tagName: String,
    drawableRes: Int,
    contentDescription: String
): ImageButton {
    val outValue = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
    return ImageButton(context).apply {
        tag = tagName
        setImageResource(drawableRes)
        this.contentDescription = contentDescription
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundResource(outValue.resourceId)
        alpha = 1.0f
    }
}

private fun dpToPx(context: Context, dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}

private fun cleanServerDisplayName(rawName: String, fallbackIndex: Int): String {
    val cleaned = rawName
        .replace(Regex("\\b(TS|M3U8|HLS|MPEGTS)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[•|()\\-]+\\s*$"), "")
        .replace(Regex("^\\s*[•|()\\-]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return cleaned.ifBlank { "سيرفر ${fallbackIndex + 1}" }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
