package com.deepsearch.hub.dns

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * "VPN" محلي لا يمرر أي حركة مرور سوى DNS:
 * - يعلن خادم DNS وهمياً (10.111.222.2) ويوجّه هذا العنوان فقط إلى واجهة TUN.
 * - كل استعلام DNS من أي تطبيق يصل إلينا، فنرسله مشفّراً عبر DoH ونعيد الرد.
 * - باقي الإنترنت يمر مباشرة من الشبكة الأصلية (لا تبطيء ولا خوادم وسيطة).
 */
class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "DnsVpn"
        const val ACTION_START = "com.deepsearch.hub.dns.START"
        const val ACTION_STOP = "com.deepsearch.hub.dns.STOP"
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        val isRunning: Boolean get() = _running.value

        fun start(ctx: Context) {
            ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var scope: CoroutineScope? = null
    private var loopJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        shutdown()
        val provider = DohProviders.current()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        loopJob = s.launch {
            DohClient.prewarm(provider)
            val builder = Builder()
                .setSession("DeepSearch Secure DNS · ${provider.name}")
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
                .setBlocking(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

            val pfd = try {
                builder.establish()
            } catch (e: Exception) {
                Log.e(TAG, "establish failed", e)
                null
            }
            if (pfd == null) {
                _running.value = false
                stopSelf()
                return@launch
            }
            tun = pfd
            _running.value = true
            pump(s, pfd, provider)
        }
    }

    private fun pump(s: CoroutineScope, pfd: ParcelFileDescriptor, provider: DohProvider) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (s.isActive) {
                val n = input.read(buf)
                if (n <= 0) continue
                val query = IpPacket.parseDnsQuery(buf, n) ?: continue
                s.launch {
                    try {
                        val answer = DohClient.resolveWire(provider, query.payload)
                        val packet = IpPacket.buildResponse(query, answer)
                        synchronized(output) { output.write(packet) }
                    } catch (e: Exception) {
                        Log.w(TAG, "DoH query failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            // يحدث طبيعياً عند إغلاق الواجهة
            Log.d(TAG, "tun loop ended: ${e.message}")
        }
    }

    private fun shutdown() {
        loopJob?.cancel()
        scope?.cancel()
        scope = null
        runCatching { tun?.close() }
        tun = null
        _running.value = false
    }

    override fun onRevoke() {
        shutdown()
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
