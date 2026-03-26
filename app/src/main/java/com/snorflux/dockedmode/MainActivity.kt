package com.snorflux.dockedmode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.snorflux.dockedmode.standby.StandbyModeController
import com.snorflux.dockedmode.standby.StandbyMonitorService
import com.snorflux.dockedmode.ui.theme.DockedModeTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FORCE_SETUP = "extra_force_setup"
        private const val REQUEST_NOTIFICATIONS_PERMISSION = 1201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        StandbyMonitorService.syncWithPowerState(this)

        val forceSetup = intent.getBooleanExtra(EXTRA_FORCE_SETUP, false)
        if (!forceSetup && StandbyModeController.isReadyForManualLaunch(this)) {
            StandbyModeController.openStandby(this)
            finish()
            return
        }

        setContent {
            DockedModeTheme {
                SetupScreen()
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS_PERMISSION
            )
        }
    }
}

@Composable
private fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = if (isLandscape) 4 else 2

    var notificationsEnabled by remember {
        mutableStateOf(StandbyModeController.isNotificationAccessEnabled(context))
    }
    var standbyReady by remember {
        mutableStateOf(StandbyModeController.isReadyForManualLaunch(context))
    }
    val darkTheme = isSystemInDarkTheme()

    val tileColors = remember(darkTheme) {
        launcherTilePalette(darkTheme).shuffled().take(4)
    }

    LaunchedEffect(context) {
        while (true) {
            notificationsEnabled = StandbyModeController.isNotificationAccessEnabled(context)
            standbyReady = StandbyModeController.isReadyForManualLaunch(context)
            delay(1_500)
        }
    }

    val backgroundBrush = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF05070C),
                Color(0xFF111827),
                Color(0xFF070A12)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0C1322),
                Color(0xFF1A2439),
                Color(0xFF0D1323)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isLandscape) 1200.dp else 760.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Docked Mode",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFFF1F5FF),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Launcher tiles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC3CEE5)
                    )
                }
            }

            item {
                SetupActionTile(
                    title = "Notifications",
                    subtitle = if (notificationsEnabled) {
                        "Access enabled"
                    } else {
                        "Access disabled"
                    },
                    buttonText = if (notificationsEnabled) {
                        "Manage Access"
                    } else {
                        "Enable Access"
                    },
                    buttonEnabled = true,
                    containerColor = tileColors[0],
                    onClick = {
                        StandbyModeController.openNotificationListenerSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SetupActionTile(
                    title = "Settings",
                    subtitle = "Personalize dashboard and tiles",
                    buttonText = "Open Settings",
                    buttonEnabled = true,
                    containerColor = tileColors[1],
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SetupActionTile(
                    title = "Start Standby",
                    subtitle = if (standbyReady) {
                        "Conditions met — launch standby"
                    } else {
                        "Needs charging + landscape"
                    },
                    buttonText = "Start",
                    buttonEnabled = standbyReady,
                    containerColor = tileColors[2],
                    onClick = {
                        StandbyModeController.openStandby(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SetupActionTile(
                    title = "Manual Standby",
                    subtitle = "Open standby without condition check",
                    buttonText = "Open Now",
                    buttonEnabled = true,
                    containerColor = tileColors[3],
                    onClick = {
                        StandbyModeController.openStandby(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SetupActionTile(
    title: String,
    subtitle: String,
    buttonText: String,
    buttonEnabled: Boolean,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(188.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFF5F8FF),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFDCE7FA)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onClick,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

private fun launcherTilePalette(darkTheme: Boolean): List<Color> {
    return if (darkTheme) {
        listOf(
            Color(0xFF2A4E7A),
            Color(0xFF314E68),
            Color(0xFF5A3D6A),
            Color(0xFF2F5C55),
            Color(0xFF624A30)
        )
    } else {
        listOf(
            Color(0xFF3D70B3),
            Color(0xFF40739D),
            Color(0xFF7A4FA0),
            Color(0xFF3A847B),
            Color(0xFF9B6B3A)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    DockedModeTheme {
        SetupScreen()
    }
}
