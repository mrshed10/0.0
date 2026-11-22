package com.tvtools.floatingmenu

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.tvtools.floatingmenu.data.Prefs
import com.tvtools.floatingmenu.system.OverlayPermission

/** نشاط بلا واجهة: يفتح القائمة العائمة مباشرة (أيقونة "فتح القائمة العائمة"). */
class QuickMenuActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OverlayPermission.isGranted(this)) {
            Prefs(this).serviceEnabled = true
            OverlayService.start(this, OverlayService.ACTION_SHOW_MENU)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
