package com.saudi.salariesapp.data

import java.time.LocalDate

/** يجمع كل مصادر المواعيد (الخدمات، المناسبات، العدادات الشخصية) في قائمة واحدة. */
class EventsRepository(val prefs: AppPreferences) {

    /** الموعد القادم لكل خدمة. */
    fun nextServiceEvents(today: LocalDate = LocalDate.now()): List<EventModel> =
        PaymentSchedule.services.map { s ->
            PaymentSchedule.toEvent(s, PaymentSchedule.nextPayment(today, prefs.paymentDay(s)), today)
        }

    /** جدول الأشهر القادمة لخدمة معيّنة. */
    fun scheduleFor(service: PaymentService, months: Int = 6, today: LocalDate = LocalDate.now()) =
        PaymentSchedule.upcomingPayments(today, prefs.paymentDay(service), months)
            .map { PaymentSchedule.toEvent(service, it, today) }

    fun customEvents(today: LocalDate = LocalDate.now()): List<EventModel> =
        prefs.loadCountdowns().map { Countdown.toEvent(it, today) }.sortedBy { it.target }

    fun allUpcoming(today: LocalDate = LocalDate.now()): List<EventModel> =
        (nextServiceEvents(today) + Occasions.upcoming(today) + customEvents(today))
            .filter { it.remainingDays >= 0 }
            .sortedBy { it.target }

    /**
     * ما يعرضه الودجت: العناصر المثبّتة (خدمة بمعرّفها أو عداد شخصي بمعرّفه)،
     * وإلا أقرب المواعيد القادمة.
     */
    fun widgetEvents(today: LocalDate = LocalDate.now()): List<EventModel> {
        val pinned = prefs.widgetPinned()
        val all = allUpcoming(today)
        if (pinned.isEmpty()) return all
        return all.filter { e -> pinned.any { p -> e.id == p || e.id.startsWith("${p}_") } }
            .ifEmpty { all }
    }
}
