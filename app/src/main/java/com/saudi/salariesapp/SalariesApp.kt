package com.saudi.salariesapp

import android.app.Application
import com.saudi.salariesapp.reminders.ReminderScheduler

class SalariesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        ReminderScheduler.rescheduleAll(this)
    }
}
