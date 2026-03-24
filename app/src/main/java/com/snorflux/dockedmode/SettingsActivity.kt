package com.snorflux.dockedmode

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.snorflux.dockedmode.settings.StandbyPreferences
import com.snorflux.dockedmode.ui.theme.DockedModeTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DockedModeTheme {
                SettingsScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = if (isLandscape) 4 else 2
    var settings by remember { mutableStateOf(StandbyPreferences.read(context)) }
    val darkTheme = isSystemInDarkTheme()

    val tileColors = remember(darkTheme) {
        settingsTilePalette(darkTheme).shuffled()
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
                Color(0xFF0D1323),
                Color(0xFF1B263D),
                Color(0xFF111C31)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isLandscape) 1200.dp else 760.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Standby Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFFF1F5FF),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Color tiles reshuffle on each visit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC3CEE5)
                    )
                }
            }

            item {
                SettingTile(
                    title = "Show battery info",
                    subtitle = "Battery percent + charging type",
                    checked = settings.showBatteryInfo,
                    onCheckedChange = { enabled ->
                        StandbyPreferences.setShowBatteryInfo(context, enabled)
                        settings = settings.copy(showBatteryInfo = enabled)
                    },
                    containerColor = tileColors[0 % tileColors.size]
                )
            }

            item {
                SettingTile(
                    title = "Show next alarm",
                    subtitle = "Display your next scheduled alarm",
                    checked = settings.showNextAlarm,
                    onCheckedChange = { enabled ->
                        StandbyPreferences.setShowNextAlarm(context, enabled)
                        settings = settings.copy(showNextAlarm = enabled)
                    },
                    containerColor = tileColors[1 % tileColors.size]
                )
            }

            item {
                SettingTile(
                    title = "Burn-in protection",
                    subtitle = "Slightly shifts UI every minute",
                    checked = settings.enableBurnInProtection,
                    onCheckedChange = { enabled ->
                        StandbyPreferences.setEnableBurnInProtection(context, enabled)
                        settings = settings.copy(enableBurnInProtection = enabled)
                    },
                    containerColor = tileColors[2 % tileColors.size]
                )
            }

            item {
                SettingTile(
                    title = "Show DND indicator",
                    subtitle = "Shows when do-not-disturb is active",
                    checked = settings.showDndIndicator,
                    onCheckedChange = { enabled ->
                        StandbyPreferences.setShowDndIndicator(context, enabled)
                        settings = settings.copy(showDndIndicator = enabled)
                    },
                    containerColor = tileColors[3 % tileColors.size]
                )
            }

            item {
                SettingTile(
                    title = "Auto dim",
                    subtitle = "Dims display after inactivity; tap to wake",
                    checked = settings.enableAutoDim,
                    onCheckedChange = { enabled ->
                        StandbyPreferences.setEnableAutoDim(context, enabled)
                        settings = settings.copy(enableAutoDim = enabled)
                    },
                    containerColor = tileColors[4 % tileColors.size]
                )
            }

            item {
                SettingTile(
                    title = "Media controls",
                    subtitle = "Shows play/pause/skip for active sessions",
                    checked = settings.showMediaControls,
                    onCheckedChange = { enabled ->
                        StandbyPreferences.setShowMediaControls(context, enabled)
                        settings = settings.copy(showMediaControls = enabled)
                    },
                    containerColor = tileColors[5 % tileColors.size]
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SettingTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    containerColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF5F8FF),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFDCE7FA)
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        }
    }
}

private fun settingsTilePalette(darkTheme: Boolean): List<Color> {
    return if (darkTheme) {
        listOf(
            Color(0xFF2A4E7A),
            Color(0xFF314E68),
            Color(0xFF5A3D6A),
            Color(0xFF2F5C55),
            Color(0xFF624A30),
            Color(0xFF61425E)
        )
    } else {
        listOf(
            Color(0xFF3D70B3),
            Color(0xFF40739D),
            Color(0xFF7A4FA0),
            Color(0xFF3A847B),
            Color(0xFF9B6B3A),
            Color(0xFF8F5F87)
        )
    }
}
