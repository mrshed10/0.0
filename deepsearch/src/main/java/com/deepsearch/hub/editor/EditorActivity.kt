package com.deepsearch.hub.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepsearch.hub.R
import com.deepsearch.hub.databinding.ActivityEditorBinding
import com.deepsearch.hub.util.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** محرر صور بسيط: قص، تدوير، قلب، سطوع/تباين/تشبع — قبل البحث العكسي. */
class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INPUT = "input"
        const val EXTRA_OUTPUT = "output"

        fun intent(context: Context, file: File): Intent =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_INPUT, file.absolutePath)
    }

    private lateinit var b: ActivityEditorBinding
    private var bitmap: Bitmap? = null

    private val brightness get() = b.sliderBrightness.value
    private val contrast get() = b.sliderContrast.value / 100f
    private val saturation get() = b.sliderSaturation.value / 100f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        val path = intent.getStringExtra(EXTRA_INPUT)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { path?.let { BitmapFactory.decodeFile(it) } }
            if (bmp == null) finish() else setBitmap(bmp)
        }

        b.btnRotateLeft.setOnClickListener { transform { MediaUtils.rotate(it, -90) } }
        b.btnRotateRight.setOnClickListener { transform { MediaUtils.rotate(it, 90) } }
        b.btnFlip.setOnClickListener { transform { MediaUtils.flipHorizontal(it) } }
        b.btnResetCrop.setOnClickListener { b.cropView.resetCrop() }
        b.btnResetColors.setOnClickListener {
            b.sliderBrightness.value = 0f
            b.sliderContrast.value = 100f
            b.sliderSaturation.value = 100f
        }
        listOf(b.sliderBrightness, b.sliderContrast, b.sliderSaturation).forEach { s ->
            s.addOnChangeListener { _, _, _ -> applyFilter() }
        }
    }

    private fun setBitmap(bmp: Bitmap) {
        bitmap = bmp
        b.cropView.bitmap = bmp
        applyFilter()
    }

    private fun transform(op: (Bitmap) -> Bitmap) {
        val src = bitmap ?: return
        setBitmap(op(src))
    }

    private fun applyFilter() {
        b.cropView.setColorMatrix(ColorAdjust.matrix(brightness, contrast, saturation))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_save) {
            save()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun save() {
        val src = bitmap ?: return
        val rect = b.cropView.cropRectInBitmap()
        val matrix = ColorAdjust.matrix(brightness, contrast, saturation)
        lifecycleScope.launch {
            val out = withContext(Dispatchers.Default) {
                val result = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
                Canvas(result).drawBitmap(src, rect, android.graphics.Rect(0, 0, rect.width(), rect.height()), paint)
                File(MediaUtils.workDir(this@EditorActivity), "edited.jpg").also { MediaUtils.saveJpeg(result, it) }
            }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_OUTPUT, out.absolutePath))
            finish()
        }
    }
}
