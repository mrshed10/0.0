package com.tvbrowser.aosp.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.tvbrowser.aosp.ui.MainActivity

/**
 * Clean restart of the whole browser (main process + engine processes).
 * Runs in its own ":restart" process: kills the old browser process, then relaunches it.
 * Needed because Gecko reads its engine preferences only at start-up.
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        if (pid > 0) Process.killProcess(pid)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val EXTRA_PID = "pid"

        fun restart(context: Context) {
            val intent = Intent(context, RestartActivity::class.java)
                .putExtra(EXTRA_PID, Process.myPid())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            (context as? Activity)?.finishAffinity()
            Runtime.getRuntime().exit(0)
        }
    }
}
