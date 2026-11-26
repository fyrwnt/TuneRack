package com.gitgud.tunerack.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gitgud.tunerack.R
import com.gitgud.tunerack.data.youtube.UnplayableException
import com.gitgud.tunerack.tuneRack
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Downloads one song as foreground work with a progress notification, so long batches aren't
 * killed in the background. Network failures retry with backoff; anything else marks it failed.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val downloads = applicationContext.tuneRack.downloads
        return try {
            downloads.performDownload(id) { title, progress, waiting ->
                // Android may refuse foreground work while the app is in the background; the
                // download then simply continues as normal background work without a notification.
                runCatching { setForeground(foregroundInfo(title, progress, waiting)) }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnplayableException) {
            // YouTube won't serve this song; retrying won't change that.
            Log.w(LOG_TAG, "Download of $id failed: ${e.reason}", e)
            downloads.markFailed(id, e.reason)
            Result.failure()
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Download of $id failed (attempt ${runAttemptCount + 1})", e)
            if (runAttemptCount < MAX_RETRIES) {
                downloads.markQueued(id)
                Result.retry()
            } else {
                downloads.markFailed(id, e.message?.takeIf { it.startsWith("YouTube is") } ?: "Network problem. Tap to try again.")
                Result.failure()
            }
        } catch (e: IllegalStateException) {
            Log.e(LOG_TAG, "Download of $id failed", e)
            downloads.markFailed(id, if (e.message?.contains("folder") == true) "Choose a download folder first" else "Download failed")
            Result.failure()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Download of $id failed", e)
            downloads.markFailed(id, "Download failed")
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Preparing download", 0f, 0)

    private fun foregroundInfo(title: String, progress: Float, waiting: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress of songs being saved for offline listening"
                },
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Downloading “$title”")
            .setContentText(if (waiting > 0) "$waiting more queued" else "Saving for offline listening")
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_ID = "songId"
        const val TAG = "download"
        private const val MAX_RETRIES = 4
        private const val LOG_TAG = "TuneRackDownload"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 4040
    }
}
