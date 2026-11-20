package com.localcam.surveillance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** حالة التسجيل الحالية، تنشرها الخدمة وتراقبها الشاشات. */
data class RecorderStatus(
    val isRecording: Boolean = false,
    val currentFile: String? = null,
    val recordingStartedAt: Long = 0L,
    val segmentStartedAt: Long = 0L,
    val segmentMs: Long = 0L,
    val segmentsSaved: Int = 0,
    val thermalWarning: String? = null,
    val message: String? = null,
)

object RecordingState {
    private val _status = MutableStateFlow(RecorderStatus())
    val status: StateFlow<RecorderStatus> = _status

    fun update(block: (RecorderStatus) -> RecorderStatus) = _status.update(block)
}
