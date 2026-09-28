package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RouteStep

@Composable
fun CourierManeuverCard(
    currentStep: RouteStep?,
    distanceToManeuverMeters: Double,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maneuver = currentStep?.maneuverType ?: "depart"
    val modifierStr = currentStep?.modifier
    val icon = getManeuverIcon(maneuver, modifierStr)

    val formattedDistance = if (distanceToManeuverMeters >= 1000) {
        String.format(java.util.Locale.US, "%.1f km", distanceToManeuverMeters / 1000.0)
    } else {
        "${distanceToManeuverMeters.toInt()} m"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("maneuver_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A) // High contrast dark slate background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Maneuver Icon in prominent circle
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Color(0xFF2563EB), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Dönüş Talimatı",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Distance & Street Name
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = formattedDistance,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF38BDF8) // Electric Cyan for outdoor readability
                )
                Text(
                    text = currentStep?.instruction ?: "Rotayı takip edin",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2
                )
            }

            // Audio Mute/Unmute quick toggle
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.testTag("toggle_voice_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Sesi Aç" else "Sesi Kapat",
                    tint = if (isMuted) Color(0xFF94A3B8) else Color(0xFF10B981)
                )
            }
        }
    }
}

private fun getManeuverIcon(type: String, modifier: String?): ImageVector {
    return when {
        type == "arrive" -> Icons.Default.CheckCircle
        type == "roundabout" || type == "rotary" -> Icons.Default.Loop
        modifier?.contains("left") == true -> Icons.Default.TurnLeft
        modifier?.contains("right") == true -> Icons.Default.TurnRight
        else -> Icons.Default.ArrowUpward
    }
}
