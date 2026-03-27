package com.snorflux.dockedmode

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.widget.ImageView
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import com.snorflux.dockedmode.notifications.NotificationFeed
import com.snorflux.dockedmode.notifications.StandbyNotificationListenerService
import com.snorflux.dockedmode.settings.StandbyPreferences
import com.snorflux.dockedmode.settings.StandbyUiSettings
import com.snorflux.dockedmode.standby.StandbyLaunchNotifier
import com.snorflux.dockedmode.standby.StandbyModeController
import com.snorflux.dockedmode.ui.theme.DockedModeTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class PackageNotificationSummary(
    val packageName: String,
    val count: Int,
    val latestAt: Long
)

private data class BatteryUiInfo(
    val level: Int,
    val chargingSource: String
)

private data class MediaSessionUi(
    val controller: MediaController,
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val albumArt: Bitmap?
)

private enum class DashboardTileType {
    Alarm,
    Notifications,
    Battery,
    Media,
    Dnd
}

private val DashboardTilePriority = listOf(
    DashboardTileType.Notifications,
    DashboardTileType.Media,
    DashboardTileType.Alarm,
    DashboardTileType.Battery,
    DashboardTileType.Dnd
)

private val BurnInOffsets = listOf(
    IntOffset(0, 0),
    IntOffset(4, 2),
    IntOffset(-3, 3),
    IntOffset(3, -2),
    IntOffset(-4, -3),
    IntOffset(2, 4),
    IntOffset(-2, -4)
)

class StandbyActivity : ComponentActivity() {
    private val modeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!StandbyModeController.isReadyForManualLaunch(context)) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()

        setContent {
            DockedModeTheme(darkTheme = true, dynamicColor = false) {
                StandbyScreen()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun onStart() {
        super.onStart()
        StandbyLaunchNotifier.cancel(this)
        StandbyModeController.isStandbyActive = true
        registerModeReceiver()
        if (!StandbyModeController.isReadyForManualLaunch(this)) {
            finish()
        }
    }

    override fun onStop() {
        StandbyModeController.isStandbyActive = false
        unregisterReceiver(modeReceiver)
        super.onStop()
    }

    private fun registerModeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }

        ContextCompat.registerReceiver(
            this,
            modeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun MediaControls(
    media: MediaSessionUi,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 12.dp else 14.dp,
                    vertical = if (compact) 8.dp else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Text(
                text = media.title,
                style = if (compact) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = Color(0xFFF3F7FF)
            )

            if (!compact && media.subtitle.isNotBlank()) {
                Text(
                    text = media.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC1CEE1)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    if (compact) 8.dp else 10.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportButton(label = "⏮", compact = compact) {
                    media.controller.transportControls.skipToPrevious()
                }
                TransportButton(
                    label = if (media.isPlaying) "⏸" else "▶",
                    compact = compact
                ) {
                    if (media.isPlaying) {
                        media.controller.transportControls.pause()
                    } else {
                        media.controller.transportControls.play()
                    }
                }
                TransportButton(label = "⏭", compact = compact) {
                    media.controller.transportControls.skipToNext()
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    label: String,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(Color(0x2AFFFFFF), shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 9.dp else 10.dp,
                vertical = if (compact) 5.dp else 6.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleLarge
            },
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE4EEFF)
        )
    }
}

@Composable
private fun BoundedMediaControls(
    media: MediaSessionUi,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val targetWidth = (maxWidth * 0.84f).coerceAtMost(560.dp)
        MediaControls(
            media = media,
            compact = compact,
            modifier = Modifier.width(targetWidth)
        )
    }
}

@Composable
private fun StandbyScreen() {
    val context = LocalContext.current
    val settings = remember { StandbyPreferences.read(context) }
    val notifications by NotificationFeed.notifications.collectAsState()
    val groupedNotifications = remember(notifications) {
        notifications
            .groupBy { it.packageName }
            .map { (packageName, packageNotifications) ->
                PackageNotificationSummary(
                    packageName = packageName,
                    count = packageNotifications.size,
                    latestAt = packageNotifications.maxOf { it.receivedAt }
                )
            }
            .sortedByDescending { it.latestAt }
    }

    val now = rememberNow()
    val batteryInfo = rememberBatteryInfo(settings)
    val nextAlarmLabel = rememberNextAlarmLabel(settings)
    val dndEnabled = rememberDndEnabled(settings)
    val mediaSession = rememberMediaSessionUi(settings)
    val burnInOffset = rememberBurnInOffset(settings)

    var dimmed by remember { mutableStateOf(false) }
    var lastInteractionAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(settings.enableAutoDim, lastInteractionAt) {
        if (!settings.enableAutoDim) {
            dimmed = false
            return@LaunchedEffect
        }

        delay(35_000)
        dimmed = true
    }

    val tileOrder = remember(
        settings,
        nextAlarmLabel,
        groupedNotifications,
        batteryInfo,
        mediaSession,
        dndEnabled
    ) {
        val availableTiles = buildList {
            if (settings.showNextAlarm && nextAlarmLabel != null) {
                add(DashboardTileType.Alarm)
            }
            add(DashboardTileType.Notifications)
            if (settings.showBatteryInfo && batteryInfo != null) {
                add(DashboardTileType.Battery)
            }
            if (settings.showMediaControls && mediaSession != null) {
                add(DashboardTileType.Media)
            }
            if (settings.showDndIndicator && dndEnabled == true) {
                add(DashboardTileType.Dnd)
            }
        }

        DashboardTilePriority.filter { it in availableTiles }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(settings.enableAutoDim) {
                if (settings.enableAutoDim) {
                    detectTapGestures {
                        dimmed = false
                        lastInteractionAt = SystemClock.elapsedRealtime()
                    }
                }
            }
            .alpha(if (dimmed) 0.42f else 1f)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030509),
                        Color(0xFF111827),
                        Color(0xFF05070B)
                    )
                )
            )
            .padding(horizontal = 36.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { burnInOffset },
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ClockPanel(
                now = now,
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            )

            DashboardPanel(
                tileOrder = tileOrder,
                nextAlarmLabel = nextAlarmLabel,
                batteryInfo = batteryInfo,
                dndEnabled = dndEnabled == true,
                mediaSession = mediaSession,
                notifications = groupedNotifications,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ClockPanel(
    now: LocalDateTime,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val amPm = if (is24Hour) {
        ""
    } else {
        now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0x12000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x1F53A5FF),
                            Color(0x0D2B4F7A)
                        )
                    )
                )
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        modifier = Modifier.size(26.dp),
                        onClick = {
                            context.startActivity(
                                Intent(context, MainActivity::class.java).apply {
                                    putExtra(MainActivity.EXTRA_FORCE_SETUP, true)
                                }
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Open home",
                            tint = Color(0xFFB9CCE6)
                        )
                    }

                    IconButton(
                        modifier = Modifier.size(26.dp),
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Open settings",
                            tint = Color(0xFFB9CCE6)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = now.format(
                            DateTimeFormatter.ofPattern(
                                if (is24Hour) "HH" else "hh",
                                Locale.getDefault()
                            )
                        ),
                        color = Color(0xFFEAF4FF),
                        fontSize = 124.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 114.sp
                    )

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = now.format(DateTimeFormatter.ofPattern("mm", Locale.getDefault())),
                            color = Color(0xFFEAF4FF),
                            fontSize = 124.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 114.sp
                        )

                        if (!is24Hour) {
                            Text(
                                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
                                text = amPm,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color(0xFFD4E6FF)
                            )
                        }
                    }
                }
            }

            Text(
                text = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFBDD0EA)
            )
        }
    }
}

@Composable
private fun DashboardPanel(
    tileOrder: List<DashboardTileType>,
    nextAlarmLabel: String?,
    batteryInfo: BatteryUiInfo?,
    dndEnabled: Boolean,
    mediaSession: MediaSessionUi?,
    notifications: List<PackageNotificationSummary>,
    modifier: Modifier = Modifier
) {
    val pages = remember(tileOrder) { tileOrder.chunked(4) }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pageCount }
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            DashboardGridPage(
                tiles = pages.getOrNull(page).orEmpty(),
                nextAlarmLabel = nextAlarmLabel,
                batteryInfo = batteryInfo,
                dndEnabled = dndEnabled,
                mediaSession = mediaSession,
                notifications = notifications,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (pageCount > 1) {
            PageIndicators(
                pageCount = pageCount,
                currentPage = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DashboardGridPage(
    tiles: List<DashboardTileType>,
    nextAlarmLabel: String?,
    batteryInfo: BatteryUiInfo?,
    dndEnabled: Boolean,
    mediaSession: MediaSessionUi?,
    notifications: List<PackageNotificationSummary>,
    modifier: Modifier = Modifier
) {
    val rows = remember(tiles) { tiles.chunked(2).take(2) }

    if (rows.isEmpty()) {
        DashboardTileCard(
            title = "Standby",
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x244A5A77), Color(0x141C2431))
            ),
            modifier = modifier
        ) {
            Text(
                text = "Waiting for cards",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC8D5E7)
            )
        }
        return
    }

    if (rows.size == 1 && rows.first().size == 1) {
        DashboardTileCell(
            tile = rows.first().first(),
            nextAlarmLabel = nextAlarmLabel,
            batteryInfo = batteryInfo,
            dndEnabled = dndEnabled,
            mediaSession = mediaSession,
            notifications = notifications,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { tile ->
                    DashboardTileCell(
                        tile = tile,
                        nextAlarmLabel = nextAlarmLabel,
                        batteryInfo = batteryInfo,
                        dndEnabled = dndEnabled,
                        mediaSession = mediaSession,
                        notifications = notifications,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                if (rowItems.size == 1) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        if (rows.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PageIndicators(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (active) 10.dp else 8.dp)
                    .background(
                        color = if (active) Color(0xFFE3ECFF) else Color(0x66C5D2E8),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

@Composable
private fun DashboardTileCell(
    tile: DashboardTileType,
    nextAlarmLabel: String?,
    batteryInfo: BatteryUiInfo?,
    dndEnabled: Boolean,
    mediaSession: MediaSessionUi?,
    notifications: List<PackageNotificationSummary>,
    modifier: Modifier = Modifier
) {
    when (tile) {
        DashboardTileType.Alarm -> {
            DashboardTileCard(
                title = "Alarms",
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x3344C6B8), Color(0x22233F54))
                ),
                modifier = modifier
            ) {
                Text(
                    text = "⏰ Upcoming",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD2FAF4)
                )
                Text(
                    text = nextAlarmLabel ?: "No alarm scheduled",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFE7FFFD)
                )
                Text(
                    text = if (nextAlarmLabel != null) {
                        "From your default clock app"
                    } else {
                        "Set an alarm to show it here"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7DCD8)
                )
            }
        }

        DashboardTileType.Notifications -> {
            DashboardTileCard(
                title = "Notifications",
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x292A3848), Color(0x1A1C2735))
                ),
                modifier = modifier
            ) {
                if (notifications.isEmpty()) {
                    Text(
                        text = "No notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC8D4E8)
                    )
                } else {
                    NotificationList(
                        notifications = notifications.take(3),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (notifications.size > 3) {
                        Text(
                            text = "+${notifications.size - 3} more apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA8B8CF)
                        )
                    }
                    Text(
                        text = "${notifications.sumOf { it.count }} alerts from ${notifications.size} apps",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB7C7DE)
                    )
                }
            }
        }

        DashboardTileType.Battery -> {
            val batteryLevel = (batteryInfo?.level ?: 0).coerceIn(0, 100)
            val batteryFraction = batteryLevel / 100f
            DashboardTileCard(
                title = "Battery",
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x336C3F12), Color(0x1F2A1B0D))
                ),
                modifier = modifier
            ) {
                Text(
                    text = "$batteryLevel%",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFFFFD69A)
                )
                Text(
                    text = "Charging • ${batteryInfo?.chargingSource ?: "Power"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFFFE8C4)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0x33FFD69A), shape = MaterialTheme.shapes.small)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(batteryFraction)
                            .fillMaxHeight()
                            .background(Color(0xFFFFC56C), shape = MaterialTheme.shapes.small)
                    )
                }
                Text(
                    text = "Live battery status",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDCBC93)
                )
            }
        }

        DashboardTileType.Media -> {
            MediaDashboardTile(
                mediaSession = mediaSession,
                modifier = modifier
            )
        }

        DashboardTileType.Dnd -> {
            DashboardTileCard(
                title = "Focus",
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x332F3758), Color(0x1D161B2D))
                ),
                modifier = modifier
            ) {
                Text(
                    text = if (dndEnabled) "Do not disturb is ON" else "Do not disturb is OFF",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFD7E2F5)
                )
            }
        }
    }
}

@Composable
private fun MediaDashboardTile(
    mediaSession: MediaSessionUi?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val albumArt = mediaSession?.albumArt
            if (albumArt != null) {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0x334F2E78), Color(0x221F1836))
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xB226133A), Color(0xCC0B0C1A))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF4E8FF)
                )

                if (mediaSession == null) {
                    Text(
                        text = "No active media",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD6C7E9)
                    )
                } else {
                    Text(
                        text = mediaSession.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF7EEFF)
                    )
                    Text(
                        text = mediaSession.subtitle.ifBlank { "Unknown Artist" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE3D4F4)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TransportButton(label = "⏮", compact = true) {
                            mediaSession.controller.transportControls.skipToPrevious()
                        }
                        TransportButton(
                            label = if (mediaSession.isPlaying) "⏸" else "▶",
                            compact = true
                        ) {
                            if (mediaSession.isPlaying) {
                                mediaSession.controller.transportControls.pause()
                            } else {
                                mediaSession.controller.transportControls.play()
                            }
                        }
                        TransportButton(label = "⏭", compact = true) {
                            mediaSession.controller.transportControls.skipToNext()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTileCard(
    title: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE8F2FF)
            )
            content()
        }
    }
}

@Composable
private fun NotificationList(
    notifications: List<PackageNotificationSummary>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notifications.forEach { item ->
            NotificationListItem(item)
        }
    }
}

@Composable
private fun NotificationListItem(item: PackageNotificationSummary) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val appIcon = remember(item.packageName) {
        runCatching { packageManager.getApplicationIcon(item.packageName) }.getOrNull()
    }
    val appName = remember(item.packageName) {
        runCatching {
            val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrElse {
            item.packageName.substringAfterLast('.')
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            modifier = Modifier.size(20.dp),
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(appIcon)
                ImageViewCompat.setImageTintList(imageView, null)
                imageView.imageAlpha = 255
            }
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE4ECFA)
            )
            Text(
                text = "${item.count} notification${if (item.count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8C9DF)
            )
        }

        Text(
            text = "${item.count}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFDDE6F5)
        )
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    return now
}

@Composable
private fun rememberBatteryInfo(settings: StandbyUiSettings): BatteryUiInfo? {
    if (!settings.showBatteryInfo) return null

    val context = LocalContext.current
    var info by remember { mutableStateOf<BatteryUiInfo?>(null) }

    LaunchedEffect(settings.showBatteryInfo) {
        while (true) {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

            val levelPercent = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt()
            } else {
                0
            }

            val chargingSource = when {
                plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
                plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
                plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
                else -> "Charging"
            }

            info = BatteryUiInfo(level = levelPercent, chargingSource = chargingSource)
            delay(15_000)
        }
    }

    return info
}

@Composable
private fun rememberNextAlarmLabel(settings: StandbyUiSettings): String? {
    if (!settings.showNextAlarm) return null

    val context = LocalContext.current
    var alarmLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.showNextAlarm) {
        while (true) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val nextAlarmMillis = alarmManager?.nextAlarmClock?.triggerTime
            alarmLabel = nextAlarmMillis?.let { formatAlarmTime(context, it) }
            delay(60_000)
        }
    }

    return alarmLabel
}

@Composable
private fun rememberDndEnabled(settings: StandbyUiSettings): Boolean? {
    if (!settings.showDndIndicator) return null

    val context = LocalContext.current
    var enabled by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(settings.showDndIndicator) {
        while (true) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            enabled = notificationManager?.currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
            delay(5_000)
        }
    }

    return enabled
}

@Composable
private fun rememberMediaSessionUi(settings: StandbyUiSettings): MediaSessionUi? {
    if (!settings.showMediaControls) return null

    val context = LocalContext.current
    var media by remember { mutableStateOf<MediaSessionUi?>(null) }

    LaunchedEffect(settings.showMediaControls) {
        while (true) {
            media = loadActiveMediaSession(context)
            delay(2_500)
        }
    }

    return media
}

@Composable
private fun rememberBurnInOffset(settings: StandbyUiSettings): IntOffset {
    if (!settings.enableBurnInProtection) return IntOffset.Zero

    var index by remember { mutableStateOf(0) }

    LaunchedEffect(settings.enableBurnInProtection) {
        while (true) {
            delay(60_000)
            index = (index + 1) % BurnInOffsets.size
        }
    }

    return BurnInOffsets[index]
}

private fun loadActiveMediaSession(context: Context): MediaSessionUi? {
    val mediaSessionManager = context.getSystemService(MediaSessionManager::class.java) ?: return null
    val listenerComponent = ComponentName(context, StandbyNotificationListenerService::class.java)

    val controllers = runCatching {
        mediaSessionManager.getActiveSessions(listenerComponent)
    }.getOrElse {
        emptyList()
    }

    val preferredController = controllers.firstOrNull { controller ->
        val state = controller.playbackState?.state
        state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_PAUSED ||
            state == PlaybackState.STATE_BUFFERING
    } ?: controllers.firstOrNull() ?: return null

    val metadata = preferredController.metadata
    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { "Media" }
    val subtitle = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
    val state = preferredController.playbackState?.state
    val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    return MediaSessionUi(
        controller = preferredController,
        title = title,
        subtitle = subtitle,
        isPlaying = state == PlaybackState.STATE_PLAYING,
        albumArt = albumArt
    )
}

private fun formatAlarmTime(context: Context, triggerTimeMillis: Long): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a"
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date(triggerTimeMillis))
}
