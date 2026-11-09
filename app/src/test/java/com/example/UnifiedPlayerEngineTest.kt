package com.example

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.example.data.model.ChannelServer
import com.example.data.model.SeriesEpisode
import com.example.data.model.SeriesSeason
import com.example.ui.player.CinemaSubtitleParser
import com.example.ui.player.DeviceCodecCapabilities
import com.example.ui.player.OkHttpStreamDataSource
import com.example.ui.player.PlaybackRecoveryManager
import com.example.ui.player.PlayerDiagnosticEvent
import com.example.ui.player.PlayerDiagnostics
import com.example.ui.player.PlayerSessionLifecycleController
import com.example.ui.player.RecoveryDecision
import com.example.ui.player.SeriesEpisodeNavigator
import com.example.ui.player.StreamFailureCategory
import com.example.ui.player.StreamOkHttpClientProvider
import com.example.ui.player.StreamSourceResolver
import com.example.ui.player.StreamSourceType
import com.example.ui.player.UnifiedPlayerManager
import com.example.ui.player.VodPlayerPreferencesManager
import com.example.ui.player.WatchProgressManager
import com.example.ui.player.WatchdogPlayerSnapshot
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.EOFException
import java.lang.reflect.Proxy

class UnifiedPlayerEngineTest {

    @Before
    fun setUp() {
        PlayerDiagnostics.clearHistory()
        PlayerDiagnostics.isVerboseLoggingEnabled = false
        StreamSourceResolver.clearObservedContentTypes()
        WatchProgressManager.clearAllForTesting()
    }

    @Test
    fun `Section 1 - StreamOkHttpClientProvider configures 20s readTimeout HTTP2 and OkHttpStreamDataSource omits forbidden Connection headers`() {
        assertEquals(10_000L, StreamOkHttpClientProvider.CONNECT_TIMEOUT_MS)
        assertEquals(20_000L, StreamOkHttpClientProvider.READ_TIMEOUT_MS)
        assertEquals(15_000L, StreamOkHttpClientProvider.PING_INTERVAL_MS)

        val sharedClient = StreamOkHttpClientProvider.sharedClient
        assertEquals(10_000, sharedClient.connectTimeoutMillis)
        assertEquals(20_000, sharedClient.readTimeoutMillis)
        assertEquals(15_000, sharedClient.pingIntervalMillis)
        assertTrue(sharedClient.protocols.contains(Protocol.HTTP_2))
        assertTrue(sharedClient.protocols.contains(Protocol.HTTP_1_1))

        var capturedRequest: Request? = null
        var callCancelled = false
        val payloadBytes = "0123456789ABCDEF".toByteArray(Charsets.UTF_8)

        val fakeCallFactory = Call.Factory { request ->
            capturedRequest = request
            object : Call {
                override fun request(): Request = request
                override fun execute(): Response {
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_2)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8")
                        .header("Content-Length", payloadBytes.size.toString())
                        .body(payloadBytes.toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                        .build()
                }
                override fun enqueue(responseCallback: Callback) = Unit
                override fun cancel() {
                    callCancelled = true
                }
                override fun isExecuted(): Boolean = true
                override fun isCanceled(): Boolean = callCancelled
                override fun timeout(): Timeout = Timeout.NONE
                override fun clone(): Call = this
            }
        }

        val dataSource = OkHttpStreamDataSource(
            callFactory = fakeCallFactory,
            userAgent = "FoulTV-Test/1.2.1",
            defaultHeaders = mapOf(
                "Connection" to "keep-alive",
                "Keep-Alive" to "timeout=5",
                "Upgrade" to "h2c",
                "X-Custom-Token" to "ok"
            )
        )
        dataSource.setRequestProperty("connection", "keep-alive")

        val testUrl = "https://cdn.example.com/live/stream_no_ext_101"
        val bytesToRead = dataSource.openUrl(
            requestUrlString = testUrl,
            position = 4L,
            length = C.LENGTH_UNSET.toLong()
        )
        assertEquals(12L, bytesToRead)

        val req = capturedRequest!!
        assertNull("Forbidden Connection header must not be sent on OkHttp requests", req.header("Connection"))
        assertNull("Forbidden Keep-Alive header must not be sent", req.header("Keep-Alive"))
        assertNull("Forbidden Upgrade header must not be sent", req.header("Upgrade"))
        assertEquals("ok", req.header("X-Custom-Token"))
        assertEquals("bytes=4-", req.header("Range"))

        val readBuffer = ByteArray(12)
        val readCount = dataSource.read(readBuffer, 0, readBuffer.size)
        assertEquals(12, readCount)
        assertArrayEquals("456789ABCDEF".toByteArray(Charsets.UTF_8), readBuffer)
        assertEquals(C.RESULT_END_OF_INPUT, dataSource.read(readBuffer, 0, readBuffer.size))

        assertEquals(
            StreamSourceType.HLS,
            StreamSourceResolver.getObservedSourceType(testUrl)
        )

        dataSource.close()
        assertTrue("close() must cancel the underlying OkHttp Call", callCancelled)
    }

    @Test
    fun `Section 1 - OkHttpStreamDataSource handles natural EOF on 206 Partial Content shorter than DataSpec length without throwing EOFException`() {
        // File is 100 bytes total (bytes 0..99). Caller requests 50 bytes starting at offset 90 (Range: bytes=90-139).
        // Server legitimately responds 206 Partial Content with bytes 90-99/100 (10 bytes).
        val tailBytes = "0123456789".toByteArray(Charsets.UTF_8)
        var capturedRangeHeader: String? = null

        val fakeCallFactory = Call.Factory { request ->
            capturedRangeHeader = request.header("Range")
            object : Call {
                override fun request(): Request = request
                override fun execute(): Response {
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_2)
                        .code(206)
                        .message("Partial Content")
                        .header("Content-Range", "bytes 90-99/100")
                        .header("Content-Length", "10")
                        .body(tailBytes.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }
                override fun enqueue(responseCallback: Callback) = Unit
                override fun cancel() = Unit
                override fun isExecuted(): Boolean = true
                override fun isCanceled(): Boolean = false
                override fun timeout(): Timeout = Timeout.NONE
                override fun clone(): Call = this
            }
        }

        val dataSource = OkHttpStreamDataSource(
            callFactory = fakeCallFactory,
            userAgent = "FoulTV-Test/1.2.1"
        )

        val expectedBytes = dataSource.openUrl(
            requestUrlString = "https://vod.example.com/movie/tail_probe.mp4",
            position = 90L,
            length = 50L
        )
        assertEquals("bytes=90-139", capturedRangeHeader)
        // Must clamp bytesToRead to 10 (natural EOF from Content-Range: bytes 90-99/100), NOT 50!
        assertEquals(10L, expectedBytes)

        val buffer = ByteArray(50)
        val firstRead = dataSource.read(buffer, 0, buffer.size)
        assertEquals(10, firstRead)
        assertArrayEquals(tailBytes, buffer.copyOfRange(0, 10))

        // Next read must return RESULT_END_OF_INPUT cleanly without throwing EOFException!
        val eofRead = dataSource.read(buffer, 0, buffer.size)
        assertEquals(C.RESULT_END_OF_INPUT, eofRead)
        dataSource.close()
    }

    @Test
    fun `Section 1 - OkHttpStreamDataSource throws EOFException when stream data is genuinely truncated mid-transfer`() {
        // Server promises 20 bytes via Content-Range: bytes 0-19/100 and Content-Length: 20,
        // but the network stream breaks after delivering only 5 bytes ("SHORT").
        val truncatedBody = "SHORT".toByteArray(Charsets.UTF_8)
        val fakeCallFactory = Call.Factory { request ->
            object : Call {
                override fun request(): Request = request
                override fun execute(): Response {
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(206)
                        .message("Partial Content")
                        .header("Content-Range", "bytes 0-19/100")
                        .header("Content-Length", "20")
                        .body(truncatedBody.toResponseBody("video/mp2t".toMediaType()))
                        .build()
                }
                override fun enqueue(responseCallback: Callback) = Unit
                override fun cancel() = Unit
                override fun isExecuted(): Boolean = true
                override fun isCanceled(): Boolean = false
                override fun timeout(): Timeout = Timeout.NONE
                override fun clone(): Call = this
            }
        }

        val dataSource = OkHttpStreamDataSource(
            callFactory = fakeCallFactory,
            userAgent = "FoulTV-Test/1.2.1"
        )
        val bytesToRead = dataSource.openUrl(
            requestUrlString = "http://iptv.example.com:8080/live/u/p/101.ts",
            position = 0L,
            length = 20L
        )
        assertEquals(20L, bytesToRead)

        val buffer = ByteArray(10)
        assertEquals(5, dataSource.read(buffer, 0, buffer.size))
        try {
            dataSource.read(buffer, 0, buffer.size)
            fail("Expected RuntimeException wrapping EOFException on premature stream termination")
        } catch (e: RuntimeException) {
            assertTrue("Cause must be EOFException", e.cause is EOFException)
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `Section 2 - Watchdog aligns with 20s READ_TIMEOUT and distinguishes initial connection first-byte wait slow buffering and mid-stream stall`() {
        val recovery = PlaybackRecoveryManager(maxRetriesPerServer = 2)
        recovery.onChannelOrManualServerSelected(initialServerIndex = 0, totalServers = 2, nowRealtimeMs = 1_000L)

        // 1. Start initial buffering at t=1_000ms (isLoading = true, zero bytes received yet)
        assertEquals(
            RecoveryDecision.None,
            recovery.evaluateWatchdogTick(
                WatchdogPlayerSnapshot(
                    playbackState = Player.STATE_BUFFERING,
                    playWhenReady = true,
                    isPlaying = false,
                    isLoading = true,
                    currentPositionMs = 0L,
                    bufferedPositionMs = 0L,
                    isLiveChannel = true,
                    currentServerIndex = 0,
                    totalServersCount = 2,
                    currentUrl = "http://host.com/live/user/pass/55.m3u8",
                    sourceType = StreamSourceType.HLS,
                    nowRealtimeMs = 1_000L
                )
            )
        )

        // 2. At t=12_000ms (11s elapsed) and t=19_000ms (18s elapsed) with zero bytes but isLoading=true:
        // Watchdog MUST NOT cancel the connection because OkHttp READ_TIMEOUT (20s) is still actively waiting for first byte!
        assertEquals(
            "Must not cancel active initial connection at 11s while OkHttp is loading",
            RecoveryDecision.None,
            recovery.evaluateWatchdogTick(
                WatchdogPlayerSnapshot(
                    playbackState = Player.STATE_BUFFERING,
                    playWhenReady = true,
                    isPlaying = false,
                    isLoading = true,
                    currentPositionMs = 0L,
                    bufferedPositionMs = 0L,
                    isLiveChannel = true,
                    currentServerIndex = 0,
                    totalServersCount = 2,
                    currentUrl = "http://host.com/live/user/pass/55.m3u8",
                    sourceType = StreamSourceType.HLS,
                    nowRealtimeMs = 12_000L
                )
            )
        )
        assertEquals(
            "Must not cancel active initial connection at 18s (within 20s READ_TIMEOUT)",
            RecoveryDecision.None,
            recovery.evaluateWatchdogTick(
                WatchdogPlayerSnapshot(
                    playbackState = Player.STATE_BUFFERING,
                    playWhenReady = true,
                    isPlaying = false,
                    isLoading = true,
                    currentPositionMs = 0L,
                    bufferedPositionMs = 0L,
                    isLiveChannel = true,
                    currentServerIndex = 0,
                    totalServersCount = 2,
                    currentUrl = "http://host.com/live/user/pass/55.m3u8",
                    sourceType = StreamSourceType.HLS,
                    nowRealtimeMs = 19_000L
                )
            )
        )

        // 3. First bytes arrive at t=19_500ms (bufferedPositionMs = 600ms), and continue trickling at t=23_000ms (1_100ms):
        // Even though total buffering is now 22s, bytes arrived 3.5s ago -> MUST NOT cancel active slow buffering!
        assertEquals(
            RecoveryDecision.None,
            recovery.evaluateWatchdogTick(
                WatchdogPlayerSnapshot(
                    playbackState = Player.STATE_BUFFERING,
                    playWhenReady = true,
                    isPlaying = false,
                    isLoading = true,
                    currentPositionMs = 0L,
                    bufferedPositionMs = 600L,
                    isLiveChannel = true,
                    currentServerIndex = 0,
                    totalServersCount = 2,
                    currentUrl = "http://host.com/live/user/pass/55.m3u8",
                    sourceType = StreamSourceType.HLS,
                    nowRealtimeMs = 19_500L
                )
            )
        )
        assertEquals(
            "Slow network actively receiving bytes must not be interrupted at 22s",
            RecoveryDecision.None,
            recovery.evaluateWatchdogTick(
                WatchdogPlayerSnapshot(
                    playbackState = Player.STATE_BUFFERING,
                    playWhenReady = true,
                    isPlaying = false,
                    isLoading = true,
                    currentPositionMs = 0L,
                    bufferedPositionMs = 1_100L,
                    isLiveChannel = true,
                    currentServerIndex = 0,
                    totalServersCount = 2,
                    currentUrl = "http://host.com/live/user/pass/55.m3u8",
                    sourceType = StreamSourceType.HLS,
                    nowRealtimeMs = 23_000L
                )
            )
        )

        // 4. Playback starts and advances normally at t=24_000ms (STATE_READY, pos=2_000ms)
        recovery.onPlaybackReady(nowRealtimeMs = 24_000L, currentUrl = "http://host.com/live/user/pass/55.m3u8")
        assertEquals(
            RecoveryDecision.None,
            recovery.evaluateWatchdogTick(
                WatchdogPlayerSnapshot(
                    playbackState = Player.STATE_READY,
                    playWhenReady = true,
                    isPlaying = true,
                    isLoading = true,
                    currentPositionMs = 2_000L,
                    bufferedPositionMs = 8_000L,
                    isLiveChannel = true,
                    currentServerIndex = 0,
                    totalServersCount = 2,
                    currentUrl = "http://host.com/live/user/pass/55.m3u8",
                    sourceType = StreamSourceType.HLS,
                    nowRealtimeMs = 24_000L
                )
            )
        )

        // 5. Stream that was playing freezes in STATE_READY for >= 9_000ms (t=33_500ms, pos still 2_000ms):
        // Step 1 performs non-destructive SeekToDefaultLiveEdge first!
        val readyFreezeDecision = recovery.evaluateWatchdogTick(
            WatchdogPlayerSnapshot(
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = false,
                isLoading = false,
                currentPositionMs = 2_000L,
                bufferedPositionMs = 2_000L,
                isLiveChannel = true,
                currentServerIndex = 0,
                totalServersCount = 2,
                currentUrl = "http://host.com/live/user/pass/55.m3u8",
                sourceType = StreamSourceType.HLS,
                nowRealtimeMs = 33_500L
            )
        )
        assertTrue(readyFreezeDecision is RecoveryDecision.SeekToDefaultLiveEdge)
    }

    @Test
    fun `Section 2 - PlaybackRecoveryManager prevents concurrent or duplicate recovery triggers while a recovery is in flight`() {
        val recovery = PlaybackRecoveryManager(maxRetriesPerServer = 2)
        recovery.onChannelOrManualServerSelected(initialServerIndex = 0, totalServers = 2, nowRealtimeMs = 1_000L)

        val firstDecision = recovery.onPlayerErrorDetails(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
            message = "Socket timeout",
            currentUrl = "http://host.com/live/user/pass/55.m3u8",
            sourceType = StreamSourceType.HLS,
            isLiveChannel = true,
            totalServers = 2,
            nowRealtimeMs = 1_500L
        )
        assertTrue(firstDecision is RecoveryDecision.ReloadCurrentServer)
        assertEquals(1, (firstDecision as RecoveryDecision.ReloadCurrentServer).attemptNumber)
        assertTrue(recovery.isRecoveryLocked(2_000L))

        // Duplicate error callback or watchdog tick while recovery is counting down MUST return RecoveryDecision.None
        assertEquals(
            RecoveryDecision.None,
            recovery.onPlayerErrorDetails(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
                message = "Duplicate socket timeout callback",
                currentUrl = "http://host.com/live/user/pass/55.m3u8",
                sourceType = StreamSourceType.HLS,
                isLiveChannel = true,
                totalServers = 2,
                nowRealtimeMs = 2_000L
            )
        )

        // Dispatch the scheduled reload at t=3_000ms -> post-dispatch cooldown (500ms) also blocks stale callbacks at t=3_200ms
        recovery.onRecoveryDispatched(3_000L)
        assertTrue("Post-dispatch cooldown must lock out immediate stale callbacks", recovery.isRecoveryLocked(3_200L))
        assertEquals(
            RecoveryDecision.None,
            recovery.onPlayerErrorDetails(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
                message = "Stale callback from aborted source",
                currentUrl = "http://host.com/live/user/pass/55.m3u8",
                sourceType = StreamSourceType.HLS,
                isLiveChannel = true,
                totalServers = 2,
                nowRealtimeMs = 3_200L
            )
        )

        // Genuine failure on the new load at t=5_000ms escalates cleanly to Attempt #2
        val secondDecision = recovery.onPlayerErrorDetails(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
            message = "Second timeout after reload",
            currentUrl = "http://host.com/live/user/pass/55.m3u8",
            sourceType = StreamSourceType.HLS,
            isLiveChannel = true,
            totalServers = 2,
            nowRealtimeMs = 5_000L
        )
        assertTrue(secondDecision is RecoveryDecision.ReloadCurrentServer)
        assertEquals(2, (secondDecision as RecoveryDecision.ReloadCurrentServer).attemptNumber)
    }

    @Test
    fun `Section 3 - evaluate4kDecoderSupport verifies actual 3840x2160 dimensions and frame rate rather than height upper bound alone`() {
        // 1. Decoder with supportedHeightUpper = 2160 but supportedWidthUpper = 1920 (cannot decode 3840x2160) -> FALSE
        val (narrowWidth4k, narrowFps) = StreamSourceResolver.evaluate4kDecoderSupport(
            isHardwareAccelerated = true,
            isLowRamDevice = false,
            supportedWidthUpper = 1920,
            supportedHeightUpper = 2160,
            isSize3840x2160Supported = false,
            is3840x2160At24FpsSupported = false,
            is3840x2160At30FpsSupported = false
        )
        assertFalse("supportedHeightUpper >= 2160 alone must NOT pass when 3840 width is unsupported", narrowWidth4k)
        assertEquals(0.0, narrowFps, 0.01)

        // 2. Decoder where 3840x2160 exceeds macroblock limit (isSize3840x2160Supported = false) -> FALSE
        val (macroblockExceeded, _) = StreamSourceResolver.evaluate4kDecoderSupport(
            isHardwareAccelerated = true,
            isLowRamDevice = false,
            supportedWidthUpper = 3840,
            supportedHeightUpper = 2160,
            isSize3840x2160Supported = false,
            is3840x2160At24FpsSupported = false,
            is3840x2160At30FpsSupported = false
        )
        assertFalse(macroblockExceeded)

        // 3. Decoder supporting 3840x2160 only at 15fps (fails 24fps and 30fps check) -> FALSE
        val (lowFps4k, _) = StreamSourceResolver.evaluate4kDecoderSupport(
            isHardwareAccelerated = true,
            isLowRamDevice = false,
            supportedWidthUpper = 3840,
            supportedHeightUpper = 2160,
            isSize3840x2160Supported = true,
            is3840x2160At24FpsSupported = false,
            is3840x2160At30FpsSupported = false
        )
        assertFalse("Decoder that cannot sustain at least 24fps at 3840x2160 must not be marked 4K-capable", lowFps4k)

        // 4. Software decoder -> FALSE even if dimensions match
        val (software4k, _) = StreamSourceResolver.evaluate4kDecoderSupport(
            isHardwareAccelerated = false,
            isLowRamDevice = false,
            supportedWidthUpper = 3840,
            supportedHeightUpper = 2160,
            isSize3840x2160Supported = true,
            is3840x2160At24FpsSupported = true,
            is3840x2160At30FpsSupported = true
        )
        assertFalse(software4k)

        // 5. Genuine hardware 4K decoder supporting 3840x2160 @ 60fps -> TRUE (60.0 fps) and no 1080p ceiling!
        val (true4k, maxFps) = StreamSourceResolver.evaluate4kDecoderSupport(
            isHardwareAccelerated = true,
            isLowRamDevice = false,
            supportedWidthUpper = 3840,
            supportedHeightUpper = 2160,
            isSize3840x2160Supported = true,
            is3840x2160At24FpsSupported = true,
            is3840x2160At30FpsSupported = true,
            is3840x2160At60FpsSupported = true
        )
        assertTrue(true4k)
        assertEquals(60.0, maxFps, 0.01)

        val controller4k = PlayerSessionLifecycleController(
            DeviceCodecCapabilities(
                supportsHardwareH264 = true,
                supportsHardwareHevc = true,
                supportsHardware4k = true4k,
                isLowRamDevice = false,
                maxSupportedFrameRate4k = maxFps
            )
        )
        assertEquals(
            Triple(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
            controller4k.resolveAutoQualityMaxVideoSize(isDataSaver = false, downgradeBitrateCap = false)
        )
    }

    @Test
    fun `Section 4 - UnifiedPlayerManager reuses the exact same ExoPlayer instance across Home and IPTV channels and server switches and invokes zero methods on ExoPlayer after release`() {
        val exoPlayerInvocations = mutableListOf<String>()
        var currentTrackParams = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        var currentPlaybackParams = PlaybackParameters.DEFAULT
        var playWhenReadyState = true

        val dummyMediaSource = Proxy.newProxyInstance(
            MediaSource::class.java.classLoader,
            arrayOf(MediaSource::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "DummyMediaSource"
                "hashCode" -> 1
                "equals" -> false
                else -> null
            }
        } as MediaSource

        val recordedExoPlayer = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java)
        ) { proxy, method, args ->
            val name = method.name
            if (name != "toString" && name != "hashCode" && name != "equals") {
                exoPlayerInvocations.add(name)
            }
            when (name) {
                "getTrackSelectionParameters" -> currentTrackParams
                "setTrackSelectionParameters" -> {
                    currentTrackParams = args?.getOrNull(0) as? TrackSelectionParameters ?: currentTrackParams
                    Unit
                }
                "getPlaybackParameters" -> currentPlaybackParams
                "setPlaybackParameters" -> {
                    currentPlaybackParams = args?.getOrNull(0) as? PlaybackParameters ?: currentPlaybackParams
                    Unit
                }
                "getPlayWhenReady" -> playWhenReadyState
                "setPlayWhenReady" -> {
                    playWhenReadyState = args?.getOrNull(0) as? Boolean ?: playWhenReadyState
                    Unit
                }
                "isCurrentMediaItemLive" -> true
                "getPlaybackState" -> Player.STATE_READY
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RecordedExoPlayerProxy"
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }
        } as ExoPlayer

        val manager = UnifiedPlayerManager(
            context = null,
            customCapabilities = DeviceCodecCapabilities(
                supportsHardwareH264 = true,
                supportsHardwareHevc = true,
                supportsHardware4k = true,
                isLowRamDevice = false
            ),
            customExoPlayer = recordedExoPlayer,
            customMediaSourceProvider = { _, _, _ -> dummyMediaSource }
        )

        val initialPlayerRef = manager.exoPlayer
        assertSame("Manager must expose the single ExoPlayer instance", recordedExoPlayer, initialPlayerRef)

        // 1. Load Home Live Channel #1 (Server 0)
        val type1 = manager.loadStream(
            channelId = "home_bein_1",
            url = "https://home.example.com/live/bein1.m3u8",
            serverIndex = 0,
            isLiveChannel = true
        )
        assertEquals(StreamSourceType.HLS, type1)
        assertSame(initialPlayerRef, manager.exoPlayer)
        assertFalse("ExoPlayer must not be released during channel load", exoPlayerInvocations.contains("release"))

        // 2. Switch Server on Home Live Channel #1 (Server 1)
        val type2 = manager.loadStream(
            channelId = "home_bein_1",
            url = "https://home.example.com/live/bein1_backup.m3u8",
            serverIndex = 1,
            isLiveChannel = true,
            downgradeBitrateCap = true
        )
        assertEquals(StreamSourceType.HLS, type2)
        assertSame("Server switch must reuse the exact same ExoPlayer instance", initialPlayerRef, manager.exoPlayer)
        assertEquals(0, manager.sessionController.getChannelSwitchCount())
        assertEquals(2, manager.sessionController.getStreamLoadCount())

        // 3. Switch from Home Live Channel to Xtream IPTV Live Channel (Server 0)
        val type3 = manager.loadStream(
            channelId = "iptv_xtream_5001",
            url = "http://iptv.example.com:8080/live/user/pass/5001.ts",
            serverIndex = 0,
            isLiveChannel = true
        )
        assertEquals(StreamSourceType.MPEG_TS, type3)
        assertSame("Switching from Home to IPTV must reuse the exact same ExoPlayer instance", initialPlayerRef, manager.exoPlayer)
        assertEquals(1, manager.sessionController.getChannelSwitchCount())
        assertEquals(3, manager.sessionController.getStreamLoadCount())
        assertFalse("ExoPlayer must never be released when switching between Home and IPTV", exoPlayerInvocations.contains("release"))

        // 4. Switch to another IPTV Live Channel
        manager.loadStream(
            channelId = "iptv_xtream_5002",
            url = "http://iptv.example.com:8080/live/user/pass/5002.m3u8",
            serverIndex = 0,
            isLiveChannel = true
        )
        assertSame(initialPlayerRef, manager.exoPlayer)
        assertEquals(2, manager.sessionController.getChannelSwitchCount())
        assertEquals(4, manager.sessionController.getStreamLoadCount())
        assertEquals(4, exoPlayerInvocations.count { it == "setMediaSource" })
        assertEquals(4, exoPlayerInvocations.count { it == "prepare" })
        assertEquals(0, exoPlayerInvocations.count { it == "release" })

        // 5. Release the player on screen exit
        manager.release()
        assertTrue(manager.isReleased())
        assertEquals("ExoPlayer.release() must be called exactly once", 1, exoPlayerInvocations.count { it == "release" })

        // Clear recorded invocations after release() to verify ZERO post-release calls reach ExoPlayer
        exoPlayerInvocations.clear()

        // 6. Attempt operations after release() -> all must be safely blocked before touching ExoPlayer
        manager.loadStream(
            channelId = "post_release_channel",
            url = "https://home.example.com/live/after_release.m3u8",
            serverIndex = 0,
            isLiveChannel = true
        )
        manager.seekToLiveEdgeNonDestructive()
        manager.applyQualityMode(isAutoQuality = true, isDataSaver = false)
        manager.release()

        assertTrue(
            "Zero methods may be invoked on ExoPlayer after release(), but found: $exoPlayerInvocations",
            exoPlayerInvocations.isEmpty()
        )
        assertEquals(4, manager.sessionController.getIgnoredPostReleaseCallCount())
    }

    @Test
    fun `detectStreamSourceType resolves extensionless URLs using Content-Type streamType serverNameHint and Xtream structure`() {
        assertEquals(
            StreamSourceType.HLS,
            StreamSourceResolver.detectStreamSourceType(
                url = "https://cdn.example.net/stream/index.m3u8?token=abc123xyz&expires=199999",
                isLiveChannel = true
            )
        )
        assertEquals(
            StreamSourceType.MPEG_TS,
            StreamSourceResolver.detectStreamSourceType(
                url = "http://iptv.example.com:8080/live/user123/pass456/9988",
                isLiveChannel = true
            )
        )
        assertEquals(
            StreamSourceType.HLS,
            StreamSourceResolver.detectStreamSourceType(
                url = "https://gateway.example.org/watch/channel_500",
                serverNameHint = "سيرفر أساسي (M3U8)",
                isLiveChannel = true
            )
        )

        val extensionlessUrl = "http://iptv.example.com:8080/live/user123/pass456/7777"
        StreamSourceResolver.recordObservedContentType(
            url = extensionlessUrl,
            contentTypeHeader = "application/vnd.apple.mpegurl"
        )
        assertEquals(
            StreamSourceType.HLS,
            StreamSourceResolver.detectStreamSourceType(
                url = extensionlessUrl,
                isLiveChannel = true
            )
        )
    }

    @Test
    fun `selectOptimalInitialServerIndex respects hardware capabilities without assuming lower quality SD is faster than primary FHD`() {
        val standardHardware = DeviceCodecCapabilities(
            supportsHardwareH264 = true,
            supportsHardwareHevc = false,
            supportsHardware4k = false,
            isLowRamDevice = false
        )
        val lowRamHardware = DeviceCodecCapabilities(
            supportsHardwareH264 = true,
            supportsHardwareHevc = false,
            supportsHardware4k = false,
            isLowRamDevice = true
        )

        val fhdAndSdServers = listOf(
            ChannelServer(id = "s1", name = "سيرفر 1 FHD 1080p", streamUrl = "https://a.com/fhd.m3u8", streamType = "m3u8"),
            ChannelServer(id = "s2", name = "سيرفر 2 SD 480p", streamUrl = "https://a.com/sd.m3u8", streamType = "m3u8")
        )
        assertEquals(
            0,
            StreamSourceResolver.selectOptimalInitialServerIndex(
                servers = fhdAndSdServers,
                isLiveChannel = true,
                deviceCapabilities = standardHardware
            )
        )
        assertEquals(
            1,
            StreamSourceResolver.selectOptimalInitialServerIndex(
                servers = fhdAndSdServers,
                isLiveChannel = true,
                deviceCapabilities = lowRamHardware
            )
        )
    }

    @Test
    fun `PlayerDiagnostics redacts Xtream credentials tokens and userInfo from both URLs and free-form error details`() {
        val rawXtreamUrl = "http://vip-server.tv:8080/live/ahmed_user_99/secretPass2026/14920.m3u8?token=super_secret_jwt_token"
        val rawExceptionDetails =
            "HttpDataSourceException: Unable to connect to http://vip-server.tv:8080/live/ahmed_user_99/secretPass2026/14920.ts?auth=my_secret_key99 " +
                "via proxy http://proxyUser:proxySecret@10.0.0.1:8080 with Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"

        PlayerDiagnostics.record(
            event = PlayerDiagnosticEvent.PLAYER_ERROR,
            rawUrl = rawXtreamUrl,
            serverIndex = 0,
            sourceType = StreamSourceType.MPEG_TS.label,
            details = rawExceptionDetails
        )

        val entry = PlayerDiagnostics.getRecentEntries().first()
        assertFalse(entry.sanitizedUrl.contains("ahmed_user_99"))
        assertFalse(entry.sanitizedUrl.contains("secretPass2026"))
        assertFalse(entry.sanitizedUrl.contains("super_secret_jwt_token"))
        assertFalse(entry.details.contains("ahmed_user_99"))
        assertFalse(entry.details.contains("secretPass2026"))
        assertFalse(entry.details.contains("my_secret_key99"))
        assertFalse(entry.details.contains("proxySecret"))
        assertFalse(entry.details.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    @Test
    fun `PlaybackRecoveryManager performs immediate server switch on HTTP 404 or 403 and avoids infinite server loops`() {
        val recovery = PlaybackRecoveryManager(maxRetriesPerServer = 2)
        recovery.onChannelOrManualServerSelected(initialServerIndex = 0, totalServers = 2, nowRealtimeMs = 1_000L)

        val decision1 = recovery.onPlayerErrorDetails(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS",
            message = "Response code: 404 Not Found",
            currentUrl = "http://host.com/live/myuser/mypass/101.m3u8",
            sourceType = StreamSourceType.HLS,
            isLiveChannel = true,
            totalServers = 2,
            nowRealtimeMs = 1_200L
        )
        assertTrue(decision1 is RecoveryDecision.SwitchToNextServer)
        assertEquals(1, (decision1 as RecoveryDecision.SwitchToNextServer).nextServerIndex)

        recovery.onAutoServerSwitched(newServerIndex = 1, totalServers = 2, nowRealtimeMs = 2_000L)

        val decision2 = recovery.onPlayerErrorDetails(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS",
            message = "Response code: 404 Not Found",
            currentUrl = "http://host.com/live/myuser/mypass/101.ts",
            sourceType = StreamSourceType.MPEG_TS,
            isLiveChannel = true,
            totalServers = 2,
            nowRealtimeMs = 2_500L
        )
        assertTrue(decision2 is RecoveryDecision.AllServersExhausted)
        assertEquals(
            StreamFailureCategory.HTTP_FATAL_STATUS,
            (decision2 as RecoveryDecision.AllServersExhausted).category
        )
    }

    @Test
    fun `Cinema VOD - WatchProgressManager ignores first 15s saves separate movie and episode progress and marks 93 percent as completed`() {
        // 1. Opening a movie for only 8 seconds (< 15s) must NOT create a resume entry
        val earlyEntry = WatchProgressManager.saveProgress(
            accountId = "acc_1",
            mediaId = "movie_101",
            title = "Inception",
            posterUrl = "https://img/inception.jpg",
            isSeries = false,
            positionMs = 8_000L,
            durationMs = 7_200_000L
        )
        assertNull("Progress < 15s must not be saved on initial open", earlyEntry)
        assertNull(WatchProgressManager.getMovieProgress("acc_1", "movie_101"))

        // 2. Watching to 25 minutes (1,500,000ms) saves progress and offers resume prompt
        val midEntry = WatchProgressManager.saveProgress(
            accountId = "acc_1",
            mediaId = "movie_101",
            title = "Inception",
            posterUrl = "https://img/inception.jpg",
            isSeries = false,
            positionMs = 1_500_000L,
            durationMs = 7_200_000L,
            nowEpochMs = 1_000L
        )
        assertTrue(WatchProgressManager.shouldOfferResume(midEntry))
        assertEquals(1_500_000L, WatchProgressManager.getMovieProgress("acc_1", "movie_101")?.positionMs)
        assertEquals(1, WatchProgressManager.continueWatchingFlow.value.size)

        // 3. Series episode progress is stored separately per episode and deduplicated in Continue Watching
        WatchProgressManager.saveProgress(
            accountId = "acc_1",
            mediaId = "ep_501",
            title = "Breaking Bad",
            posterUrl = "https://img/bb.jpg",
            isSeries = true,
            seriesId = "series_50",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeId = "ep_501",
            episodeTitle = "Pilot",
            positionMs = 600_000L,
            durationMs = 3_000_000L,
            nowEpochMs = 2_000L
        )
        WatchProgressManager.saveProgress(
            accountId = "acc_1",
            mediaId = "ep_502",
            title = "Breaking Bad",
            posterUrl = "https://img/bb.jpg",
            isSeries = true,
            seriesId = "series_50",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeId = "ep_502",
            episodeTitle = "Cat's in the Bag",
            positionMs = 900_000L,
            durationMs = 3_000_000L,
            nowEpochMs = 3_000L
        )
        assertEquals(600_000L, WatchProgressManager.getEpisodeProgress("acc_1", "series_50", 1, 1, "ep_501")?.positionMs)
        assertEquals(900_000L, WatchProgressManager.getEpisodeProgress("acc_1", "series_50", 1, 2, "ep_502")?.positionMs)
        assertEquals("ep_502", WatchProgressManager.getLastWatchedEpisodeForSeries("acc_1", "series_50")?.episodeId)

        // 4. Watching movie past 93% (e.g. 95% = 6,840,000ms of 7,200,000ms) marks completed and disables resume prompt
        val completedEntry = WatchProgressManager.saveProgress(
            accountId = "acc_1",
            mediaId = "movie_101",
            title = "Inception",
            posterUrl = "https://img/inception.jpg",
            isSeries = false,
            positionMs = 6_840_000L,
            durationMs = 7_200_000L,
            nowEpochMs = 4_000L
        )
        assertTrue(completedEntry!!.isCompleted)
        assertFalse("Completed movie must not prompt to resume near credits", WatchProgressManager.shouldOfferResume(completedEntry))
    }

    @Test
    fun `Cinema VOD - CinemaSubtitleParser parses SRT and VTT cues and applies 250ms step delay synchronization`() {
        val sampleSrt = """
            1
            00:00:02,000 --> 00:00:05,000
            <i>مرحباً بكم في مشغل FOUL TV السينمائي</i>

            2
            00:00:06,500 --> 00:00:09,000
            السطر الثاني من الترجمة
        """.trimIndent()

        val cues = CinemaSubtitleParser.parseSubtitleText(sampleSrt)
        assertEquals(2, cues.size)
        assertEquals(2_000L, cues[0].startTimeMs)
        assertEquals(5_000L, cues[0].endTimeMs)
        assertEquals("مرحباً بكم في مشغل FOUL TV السينمائي", cues[0].text)

        // At 1_800ms with 0ms offset -> empty
        assertEquals("", CinemaSubtitleParser.findActiveCueText(cues, playbackPositionMs = 1_800L, subtitleDelayOffsetMs = 0L))
        // At 2_500ms with 0ms offset -> first cue
        assertEquals(
            "مرحباً بكم في مشغل FOUL TV السينمائي",
            CinemaSubtitleParser.findActiveCueText(cues, playbackPositionMs = 2_500L, subtitleDelayOffsetMs = 0L)
        )
        // With +750ms (+0.75s = 3 steps of +0.25s) delay, cue 1 now starts at 2_750ms, so at 2_500ms it has not appeared yet
        assertEquals(
            "",
            CinemaSubtitleParser.findActiveCueText(cues, playbackPositionMs = 2_500L, subtitleDelayOffsetMs = 750L)
        )
        assertEquals(
            "مرحباً بكم في مشغل FOUL TV السينمائي",
            CinemaSubtitleParser.findActiveCueText(cues, playbackPositionMs = 3_000L, subtitleDelayOffsetMs = 750L)
        )
    }

    @Test
    fun `Cinema VOD - SeriesEpisodeNavigator resolves cross-season neighbours and gates Auto-Next-Episode countdown safely`() {
        val seasons = listOf(
            SeriesSeason(seasonNumber = 1, name = "الموسم 1", episodeCount = 2, coverUrl = ""),
            SeriesSeason(seasonNumber = 2, name = "الموسم 2", episodeCount = 1, coverUrl = "")
        )
        val epS1E1 = SeriesEpisode("101", 1, 1, "البداية", "", "45:00", "9.0", "", "mp4", "")
        val epS1E2 = SeriesEpisode("102", 2, 1, "المواجهة", "", "45:00", "9.2", "", "mp4", "")
        val epS2E1 = SeriesEpisode("201", 1, 2, "موسم جديد", "", "50:00", "9.5", "", "mp4", "")

        val map = mapOf(
            1 to listOf(epS1E1, epS1E2),
            2 to listOf(epS2E1)
        )

        // S1E2 should have previous = S1E1 and next = S2E1 (cross-season transition!)
        val navS1E2 = SeriesEpisodeNavigator.resolveNeighbours(
            seasons = seasons,
            episodesBySeason = map,
            currentSeasonNumber = 1,
            currentEpisodeId = "102",
            currentEpisodeNumber = 2
        )
        assertEquals("101", navS1E2.previousEpisode?.id)
        assertEquals("201", navS1E2.nextEpisode?.id)
        assertEquals(2, navS1E2.nextSeasonNumber)

        // Auto-next prompt triggers within final 20s (e.g. 2,685,000ms of 2,700,000ms)
        assertTrue(
            SeriesEpisodeNavigator.shouldShowAutoNextPrompt(
                isSeries = true,
                autoNextEnabled = true,
                nextEpisode = navS1E2.nextEpisode,
                isCancelledForCurrentEpisode = false,
                positionMs = 2_685_000L,
                durationMs = 2_700_000L,
                isPlaybackEnded = false
            )
        )

        // Countdown pauses when user opens settings/episodes modal or pauses video
        assertFalse(
            SeriesEpisodeNavigator.shouldAdvanceCountdownTick(
                isPromptVisible = true,
                isPlaying = true,
                isPlaybackEnded = false,
                isMenuOrSheetOpen = true
            )
        )
        assertFalse(
            SeriesEpisodeNavigator.shouldAdvanceCountdownTick(
                isPromptVisible = true,
                isPlaying = false,
                isPlaybackEnded = false,
                isMenuOrSheetOpen = false
            )
        )

        // Final episode S2E1 has no nextEpisode -> Auto-Next never triggers
        val navS2E1 = SeriesEpisodeNavigator.resolveNeighbours(
            seasons = seasons,
            episodesBySeason = map,
            currentSeasonNumber = 2,
            currentEpisodeId = "201",
            currentEpisodeNumber = 1
        )
        assertNull(navS2E1.nextEpisode)
        assertFalse(
            SeriesEpisodeNavigator.shouldShowAutoNextPrompt(
                isSeries = true,
                autoNextEnabled = true,
                nextEpisode = navS2E1.nextEpisode,
                isCancelledForCurrentEpisode = false,
                positionMs = 2_995_000L,
                durationMs = 3_000_000L,
                isPlaybackEnded = true
            )
        )
    }

    @Test
    fun `Cinema VOD - UnifiedPlayerManager preserves startPositionMs when reloading or switching servers for VOD and detects MPEG-DASH`() {
        assertEquals(
            StreamSourceType.DASH,
            StreamSourceResolver.detectStreamSourceType(
                url = "https://vod.example.com/movies/avatar_4k/manifest.mpd?token=xyz",
                isLiveChannel = false
            )
        )

        var lastSetPositionMs: Long = C.TIME_UNSET
        var resetPositionCalled = false

        val dummyMediaSource = Proxy.newProxyInstance(
            MediaSource::class.java.classLoader,
            arrayOf(MediaSource::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "DummyVodMediaSource"
                "hashCode" -> 1
                "equals" -> false
                else -> null
            }
        } as MediaSource

        val recordedExoPlayer = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "setMediaSource" -> {
                    if (args != null && args.size == 2 && args[1] is Long) {
                        lastSetPositionMs = args[1] as Long
                    } else if (args != null && args.size == 2 && args[1] is Boolean) {
                        resetPositionCalled = args[1] as Boolean
                    }
                    Unit
                }
                "getTrackSelectionParameters" -> TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                "getPlaybackState" -> Player.STATE_READY
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "VodExoPlayerProxy"
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }
        } as ExoPlayer

        val manager = UnifiedPlayerManager(
            context = null,
            customCapabilities = DeviceCodecCapabilities(),
            customExoPlayer = recordedExoPlayer,
            customMediaSourceProvider = { _, _, _ -> dummyMediaSource }
        )

        // Load VOD movie at saved resume position 1,845,000ms (30m 45s)
        val sourceType = manager.loadStream(
            channelId = "movie_777",
            url = "http://vod.example.com:8080/movie/user/pass/777.mkv",
            serverIndex = 0,
            isLiveChannel = false,
            startPositionMs = 1_845_000L
        )
        assertEquals(StreamSourceType.MKV, sourceType)
        assertEquals("VOD loadStream must pass startPositionMs to setMediaSource", 1_845_000L, lastSetPositionMs)
        assertFalse(resetPositionCalled)

        // Verify configurable seek step defaults and updates
        VodPlayerPreferencesManager.updateSeekSteps(forwardSeconds = 15, rewindSeconds = 10)
        assertEquals(15_000L, VodPlayerPreferencesManager.preferencesFlow.value.forwardSeekMs)
        assertEquals(10_000L, VodPlayerPreferencesManager.preferencesFlow.value.rewindSeekMs)
        VodPlayerPreferencesManager.updateSeekSteps(forwardSeconds = 10, rewindSeconds = 10)
    }

    @Test
    fun `Section 1 - OkHttpStreamDataSource handles 206 Partial Content when server returns smaller 20-byte chunk than requested 50 bytes`() {
        // Caller requests 50 bytes from offset 0 (bytes=0-49), but server legitimately serves a 20-byte chunk
        // declared via Content-Range: bytes 0-19/100 and Content-Length: 20.
        val twentyBytes = "0123456789abcdefghij".toByteArray(Charsets.UTF_8)
        val fakeCallFactory = Call.Factory { request ->
            object : Call {
                override fun request(): Request = request
                override fun execute(): Response {
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_2)
                        .code(206)
                        .message("Partial Content")
                        .header("Content-Range", "bytes 0-19/100")
                        .header("Content-Length", "20")
                        .body(twentyBytes.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }
                override fun enqueue(responseCallback: Callback) = Unit
                override fun cancel() = Unit
                override fun isExecuted(): Boolean = true
                override fun isCanceled(): Boolean = false
                override fun timeout(): Timeout = Timeout.NONE
                override fun clone(): Call = this
            }
        }

        val dataSource = OkHttpStreamDataSource(
            callFactory = fakeCallFactory,
            userAgent = "FoulTV-Test/1.2.1"
        )
        val expectedBytes = dataSource.openUrl(
            requestUrlString = "https://vod.example.com/movie/chunked_206.mp4",
            position = 0L,
            length = 50L
        )
        // Must treat the declared 20-byte partial response as bytesToRead = 20L
        assertEquals(20L, expectedBytes)

        val buffer = ByteArray(32)
        val readCount = dataSource.read(buffer, 0, buffer.size)
        assertEquals(20, readCount)
        assertArrayEquals(twentyBytes, buffer.copyOfRange(0, 20))
        // Reaching the end of the declared 20-byte partial chunk must return RESULT_END_OF_INPUT cleanly
        assertEquals(C.RESULT_END_OF_INPUT, dataSource.read(buffer, 0, buffer.size))
        dataSource.close()
    }

    @Test
    fun `Cinema VOD - VodMediaCacheManager generates collision-free SHA-256 cache keys with zero plain-text credentials`() {
        val urlAccountA = "http://xtream.server.tv:8080/movie/user_alpha/secret_pass_111/9050.mp4?token=jwt_aaa"
        val urlAccountB = "http://xtream.server.tv:8080/movie/user_beta/secret_pass_222/9050.mp4?token=jwt_bbb"

        val keyA = com.example.ui.player.VodMediaCacheManager.computeSha256CacheKey(urlAccountA)
        val keyB = com.example.ui.player.VodMediaCacheManager.computeSha256CacheKey(urlAccountB)

        assertTrue("Cache key must start with vod_sha256_ prefix", keyA.startsWith("vod_sha256_"))
        assertEquals("SHA-256 hex digest length must be 11 + 64 = 75 chars", 75, keyA.length)
        assertFalse("Two different accounts/tokens for the same movie ID must never collide", keyA == keyB)

        // Ensure zero plain-text credentials or tokens appear in the cache key
        assertFalse(keyA.contains("user_alpha"))
        assertFalse(keyA.contains("secret_pass_111"))
        assertFalse(keyA.contains("jwt_aaa"))
    }

    @Test
    fun `Cinema VOD - CinemaSubtitleParser supports WebVTT HH-MM-SS and MM-SS formats ASS dialogue lines and both positive and negative delay offsets`() {
        val sampleVtt = """
            WEBVTT
            Kind: captions
            Language: ar

            STYLE
            ::cue { color: yellow; }

            00:05.000 --> 00:08.000 align:middle line:90%
            <c.yellow>ترجمة بصيغة MM:SS.mmm</c>

            00:01:10.250 --> 00:01:14.750
            ترجمة بصيغة HH:MM:SS.mmm
        """.trimIndent()

        val vttCues = CinemaSubtitleParser.parseSubtitleText(sampleVtt)
        assertEquals(2, vttCues.size)
        assertEquals(5_000L, vttCues[0].startTimeMs)
        assertEquals(8_000L, vttCues[0].endTimeMs)
        assertEquals("ترجمة بصيغة MM:SS.mmm", vttCues[0].text)
        assertEquals(70_250L, vttCues[1].startTimeMs)
        assertEquals(74_750L, vttCues[1].endTimeMs)

        // Test negative delay (-1000ms = -1.0s): cue [5000..8000] shifts earlier to [4000..7000]
        assertEquals(
            "ترجمة بصيغة MM:SS.mmm",
            CinemaSubtitleParser.findActiveCueText(vttCues, playbackPositionMs = 4_200L, subtitleDelayOffsetMs = -1_000L)
        )
        assertEquals(
            "",
            CinemaSubtitleParser.findActiveCueText(vttCues, playbackPositionMs = 7_500L, subtitleDelayOffsetMs = -1_000L)
        )

        // Test positive delay (+1000ms = +1.0s): cue [5000..8000] shifts later to [6000..9000]
        assertEquals(
            "",
            CinemaSubtitleParser.findActiveCueText(vttCues, playbackPositionMs = 5_500L, subtitleDelayOffsetMs = 1_000L)
        )
        assertEquals(
            "ترجمة بصيغة MM:SS.mmm",
            CinemaSubtitleParser.findActiveCueText(vttCues, playbackPositionMs = 8_500L, subtitleDelayOffsetMs = 1_000L)
        )

        // Test ASS subtitle parsing
        val sampleAss = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:12.00,0:00:16.50,Default,,0,0,0,,{\pos(400,570)}سطر أول\Nسطر ثانٍ
        """.trimIndent()
        val assCues = CinemaSubtitleParser.parseSubtitleText(sampleAss)
        assertEquals(1, assCues.size)
        assertEquals(12_000L, assCues[0].startTimeMs)
        assertEquals(16_500L, assCues[0].endTimeMs)
        assertEquals("سطر أول\nسطر ثانٍ", assCues[0].text)
    }

    @Test
    fun `Cinema VOD - StreamSourceResolver detects MPD and Content-Type DASH and DashMediaSource Factory is wired`() {
        assertEquals(
            StreamSourceType.DASH,
            StreamSourceResolver.detectStreamSourceType(
                url = "https://vod.example.com/movies/4k_hdr/manifest.mpd",
                isLiveChannel = false
            )
        )
        assertEquals(
            StreamSourceType.DASH,
            StreamSourceResolver.resolveFromContentType("application/dash+xml")
        )
        val dataSourceFactory = OkHttpStreamDataSource.Factory(StreamOkHttpClientProvider.sharedClient)
        val dashFactory = androidx.media3.exoplayer.dash.DashMediaSource.Factory(dataSourceFactory)
        val defaultFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
        assertArrayEquals(
            intArrayOf(C.CONTENT_TYPE_DASH, C.CONTENT_TYPE_SS, C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_RTSP, C.CONTENT_TYPE_OTHER),
            intArrayOf(
                dashFactory.supportedTypes.first(),
                C.CONTENT_TYPE_SS,
                C.CONTENT_TYPE_HLS,
                C.CONTENT_TYPE_RTSP,
                C.CONTENT_TYPE_OTHER
            )
        )
        assertTrue(
            "DefaultMediaSourceFactory must support CONTENT_TYPE_DASH when media3-exoplayer-dash is present",
            defaultFactory.supportedTypes.contains(C.CONTENT_TYPE_DASH)
        )
    }
}
