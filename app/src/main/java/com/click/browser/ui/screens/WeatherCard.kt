package com.click.browser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.WeatherFeed
import com.click.browser.engine.WeatherResult
import com.click.browser.engine.WeatherStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Whole-degree temperature label for the weather card, e.g. "31°". */
internal fun weatherTempLabel(celsius: Double): String = "${celsius.roundToInt()}°"

/**
 * Home-dashboard weather card (Prince, 2026-11-21: "Weather show Nahin" —
 * the feature had never been built). Data comes from [WeatherFeed]
 * (Open-Meteo, no key): the device's last known location when the location
 * permission is already granted, otherwise Karachi by default.
 *
 * The card always settles into a clear state — loading, live weather,
 * last-saved weather with an honest caption, or a plain unavailable
 * message — it never stays blank.
 */
@Composable
fun WeatherCard(
    theme: ModeTheme,
    cardBg: Color,
    cardBorder: Color
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<WeatherResult?>(null) }
    LaunchedEffect(Unit) {
        val location = withContext(Dispatchers.IO) { WeatherFeed.resolveLocation(context) }
        result = WeatherFeed.getWeather(context, location)
    }
    val current = result
    val weather = current?.weather
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "WEATHER",
                    color = theme.modeAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                if (weather != null) {
                    Text(
                        weather.cityName,
                        color = theme.onSurface.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
            }
            when {
                current == null -> Text(
                    "Loading weather…",
                    color = theme.onSurface.copy(alpha = 0.65f),
                    fontSize = 12.5.sp
                )
                weather != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            weatherTempLabel(weather.temperatureC),
                            color = theme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                weather.conditionLabel,
                                color = theme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (weather.highC != null && weather.lowC != null) {
                                Text(
                                    "H ${weatherTempLabel(weather.highC)}  ·  L ${weatherTempLabel(weather.lowC)}",
                                    color = theme.onSurface.copy(alpha = 0.55f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (current.status != WeatherStatus.OK) {
                        Text(
                            "Showing last saved weather — connect to refresh.",
                            color = theme.onSurface.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                }
                current.status == WeatherStatus.OFFLINE -> Text(
                    "You're offline — weather will appear once you're back online.",
                    color = theme.onSurface.copy(alpha = 0.65f),
                    fontSize = 12.5.sp
                )
                else -> Text(
                    "Weather unavailable right now — try again shortly.",
                    color = theme.onSurface.copy(alpha = 0.65f),
                    fontSize = 12.5.sp
                )
            }
        }
    }
}
