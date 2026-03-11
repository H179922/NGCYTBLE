package com.ngcyt.ble.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered by AlarmManager to rotate time buckets
 * even when the device is in Doze mode.
 */
class BucketRotationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ROTATE_BUCKET = "com.ngcyt.ble.ROTATE_BUCKET"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_ROTATE_BUCKET) {
            BleScanService.onBucketRotationAlarm()
        }
    }
}
