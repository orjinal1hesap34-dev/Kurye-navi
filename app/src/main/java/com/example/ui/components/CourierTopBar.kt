package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MapboxConfig

@Composable
fun CourierTopBar(
    isNightMode: Boolean,
    isGpsActive: Boolean,
    isSimulating: Boolean,
    hasRoute: Boolean,
    onOpenSearch: () -> Unit,
    onToggleNightMode: () -> Unit,
    onOpenMapboxInfo: () -> Unit,
    onToggleSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Upper row: Branding, Mapbox status, Theme & Sim toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Title & Courier Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF59E0B), // Vibrant Amber
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "KN",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Kurye Navigasyon",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isNightMode) Color.White else Color(0xFF0F172A)
                    )
                    Text(
                        text = "Bina & Dış Kapı No Destekli",
                        fontSize = 11.sp,
                        color = Color(0xFF10B981), // Emerald green
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Mapbox Key status badge button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (MapboxConfig.isConfigured) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF3B82F6).copy(alpha = 0.15f),
                    modifier = Modifier
                        .clickable { onOpenMapboxInfo() }
                        .padding(end = 6.dp)
                        .testTag("mapbox_info_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (MapboxConfig.isConfigured) Color(0xFF10B981) else Color(0xFF3B82F6),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (MapboxConfig.isConfigured) "Mapbox" else "OSM+Bina",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (MapboxConfig.isConfigured) Color(0xFF059669) else Color(0xFF2563EB)
                        )
                    }
                }

                // Simulation mode toggle if route exists
                if (hasRoute) {
                    IconButton(
                        onClick = onToggleSimulation,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("simulation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricScooter,
                            contentDescription = "Simülasyon Sürüşü",
                            tint = if (isSimulating) Color(0xFFF59E0B) else Color(0xFF64748B)
                        )
                    }
                }

                // Day / Night Theme Toggle
                IconButton(
                    onClick = onToggleNightMode,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Gece / Gündüz Teması",
                        tint = if (isNightMode) Color(0xFFF59E0B) else Color(0xFF0F172A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.padding(top = 6.dp))

        // Search Bar Trigger
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSearch() }
                .testTag("search_bar_trigger"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNightMode) Color(0xFF1E293B) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Adres Ara",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Adres, cadde veya kapı numarası ara...",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.weight(1f)
                )
                // GPS & Simulation indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = when {
                        isSimulating -> "Simülasyon"
                        isGpsActive -> "GPS Aktif"
                        else -> "GPS Aranıyor"
                    }
                    val statusColor = when {
                        isSimulating -> Color(0xFFF59E0B)
                        isGpsActive -> Color(0xFF10B981)
                        else -> Color(0xFF94A3B8)
                    }
                    Icon(
                        imageVector = if (isSimulating) Icons.Default.ElectricScooter else Icons.Default.LocationOn,
                        contentDescription = statusText,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}
