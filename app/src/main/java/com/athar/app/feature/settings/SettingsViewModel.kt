package com.athar.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.R
import com.athar.app.core.backup.BackupCodec
import com.athar.app.core.backup.BackupData
import com.athar.app.core.backup.BackupException
import com.athar.app.core.backup.BackupManager
import com.athar.app.core.data.AppSettings
import com.athar.app.core.data.appContainer
import com.athar.app.core.data.atharApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatusMessage(@StringRes val text: Int, val arg: String = "", val isError: Boolean = false)

/** A backup file that was opened and is waiting for a password and/or confirmation. */
data class PendingRestore(val raw: String, val data: BackupData?) {
    val needsPassword: Boolean get() = data == null
}

data class SettingsUiState(
    val debtsLock: Boolean = false,
    val working: Boolean = false,
    val status: StatusMessage? = null,
    val pending: PendingRestore? = null,
)

class SettingsViewModel(
    private val backup: BackupManager,
    private val settings: AppSettings,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(local, settings.debtsLock) { s, lock -> s.copy(debtsLock = lock) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDebtsLock(enabled: Boolean) = settings.setDebtsLock(enabled)

    fun export(uri: Uri, password: String) = work {
        val data = backup.snapshot()
        val text = BackupCodec.encode(data, password.ifEmpty { null })
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("Cannot open file")
        }
        StatusMessage(R.string.backup_saved, data.itemCount.toString())
    }

    fun open(uri: Uri) = work {
        val raw = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Cannot open file")
        }
        val data = if (BackupCodec.isEncrypted(raw)) null else BackupCodec.decode(raw)
        local.update { it.copy(pending = PendingRestore(raw, data)) }
        null
    }

    fun unlockPending(password: String) = work {
        val pending = local.value.pending ?: return@work null
        val data = withContext(Dispatchers.Default) { BackupCodec.decode(pending.raw, password) }
        local.update { it.copy(pending = pending.copy(data = data)) }
        null
    }

    fun confirmRestore() = work {
        val data = local.value.pending?.data ?: return@work null
        backup.restore(data)
        local.update { it.copy(pending = null) }
        StatusMessage(R.string.backup_restored, data.itemCount.toString())
    }

    fun cancelRestore() = local.update { it.copy(pending = null) }

    fun clearStatus() = local.update { it.copy(status = null) }

    /** Runs [block] with a busy flag; maps failures to a readable message. */
    private fun work(block: suspend () -> StatusMessage?) {
        viewModelScope.launch {
            local.update { it.copy(working = true, status = null) }
            val status = try {
                block()
            } catch (e: BackupException.WrongPassword) {
                StatusMessage(R.string.backup_wrong_password, isError = true)
            } catch (e: BackupException.PasswordRequired) {
                StatusMessage(R.string.backup_wrong_password, isError = true)
            } catch (e: BackupException.NewerVersion) {
                StatusMessage(R.string.backup_newer_version, isError = true)
            } catch (e: BackupException.InvalidFile) {
                StatusMessage(R.string.backup_invalid_file, isError = true)
            } catch (e: Exception) {
                StatusMessage(R.string.backup_failed, e.message.orEmpty(), isError = true)
            }
            local.update { it.copy(working = false, status = status) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                SettingsViewModel(c.backupManager, c.settings, atharApplication().contentResolver)
            }
        }
    }
}
