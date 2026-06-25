package pt.droninho32.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Captura de ecrã da própria app (sem MediaProjection): desenha a árvore de vistas
 * atual num Bitmap com [View.draw] e guarda um PNG via MediaStore em Pictures/Droninho32.
 */
object ScreenCapture {

    /** Captura [view] (tipicamente a root view da janela) e guarda. Devolve true se gravou. */
    suspend fun captureView(context: Context, view: View): Boolean {
        val bitmap = withContext(Dispatchers.Main) {
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            bmp
        }
        return withContext(Dispatchers.IO) { save(context, bitmap) }
    }

    private fun save(context: Context, bitmap: Bitmap): Boolean {
        val name = "Droninho32_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Droninho32")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }
}
