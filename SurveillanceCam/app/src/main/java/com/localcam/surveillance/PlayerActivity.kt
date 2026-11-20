package com.localcam.surveillance

import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** مشغل فيديو داخلي بسيط (VideoView) يعمل بدون إنترنت أو تطبيقات خارجية. */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "path"
    }

    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null || !File(path).exists()) {
            Toast.makeText(this, "الملف غير موجود", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        videoView = findViewById(R.id.videoView)
        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setVideoPath(path)
        videoView.setOnPreparedListener {
            videoView.start()
            controller.show(3_000)
        }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "تعذر تشغيل المقطع (قد يكون تالفاً)", Toast.LENGTH_LONG).show()
            true
        }
    }

    override fun onPause() {
        super.onPause()
        if (::videoView.isInitialized) videoView.pause()
    }
}
