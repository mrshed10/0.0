package com.livetranslate.overlay.overlay

/** حالة محرك الترجمة؛ تظهر في الإشعار فقط (لا توجد أي عناصر عائمة على الشاشة). */
sealed interface EngineStatus {
    data object Ready : EngineStatus
    data object Working : EngineStatus
    data class Downloading(val language: String) : EngineStatus
    data class Error(val message: String) : EngineStatus
}
