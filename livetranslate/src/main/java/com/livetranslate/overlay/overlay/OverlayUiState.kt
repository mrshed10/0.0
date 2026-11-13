package com.livetranslate.overlay.overlay

enum class OverlayMode {
    /** فقاعة صغيرة: "ترجمة الشاشة". */
    LAUNCHER,

    /** بطاقة اختيار اللغتين + زر "مرّر وترجم" (الإطار المضيء ظاهر). */
    PICKER,

    /** فقاعة "جارٍ الترجمة إلى…" مع ×. */
    TRANSLATING,
}

sealed interface EngineStatus {
    data object Ready : EngineStatus
    data object Working : EngineStatus
    data class Downloading(val language: String) : EngineStatus
    data class Error(val message: String) : EngineStatus
    data object CaptureDenied : EngineStatus
}

data class OverlayUiState(
    val mode: OverlayMode = OverlayMode.LAUNCHER,
    val source: String,
    val target: String,
    val status: EngineStatus = EngineStatus.Ready,
)

interface OverlayActions {
    fun openPicker()
    fun closeOverlay()
    fun startTranslating()
    fun stopTranslating()
    fun selectSource(code: String)
    fun selectTarget(code: String)
    fun swapLanguages()
}
