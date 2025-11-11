package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.security.PinVerificationStatus

/**
 * Banner shown in chat screen during PIN verification
 * Shows different states: waiting for initiator, waiting for responder, or blocked
 */
@Composable
fun PinVerificationBanner(
    status: PinVerificationStatus,
    peerNickname: String,
    modifier: Modifier = Modifier
) {
    val (message, icon) = when (status) {
        PinVerificationStatus.PENDING_INITIATOR -> {
            "Waiting for $peerNickname to enter PIN..." to "⏳"
        }
        PinVerificationStatus.PENDING_RESPONDER -> {
            "Enter PIN to start chat with $peerNickname" to "🔒"
        }
        PinVerificationStatus.BLOCKED -> {
            "Verification blocked" to "⛔"
        }
        else -> return // Don't show banner for other states
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )

            // Message
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "PIN Verification Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            // Loading indicator for pending states
            if (status == PinVerificationStatus.PENDING_INITIATOR) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Simplified banner for chat input area
 */
@Composable
fun PinVerificationInputBlocker(
    status: PinVerificationStatus,
    modifier: Modifier = Modifier
) {
    if (status != PinVerificationStatus.VERIFIED && status != PinVerificationStatus.NOT_REQUIRED) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Complete PIN verification to send messages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
