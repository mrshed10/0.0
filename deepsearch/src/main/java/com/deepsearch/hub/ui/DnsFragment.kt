package com.deepsearch.hub.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deepsearch.hub.Prefs
import com.deepsearch.hub.R
import com.deepsearch.hub.browser.AdBlocker
import com.deepsearch.hub.databinding.FragmentDnsBinding
import com.deepsearch.hub.dns.DnsVpnService
import com.deepsearch.hub.dns.DohClient
import com.deepsearch.hub.dns.DohProviders
import com.google.android.material.radiobutton.MaterialRadioButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** إعدادات DNS الآمن: اختيار المزوّد، اختباره، وتطبيقه على الجهاز كاملاً أو على المتصفح فقط. */
class DnsFragment : Fragment(R.layout.fragment_dns) {

    private var _b: FragmentDnsBinding? = null
    private val b get() = _b!!
    private var bindingUi = false

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) DnsVpnService.start(requireContext())
        else setVpnSwitch(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentDnsBinding.bind(view)
        buildProviderList()

        b.customUrl.setText(Prefs.customDohUrl)
        b.customUrl.doAfterTextChanged { Prefs.customDohUrl = it.toString().trim() }
        b.btnTest.setOnClickListener { testProvider() }

        b.switchVpn.setOnCheckedChangeListener { _, checked ->
            if (bindingUi) return@setOnCheckedChangeListener
            if (checked) {
                val prepare = VpnService.prepare(requireContext())
                if (prepare != null) vpnPermission.launch(prepare) else DnsVpnService.start(requireContext())
            } else {
                DnsVpnService.stop(requireContext())
            }
        }

        b.switchBrowserDoh.isChecked = Prefs.browserUseDoh
        b.switchBrowserDoh.setOnCheckedChangeListener { _, c -> Prefs.browserUseDoh = c }

        b.btnCopyDot.setOnClickListener {
            DohProviders.current().dotHost?.let { LinkActions.copy(requireContext(), it) }
        }
        b.btnOpenPrivateDns.setOnClickListener { openPrivateDnsSettings() }

        b.btnUpdateAdblock.setOnClickListener { updateAdblockList() }
        refreshProviderInfo()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                DnsVpnService.running.collect { running ->
                    setVpnSwitch(running)
                    b.vpnStatus.text = getString(
                        if (running) R.string.vpn_status_on else R.string.vpn_status_off,
                        DohProviders.current().name,
                    )
                }
            }
        }
    }

    private fun buildProviderList() {
        val group = b.providerGroup
        val current = Prefs.dohProviderId
        val entries = DohProviders.ALL.map { Triple(it.id, it.name, it.note) } +
            Triple(DohProviders.CUSTOM_ID, getString(R.string.custom_provider), getString(R.string.custom_provider_note))
        entries.forEach { (id, name, note) ->
            val rb = MaterialRadioButton(requireContext()).apply {
                this.id = View.generateViewId()
                tag = id
                text = getString(R.string.provider_fmt, name, note)
                setPadding(paddingLeft, 12, paddingRight, 12)
            }
            group.addView(rb)
            if (id == current) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            val id = g.findViewById<View>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            Prefs.dohProviderId = id
            refreshProviderInfo()
            // إعادة تشغيل VPN بالمزوّد الجديد
            if (DnsVpnService.isRunning) DnsVpnService.start(requireContext())
        }
    }

    private fun refreshProviderInfo() {
        val isCustom = Prefs.dohProviderId == DohProviders.CUSTOM_ID
        b.customLayout.isVisible = isCustom
        val dot = DohProviders.current().dotHost
        b.dotHost.text = dot ?: getString(R.string.dot_not_available)
        b.btnCopyDot.isEnabled = dot != null
        b.testResult.text = ""
    }

    private fun setVpnSwitch(on: Boolean) {
        bindingUi = true
        b.switchVpn.isChecked = on
        bindingUi = false
    }

    private fun testProvider() {
        val p = DohProviders.current()
        b.testResult.text = getString(R.string.testing)
        viewLifecycleOwner.lifecycleScope.launch {
            val text = try {
                val r = withContext(Dispatchers.IO) { DohClient.test(p) }
                getString(R.string.test_ok, p.name, r.millis, r.addresses.joinToString(", "))
            } catch (e: Exception) {
                getString(R.string.test_fail, e.message ?: e.javaClass.simpleName)
            }
            _b?.testResult?.text = text
        }
    }

    /** Private DNS (DNS-over-TLS) المدمج في Android 9+ يطبق التشفير على كل الجهاز بدون VPN. */
    private fun openPrivateDnsSettings() {
        DohProviders.current().dotHost?.let { LinkActions.copy(requireContext(), it) }
        try {
            startActivity(Intent("android.settings.PRIVATE_DNS_SETTINGS"))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        Toast.makeText(requireContext(), R.string.private_dns_hint, Toast.LENGTH_LONG).show()
    }

    private fun updateAdblockList() {
        b.btnUpdateAdblock.isEnabled = false
        b.adblockCount.text = getString(R.string.downloading)
        viewLifecycleOwner.lifecycleScope.launch {
            val msg = try {
                getString(R.string.adblock_count, AdBlocker.updateFromUrl(requireContext().applicationContext))
            } catch (e: Exception) {
                getString(R.string.error_x, e.message)
            }
            _b?.let {
                it.adblockCount.text = msg
                it.btnUpdateAdblock.isEnabled = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        b.adblockCount.text = getString(R.string.adblock_count, AdBlocker.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
