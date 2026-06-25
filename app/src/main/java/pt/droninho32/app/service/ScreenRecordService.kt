package pt.droninho32.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.droninho32.app.R
import java.io.File

/**
 * Serviço em primeiro plano que grava a tela via MediaProjection para um MP4 em
 * Movies/Droninho32 (MediaStore). Cumpre a ordem exigida no Android 14: arranca em
 * primeiro plano (tipo mediaProjection) ANTES de obter a projeção e regista um callback.
 *
 * O consentimento (resultCode + Intent de dados) é obtido pela MainActivity com
 * MediaProjectionManager e passado a este serviço via extras.
 */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null

    private var outputUri: Uri? = null          // API 29+
    private var outputFile: File? = null         // API < 29
    private var pfd: ParcelFileDescriptor? = null

    private val handler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecordingAndSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                }
                startForegroundCompat()
                if (data == null || !startRecording(resultCode, data)) {
                    stopRecordingAndSelf()
                }
            }
            ACTION_STOP -> stopRecordingAndSelf()
            else -> stopRecordingAndSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.rec_channel_name), NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.rec_notif_title))
            .setContentText(getString(R.string.rec_notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startRecording(resultCode: Int, data: Intent): Boolean {
        return try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val mp = mpm.getMediaProjection(resultCode, data) ?: return false
            projection = mp
            // Android 14 exige um callback registado antes de criar o VirtualDisplay.
            mp.registerCallback(projectionCallback, handler)

            val metrics = resources.displayMetrics
            val width = (metrics.widthPixels).let { it - (it % 2) }
            val height = (metrics.heightPixels).let { it - (it % 2) }
            val density = metrics.densityDpi

            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder = rec
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(width, height)
            rec.setVideoEncodingBitRate(8_000_000)
            rec.setVideoFrameRate(30)
            openOutput(rec)
            rec.prepare()

            virtualDisplay = mp.createVirtualDisplay(
                "Droninho32Screen",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface, null, null,
            )
            rec.start()
            _recordingState.value = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Configura o destino do MP4 (MediaStore pendente em 29+, ficheiro público em <29). */
    private fun openOutput(rec: MediaRecorder) {
        val name = "Droninho32_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Droninho32")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert falhou")
            outputUri = uri
            val descriptor = contentResolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("openFileDescriptor falhou")
            pfd = descriptor
            rec.setOutputFile(descriptor.fileDescriptor)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Droninho32")
            dir.mkdirs()
            val file = File(dir, name)
            outputFile = file
            rec.setOutputFile(file.absolutePath)
        }
    }

    private fun finalizeOutput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            outputUri?.let { uri ->
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                runCatching { contentResolver.update(uri, values, null, null) }
            }
        } else {
            outputFile?.let { f ->
                // Indexa no MediaStore para aparecer na galeria.
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        @Suppress("DEPRECATION")
                        put(MediaStore.Video.Media.DATA, f.absolutePath)
                    }
                    contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                }
            }
        }
    }

    private fun stopRecordingAndSelf() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        runCatching { pfd?.close() }
        pfd = null
        if (_recordingState.value) finalizeOutput()
        outputUri = null
        outputFile = null
        _recordingState.value = false

        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_recordingState.value) stopRecordingAndSelf()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "pt.droninho32.app.action.START_REC"
        const val ACTION_STOP = "pt.droninho32.app.action.STOP_REC"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"

        private const val NOTIF_ID = 4201
        private const val CHANNEL_ID = "screen_rec"

        private val _recordingState = MutableStateFlow(false)
        /** Estado real da gravação; observado pela MainActivity para refletir no ViewModel. */
        val recordingState: StateFlow<Boolean> = _recordingState.asStateFlow()
    }
}
