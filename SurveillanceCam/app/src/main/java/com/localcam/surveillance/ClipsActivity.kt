package com.localcam.surveillance

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** شاشة المقاطع: من الأحدث للأقدم، مع التشغيل والحذف اليدوي والقفل. */
class ClipsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var adapter: ClipsAdapter
    private lateinit var tvSummary: TextView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clips)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = AppSettings(this)

        tvSummary = findViewById(R.id.tvSummary)
        tvEmpty = findViewById(R.id.tvEmpty)
        adapter = ClipsAdapter(
            onPlay = ::play,
            onToggleLock = ::toggleLock,
            onDelete = ::confirmDelete,
        )
        findViewById<RecyclerView>(R.id.rvClips).apply {
            layoutManager = LinearLayoutManager(this@ClipsActivity)
            adapter = this@ClipsActivity.adapter
        }

        // تحديث تلقائي كلما بدأ مقطع جديد أو حُذف مقطع قديم أثناء التسجيل
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RecordingState.status
                    .distinctUntilChangedBy { it.currentFile to it.isRecording }
                    .collect { refresh() }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        lifecycleScope.launch {
            val recordingName = RecordingState.status.value.let { if (it.isRecording) it.currentFile else null }
            val (items, total) = withContext(Dispatchers.IO) {
                val locked = settings.lockedClips()
                val list = ClipStorage.listClips(this@ClipsActivity).map { f ->
                    ClipItem(f, f.length(), f.name in locked, f.name == recordingName)
                }
                list to list.sumOf { it.sizeBytes }
            }
            adapter.submit(items)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            val used = Formatter.formatShortFileSize(this@ClipsActivity, total)
            val limit = Formatter.formatShortFileSize(this@ClipsActivity, settings.maxStorageBytes)
            val lockedCount = items.count { it.locked }
            tvSummary.text = "${items.size} مقطع  •  المستخدم $used من $limit  •  المقفلة: $lockedCount"
        }
    }

    private fun play(item: ClipItem) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PATH, item.file.absolutePath)
        )
    }

    private fun toggleLock(item: ClipItem) {
        settings.setLocked(item.file.name, !item.locked)
        Toast.makeText(
            this,
            if (item.locked) "أُلغي قفل المقطع" else "تم قفل المقطع — لن يُحذف تلقائياً",
            Toast.LENGTH_SHORT,
        ).show()
        refresh()
    }

    private fun confirmDelete(item: ClipItem) {
        if (item.isRecordingNow) return
        val extra = if (item.locked) "\n\nتنبيه: هذا المقطع مقفل (محمي)." else ""
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف المقطع؟")
            .setMessage("${item.file.name}$extra")
            .setPositiveButton("حذف") { _, _ ->
                if (!ClipStorage.deleteClip(this, item.file)) {
                    Toast.makeText(this, "تعذر الحذف", Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
