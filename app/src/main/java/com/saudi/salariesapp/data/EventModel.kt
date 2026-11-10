package com.saudi.salariesapp.data

import java.time.LocalDateTime

/**
 * نموذج موحّد لأي موعد يُعرض في التطبيق أو الودجت:
 * راتب، تقاعد، حساب المواطن، مناسبة وطنية، أو عداد شخصي أنشأه المستخدم.
 */
data class EventModel(
    val id: String,
    val title: String,          // مثل: راتب نوفمبر، موعد شخصي، إجازة
    val dateHijri: String,      // التاريخ الهجري (أم القرى) منسّقاً للعرض
    val dateGregorian: String,  // التاريخ الميلادي منسّقاً للعرض
    val remainingDays: Int,     // الأيام المتبقية للعداد التنازلي
    val category: EventCategory,
    val colorHex: String,       // اللون المفضل للبطاقة أو الودجت
    val target: LocalDateTime,  // اللحظة الفعلية للموعد (لحساب الساعات والدقائق)
    val isCustom: Boolean = false,
)

enum class EventCategory(val label: String) {
    SALARIES("الرواتب"),
    RETIREMENT("التقاعد والتأمينات"),
    CITIZEN("حساب المواطن"),
    SUPPORT("الضمان والدعم"),
    OCCASIONS("المناسبات"),
    PERSONAL("عداداتي"),
}

/** عداد شخصي يحفظه المستخدم محلياً. */
data class CustomCountdown(
    val id: String,
    val title: String,
    val target: LocalDateTime,
    val colorHex: String,
    /** هل أدخل المستخدم التاريخ بالهجري؟ (للعرض فقط؛ التخزين دائماً ميلادي) */
    val enteredAsHijri: Boolean,
)
