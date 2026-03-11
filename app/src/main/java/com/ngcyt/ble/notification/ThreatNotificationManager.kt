package com.ngcyt.ble.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ngcyt.ble.domain.model.ThreatAssessment
import com.ngcyt.ble.domain.model.ThreatLevel
import com.ngcyt.ble.scanner.BleScanService

class ThreatNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private var notificationId = 1000

    fun onThreatDetected(assessment: ThreatAssessment) {
        if (assessment.threatLevel !in listOf(ThreatLevel.HIGH, ThreatLevel.CRITICAL)) return

        val title = when (assessment.threatLevel) {
            ThreatLevel.CRITICAL -> "CRITICAL Threat Detected"
            ThreatLevel.HIGH -> "HIGH Threat Detected"
            else -> return
        }

        val body = buildString {
            append("Device ${assessment.mac.takeLast(8)}")
            append(" | Score: ${assessment.threatScore}/100")
            append(" | ${assessment.timeBucketsPresent.size} time windows")
            if (assessment.isMacRandomized) {
                append(" | MAC randomization detected")
            }
        }

        val notification = NotificationCompat.Builder(context, BleScanService.CHANNEL_THREAT_ALERT)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$body\n${assessment.reasoning}"
            ))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId++, notification)
    }
}
