package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.BuildingInfo
import com.example.data.model.MultiStopRoutePlan
import com.example.data.model.NavLocation
import com.example.data.model.NavigationState
import com.example.data.model.RouteModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CourierBottomPanel(
    navigationState: NavigationState,
    destination: NavLocation?,
    destinationTitle: String,
    targetBuilding: BuildingInfo?,
    currentRoute: RouteModel?,
    currentLocation: NavLocation,
    remainingDistanceMeters: Double,
    remainingDurationSeconds: Double,
    isLoadingRoute: Boolean,
    routeError: String?,
    isCameraLocked: Boolean,
    isNightMode: Boolean,
    onCreateRoute: () -> Unit,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onRecenterCamera: () -> Unit,
    onClearRouteError: () -> Unit,
    modifier: Modifier = Modifier,
    multiStopPlan: MultiStopRoutePlan? = null,
    onCompleteCurrentStop: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // Floating Re-Center Button when user moves camera manually
        if (!isCameraLocked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FloatingActionButton(
                    onClick = onRecenterCamera,
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("recenter_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Konumuma Dön",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Main Panel Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNightMode) Color(0xFF0F172A) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (navigationState) {
                    NavigationState.NAVIGATING -> {
                        // Live Navigation metrics
                        NavigatingContent(
                            destinationTitle = destinationTitle,
                            houseNumber = targetBuilding?.houseNumber,
                            hasEntrance = targetBuilding?.entranceLocation != null,
                            remainingDistanceMeters = remainingDistanceMeters,
                            remainingDurationSeconds = remainingDurationSeconds,
                            speedKmh = currentLocation.speedKmh,
                            isNightMode = isNightMode,
                            onStopNavigation = onStopNavigation
                        )
                    }
                    NavigationState.ARRIVED -> {
                        // Arrived View
                        ArrivedContent(
                            destinationTitle = destinationTitle,
                            houseNumber = targetBuilding?.houseNumber,
                            isNightMode = isNightMode,
                            onClose = onStopNavigation,
                            multiStopPlan = multiStopPlan,
                            onCompleteCurrentStop = onCompleteCurrentStop
                        )
                    }
                    else -> {
                        // Idle or Destination Selected
                        DestinationSelectionContent(
                            destination = destination,
                            destinationTitle = destinationTitle,
                            targetBuilding = targetBuilding,
                            currentRoute = currentRoute,
                            isLoadingRoute = isLoadingRoute,
                            routeError = routeError,
                            isNightMode = isNightMode,
                            onCreateRoute = onCreateRoute,
                            onStartNavigation = onStartNavigation,
                            onClearRouteError = onClearRouteError
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationSelectionContent(
    destination: NavLocation?,
    destinationTitle: String,
    targetBuilding: BuildingInfo?,
    currentRoute: RouteModel?,
    isLoadingRoute: Boolean,
    routeError: String?,
    isNightMode: Boolean,
    onCreateRoute: () -> Unit,
    onStartNavigation: () -> Unit,
    onClearRouteError: () -> Unit
) {
    if (destination == null) {
        // No destination selected yet
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.2f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PinDrop,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Hedef Seçilmedi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isNightMode) Color.White else Color(0xFF0F172A)
                )
                Text(
                    text = "Arama yapın veya haritaya basılı tutarak hedef belirleyin",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    } else {
        // Destination selected
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFF2563EB).copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PinDrop,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destinationTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (isNightMode) Color.White else Color(0xFF0F172A),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Door number and entrance badges
                    val houseNum = targetBuilding?.houseNumber
                    if (!houseNum.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "DIŞ KAPI NO: $houseNum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (targetBuilding?.entranceLocation != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MeetingRoom,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Giriş Noktası",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Kayıtlı dış kapı numarası bulunamadı",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Route details if created
                    if (currentRoute != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mesafe: ${currentRoute.formattedDistance} • Süre: ${currentRoute.formattedDuration}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }

            // Route error message banner if failed
            if (!routeError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = routeError,
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearRouteError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: "Rotayı Oluştur" & "Navigasyonu Başlat"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateRoute,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_route_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoadingRoute) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Rotayı Oluştur", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onStartNavigation,
                    enabled = currentRoute != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("start_navigation_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981) // Emerald Green
                    )
                ) {
                    Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Navigasyonu Başlat", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NavigatingContent(
    destinationTitle: String,
    houseNumber: String?,
    hasEntrance: Boolean,
    remainingDistanceMeters: Double,
    remainingDurationSeconds: Double,
    speedKmh: Float,
    isNightMode: Boolean,
    onStopNavigation: () -> Unit
) {
    val formattedDist = if (remainingDistanceMeters >= 1000) {
        String.format(Locale.US, "%.1f km", remainingDistanceMeters / 1000.0)
    } else {
        "${remainingDistanceMeters.toInt()} m"
    }

    val minutes = (remainingDurationSeconds / 60).toInt()
    val formattedTime = if (minutes >= 60) {
        "${minutes / 60} sa ${minutes % 60} dk"
    } else {
        "${maxOf(1, minutes)} dk"
    }

    val etaCalendar = Date(System.currentTimeMillis() + (remainingDurationSeconds * 1000).toLong())
    val etaString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(etaCalendar)

    Column {
        // Target Title & Door number badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destinationTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isNightMode) Color.White else Color(0xFF0F172A),
                    maxLines = 1
                )
                if (!houseNumber.isNullOrBlank()) {
                    Text(
                        text = "Kapı No: $houseNumber ${if (hasEntrance) "• Giriş İşaretli" else ""}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF59E0B)
                    )
                }
            }

            // Stop navigation button
            Button(
                onClick = onStopNavigation,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("stop_navigation_button")
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Bitir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large 4-Metric Grid for Couriers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricItem(label = "Kalan", value = formattedDist, valueColor = Color(0xFF2563EB))
            MetricItem(label = "Süre", value = formattedTime, valueColor = Color(0xFF10B981))
            MetricItem(label = "Varış", value = etaString, valueColor = if (isNightMode) Color.White else Color(0xFF0F172A))
            MetricItem(label = "Hız", value = "${speedKmh.toInt()} km/s", valueColor = Color(0xFFF59E0B))
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = valueColor)
    }
}

@Composable
private fun ArrivedContent(
    destinationTitle: String,
    houseNumber: String?,
    isNightMode: Boolean,
    onClose: () -> Unit,
    multiStopPlan: MultiStopRoutePlan? = null,
    onCompleteCurrentStop: (() -> Unit)? = null
) {
    val remainingStops = multiStopPlan?.remainingPoints ?: emptyList()
    val hasMoreStops = remainingStops.size > 1

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFF10B981), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MeetingRoom,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "HEDEFE ULAŞTINIZ!",
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF10B981)
        )
        if (!houseNumber.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF59E0B),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "DIŞ KAPI NUMARASI: $houseNumber",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            text = destinationTitle,
            fontSize = 14.sp,
            color = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF475569),
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (hasMoreStops && onCompleteCurrentStop != null) {
            Button(
                onClick = onCompleteCurrentStop,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(text = "Teslim Edildi & Sıradaki Noktaya Geç (${remainingStops.size - 1} Kaldı)", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClose,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(text = "Rotayı Sonlandır")
            }
        } else {
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text(text = "Teslimatı Tamamla", fontWeight = FontWeight.Bold)
            }
        }
    }
}
