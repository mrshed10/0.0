package com.deepsearch.hub.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/** قناة بسيطة بين الشاشات: رابط بانتظار فتحه في المتصفح الداخلي. */
class HubViewModel : ViewModel() {
    val pendingUrl = MutableStateFlow<String?>(null)
}
