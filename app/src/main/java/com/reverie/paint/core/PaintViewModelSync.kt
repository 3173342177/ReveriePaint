/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.reverie.paint.R
import com.reverie.paint.core.sync.SyncCategory
import com.reverie.paint.core.sync.SyncCredentialStore
import com.reverie.paint.core.sync.SyncCredentials
import com.reverie.paint.core.sync.SyncEngine
import com.reverie.paint.core.sync.SyncException
import com.reverie.paint.core.sync.SyncHttp
import com.reverie.paint.core.sync.SyncSource
import com.reverie.paint.core.sync.WebDavSyncClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class SyncConnectionStatus { IDLE, TESTING, OK, FAILED }

internal enum class SyncBackupStatus { IDLE, RUNNING, DONE, FAILED, WAITING_WIFI }

internal enum class SyncRestoreStatus { IDLE, RUNNING, DONE, FAILED }

internal class SyncState {
    var serverUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var hasSavedPassword by mutableStateOf(false)
    var connected by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var autoBackupEnabled by mutableStateOf(false)
    var wifiOnly by mutableStateOf(true)
    var syncOnExitEnabled by mutableStateOf(false)
    var periodicBackupEnabled by mutableStateOf(false)
    var trustSelfSigned by mutableStateOf(false)

    internal var syncTimerJob: Job? = null
    internal var lastPeriodicBackupMs: Long = 0L

    var lastBackupAtMs by mutableLongStateOf(0L)
    var status by mutableStateOf(SyncConnectionStatus.IDLE)

    var statusDetail by mutableStateOf("")

    var backupStatus by mutableStateOf(SyncBackupStatus.IDLE)
    var backupDone by mutableIntStateOf(0)
    var backupTotal by mutableIntStateOf(0)
    var lastBackupUploaded by mutableIntStateOf(0)
    var lastBackupSkipped by mutableIntStateOf(0)
    var lastBackupDeleted by mutableIntStateOf(0)
    var lastBackupRemoteChanged by mutableIntStateOf(0)
    var lastBackupFailed by mutableIntStateOf(0)
    var lastBackupBytes by mutableLongStateOf(0L)
    var lastBackupError by mutableStateOf("")

    var restoreStatus by mutableStateOf(SyncRestoreStatus.IDLE)
    var restoreCategory by mutableStateOf<SyncCategory?>(null)
    var restoreDone by mutableIntStateOf(0)
    var restoreTotal by mutableIntStateOf(0)
    var lastRestoreDownloaded by mutableIntStateOf(0)
    var lastRestoreUpdated by mutableIntStateOf(0)
    var lastRestoreSkipped by mutableIntStateOf(0)
    var lastRestoreConflicts by mutableIntStateOf(0)
    var lastRestoreFailed by mutableIntStateOf(0)
    var lastRestoreError by mutableStateOf("")
}

private const val PREF_SERVER_URL = "sync_server_url"
private const val PREF_USERNAME = "sync_username"
private const val PREF_AUTO_BACKUP = "sync_auto_backup"
private const val PREF_WIFI_ONLY = "sync_wifi_only"
private const val PREF_SYNC_ON_EXIT = "sync_on_exit"
private const val PREF_PERIODIC_BACKUP = "sync_periodic_backup"
private const val PREF_TRUST_SELF_SIGNED = "sync_trust_self_signed"
private const val SYNC_TICK_MS = 60_000L
private const val SYNC_INTERVAL_MS = 30 * 60_000L
private const val PREF_LAST_BACKUP = "sync_last_backup"
private const val PREF_SYNC = "paint_prefs"
private const val MANIFEST_FILE = "sync_manifest.txt"

internal const val SYNC_DETAIL_NOT_CONFIGURED = "NOT_CONFIGURED"

private fun PaintViewModel.syncPrefs() =
    appContext.getSharedPreferences(PREF_SYNC, android.content.Context.MODE_PRIVATE)

private fun PaintViewModel.syncManifestFile() = File(appContext.filesDir, MANIFEST_FILE)

private fun PaintViewModel.savedPassword(): String =
    if (hasAppContext()) SyncCredentialStore.loadPassword(appContext) else ""

private fun PaintViewModel.bundledNames(assetDir: String): Set<String> =
    try {
        appContext.assets.list(assetDir)?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

private fun PaintViewModel.syncSources(): List<SyncSource> {
    val filesDir = appContext.filesDir
    val bundledPresets = bundledNames("paintoppresets")
    val bundledTips = bundledNames("brushes")
    return listOf(
        SyncSource(SyncCategory.ARTWORKS, File(filesDir, "projects"), "") { SyncEngine.isSyncable(it) },
        SyncSource(SyncCategory.ARTWORKS, File(filesDir, "autosave"), "autosave/") {
            SyncEngine.isPlainFile(it) && it.endsWith(".revp", ignoreCase = true)
        },
        SyncSource(SyncCategory.BRUSHES, File(filesDir, "paintoppresets"), "brushes/presets/") {
            SyncEngine.isPlainFile(it) && it !in bundledPresets
        },
        SyncSource(SyncCategory.BRUSHES, File(filesDir, "brushes"), "brushes/tips/") {
            SyncEngine.isPlainFile(it) && it !in bundledTips
        },
    )
}

internal fun PaintViewModel.loadSyncSettings() {
    if (!hasAppContext()) return
    val prefs = syncPrefs()
    syncState.serverUrl = prefs.getString(PREF_SERVER_URL, "") ?: ""
    syncState.username = prefs.getString(PREF_USERNAME, "") ?: ""
    syncState.autoBackupEnabled = prefs.getBoolean(PREF_AUTO_BACKUP, false)
    syncState.wifiOnly = prefs.getBoolean(PREF_WIFI_ONLY, true)
    syncState.syncOnExitEnabled = prefs.getBoolean(PREF_SYNC_ON_EXIT, false)
    syncState.periodicBackupEnabled = prefs.getBoolean(PREF_PERIODIC_BACKUP, false)
    syncState.trustSelfSigned = prefs.getBoolean(PREF_TRUST_SELF_SIGNED, false)
    startSyncTimerIfNeeded()
    syncState.lastBackupAtMs = prefs.getLong(PREF_LAST_BACKUP, 0L)
    syncState.hasSavedPassword = savedPassword().isNotEmpty()
    syncState.connected = syncState.serverUrl.isNotBlank() && syncState.username.isNotBlank()
    syncState.editing = false
    syncState.status = SyncConnectionStatus.IDLE
    syncState.statusDetail = ""
    syncState.backupStatus = SyncBackupStatus.IDLE
}

internal fun PaintViewModel.openSyncEditor() {
    syncState.editing = true
    syncState.status = SyncConnectionStatus.IDLE
    syncState.statusDetail = ""
}

internal fun PaintViewModel.closeSyncEditor() {
    syncState.editing = false
    syncState.status = SyncConnectionStatus.IDLE
    syncState.statusDetail = ""
}

internal fun PaintViewModel.setSyncAutoBackupEnabled(value: Boolean) {
    syncState.autoBackupEnabled = value
    persistSyncSettings()
}

internal fun PaintViewModel.setSyncWifiOnly(value: Boolean) {
    syncState.wifiOnly = value
    persistSyncSettings()
}

internal fun PaintViewModel.setSyncOnExitEnabled(value: Boolean) {
    syncState.syncOnExitEnabled = value
    persistSyncSettings()
}

internal fun PaintViewModel.setSyncTrustSelfSigned(value: Boolean) {
    syncState.trustSelfSigned = value
    persistSyncSettings()
}

internal fun PaintViewModel.setSyncPeriodicBackup(value: Boolean) {
    syncState.periodicBackupEnabled = value
    syncState.lastPeriodicBackupMs = System.currentTimeMillis()
    persistSyncSettings()
    startSyncTimerIfNeeded()
}

internal fun PaintViewModel.startSyncTimerIfNeeded() {
    if (!syncState.periodicBackupEnabled) {
        stopSyncTimer()
        return
    }
    if (syncState.syncTimerJob?.isActive == true) return
    syncState.syncTimerJob =
        viewModelScope.launch {
            while (isActive) {
                delay(SYNC_TICK_MS)
                maybePeriodicBackup()
            }
        }
}

internal fun PaintViewModel.stopSyncTimer() {
    syncState.syncTimerJob?.cancel()
    syncState.syncTimerJob = null
}

private fun PaintViewModel.maybePeriodicBackup() {
    if (!syncState.periodicBackupEnabled) return
    if (!isSyncConfigValid()) return
    val now = System.currentTimeMillis()
    if (syncState.lastPeriodicBackupMs == 0L) {
        syncState.lastPeriodicBackupMs = now
        return
    }
    if (now - syncState.lastPeriodicBackupMs < SYNC_INTERVAL_MS) return
    syncState.lastPeriodicBackupMs = now
    triggerAutoBackup()
}

private fun PaintViewModel.syncClient(
    url: String,
    user: String,
    pass: String,
) = WebDavSyncClient(url, user, pass, SyncHttp.client(syncState.trustSelfSigned))

private fun PaintViewModel.isOnWifi(): Boolean {
    if (!hasAppContext()) return false
    val cm =
        appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
}

internal fun PaintViewModel.connectSync(
    serverUrl: String,
    username: String,
    passwordDraft: String,
) {
    if (!SyncCredentials.isValidServerUrl(serverUrl) || username.isBlank()) {
        syncState.status = SyncConnectionStatus.FAILED
        syncState.statusDetail = SYNC_DETAIL_NOT_CONFIGURED
        return
    }
    val normalized = SyncCredentials.normalizeServerUrl(serverUrl)
    val user = username
    val existing = savedPassword()
    val effectivePassword = passwordDraft.ifBlank { existing }
    syncState.status = SyncConnectionStatus.TESTING
    syncState.statusDetail = ""

    viewModelScope.launch {
        val failure =
            withContext(Dispatchers.IO) {
                try {
                    syncClient(normalized, user, effectivePassword).stat("")
                    null
                } catch (e: SyncException) {
                    e.kind.name
                } catch (e: Exception) {
                    e.javaClass.simpleName
                }
            }
        if (failure == null) {
            syncState.serverUrl = normalized
            syncState.username = user
            syncState.connected = true
            syncState.editing = false
            if (hasAppContext()) {
                if (passwordDraft.isNotEmpty()) {
                    SyncCredentialStore.savePassword(appContext, passwordDraft)
                }
                syncState.hasSavedPassword = savedPassword().isNotEmpty()
                persistSyncSettings()
            }
            syncState.status = SyncConnectionStatus.OK
            syncState.statusDetail = ""
        } else {
            syncState.status = SyncConnectionStatus.FAILED
            syncState.statusDetail = failure
        }
    }
}

internal fun PaintViewModel.testSyncConnection() {
    if (!isSyncConfigValid()) {
        syncState.status = SyncConnectionStatus.FAILED
        syncState.statusDetail = SYNC_DETAIL_NOT_CONFIGURED
        return
    }
    val url = SyncCredentials.normalizeServerUrl(syncState.serverUrl)
    val user = syncState.username
    val pass = savedPassword()
    syncState.status = SyncConnectionStatus.TESTING
    syncState.statusDetail = ""
    viewModelScope.launch {
        val failure =
            withContext(Dispatchers.IO) {
                try {
                    syncClient(url, user, pass).stat("")
                    null
                } catch (e: SyncException) {
                    e.kind.name
                } catch (e: Exception) {
                    e.javaClass.simpleName
                }
            }
        syncState.status = if (failure == null) SyncConnectionStatus.OK else SyncConnectionStatus.FAILED
        syncState.statusDetail = failure ?: ""
    }
}

internal fun PaintViewModel.disconnectSync() {
    if (hasAppContext()) {
        SyncCredentialStore.clearPassword(appContext)
        syncPrefs()
            .edit()
            .remove(PREF_SERVER_URL)
            .remove(PREF_USERNAME)
            .remove(PREF_AUTO_BACKUP)
            .remove(PREF_WIFI_ONLY)
            .remove(PREF_SYNC_ON_EXIT)
            .remove(PREF_PERIODIC_BACKUP)
            .remove(PREF_TRUST_SELF_SIGNED)
            .remove(PREF_LAST_BACKUP)
            .apply()
        syncManifestFile().delete()
    }
    stopSyncTimer()
    syncState.serverUrl = ""
    syncState.username = ""
    syncState.hasSavedPassword = false
    syncState.connected = false
    syncState.editing = false
    syncState.autoBackupEnabled = false
    syncState.wifiOnly = true
    syncState.syncOnExitEnabled = false
    syncState.periodicBackupEnabled = false
    syncState.lastBackupAtMs = 0L
    syncState.status = SyncConnectionStatus.IDLE
    syncState.statusDetail = ""
    syncState.backupStatus = SyncBackupStatus.IDLE
    syncState.lastBackupError = ""
    syncState.restoreStatus = SyncRestoreStatus.IDLE
    syncState.lastRestoreError = ""
}

internal fun PaintViewModel.backupToCloud() {
    if (!isSyncConfigValid()) {
        syncState.status = SyncConnectionStatus.FAILED
        syncState.statusDetail = SYNC_DETAIL_NOT_CONFIGURED
        return
    }
    if (!hasAppContext()) return
    val url = SyncCredentials.normalizeServerUrl(syncState.serverUrl)
    val user = syncState.username
    val pass = savedPassword()
    val manifestFile = syncManifestFile()

    syncState.backupStatus = SyncBackupStatus.RUNNING
    syncState.backupDone = 0
    syncState.backupTotal = 0
    syncState.lastBackupError = ""

    viewModelScope.launch {
        val outcome =
            withContext(Dispatchers.IO) {
                try {
                    val client = syncClient(url, user, pass)
                    val manifest =
                        if (manifestFile.exists()) {
                            SyncEngine.decodeManifest(manifestFile.readText())
                        } else {
                            emptyMap()
                        }
                    val sources = syncSources()
                    val local = SyncEngine.scan(sources)
                    val remoteEntries = SyncEngine.listRemoteAll(client, sources)
                    val result =
                        SyncEngine.backup(client, sources, local, manifest, remoteEntries) { done, total ->
                            syncState.backupDone = done
                            syncState.backupTotal = total
                        }
                    try {
                        SyncEngine.writeTextAtomically(manifestFile, SyncEngine.encodeManifest(result.manifest))
                    } catch (e: Exception) {
                        android.util.Log.e("ReverieSync", "写入同步清单失败", e)
                    }
                    result to null
                } catch (e: SyncException) {
                    null to e.kind.name
                } catch (e: Exception) {
                    null to e.javaClass.simpleName
                }
            }
        val result = outcome.first
        if (result == null) {
            syncState.backupStatus = SyncBackupStatus.FAILED
            syncState.lastBackupError = outcome.second ?: ""
        } else {
            syncState.backupStatus = SyncBackupStatus.DONE
            syncState.lastBackupUploaded = result.uploaded
            syncState.lastBackupSkipped = result.skipped
            syncState.lastBackupDeleted = result.deleted
            syncState.lastBackupRemoteChanged = result.remoteChanged
            syncState.lastBackupFailed = result.failed
            syncState.lastBackupBytes = result.bytes
            syncState.lastBackupError = result.errors.firstOrNull() ?: ""
            syncState.lastBackupAtMs = System.currentTimeMillis()
            persistSyncSettings()
        }
    }
}

internal fun PaintViewModel.restoreFromCloud(category: SyncCategory) {
    if (!isSyncConfigValid()) {
        syncState.status = SyncConnectionStatus.FAILED
        syncState.statusDetail = SYNC_DETAIL_NOT_CONFIGURED
        return
    }
    if (!hasAppContext()) return
    val url = SyncCredentials.normalizeServerUrl(syncState.serverUrl)
    val user = syncState.username
    val pass = savedPassword()
    val manifestFile = syncManifestFile()
    val conflictSuffix = getString(R.string.sync_conflict_suffix)

    syncState.restoreStatus = SyncRestoreStatus.RUNNING
    syncState.restoreCategory = category
    syncState.restoreDone = 0
    syncState.restoreTotal = 0
    syncState.lastRestoreError = ""

    viewModelScope.launch {
        val outcome =
            withContext(Dispatchers.IO) {
                try {
                    val client = syncClient(url, user, pass)
                    val manifest =
                        if (manifestFile.exists()) {
                            SyncEngine.decodeManifest(manifestFile.readText())
                        } else {
                            emptyMap()
                        }
                    val sources = syncSources()
                    val local = SyncEngine.scan(sources.filter { it.category == category })
                    val result =
                        SyncEngine.restore(client, sources, category, local, manifest, conflictSuffix) { done, total ->
                            syncState.restoreDone = done
                            syncState.restoreTotal = total
                        }
                    try {
                        SyncEngine.writeTextAtomically(manifestFile, SyncEngine.encodeManifest(result.manifest))
                    } catch (e: Exception) {
                        android.util.Log.e("ReverieSync", "写入同步清单失败", e)
                    }
                    result to null
                } catch (e: SyncException) {
                    null to e.kind.name
                } catch (e: Exception) {
                    null to e.javaClass.simpleName
                }
            }
        val result = outcome.first
        if (result == null) {
            syncState.restoreStatus = SyncRestoreStatus.FAILED
            syncState.lastRestoreError = outcome.second ?: ""
        } else {
            syncState.restoreStatus = SyncRestoreStatus.DONE
            syncState.lastRestoreDownloaded = result.downloaded
            syncState.lastRestoreUpdated = result.updated
            syncState.lastRestoreSkipped = result.skipped
            syncState.lastRestoreConflicts = result.conflicts
            syncState.lastRestoreFailed = result.failed
            syncState.lastRestoreError = result.errors.firstOrNull() ?: ""
            refreshProjects()
        }
    }
}

internal fun PaintViewModel.maybeAutoBackup() {
    if (!syncState.autoBackupEnabled) return
    triggerAutoBackup()
}

internal fun PaintViewModel.maybeExitBackup() {
    if (!syncState.syncOnExitEnabled) return
    triggerAutoBackup()
}

private fun PaintViewModel.triggerAutoBackup() {
    if (syncState.backupStatus == SyncBackupStatus.RUNNING) return
    if (!isSyncConfigValid()) return
    if (syncState.wifiOnly && !isOnWifi()) {
        syncState.backupStatus = SyncBackupStatus.WAITING_WIFI
        return
    }
    backupToCloud()
}

private fun PaintViewModel.isSyncConfigValid(): Boolean =
    SyncCredentials.isValidServerUrl(syncState.serverUrl) && syncState.username.isNotBlank()

private fun PaintViewModel.persistSyncSettings() {
    if (!hasAppContext()) return
    syncPrefs()
        .edit()
        .putString(PREF_SERVER_URL, syncState.serverUrl)
        .putString(PREF_USERNAME, syncState.username)
        .putBoolean(PREF_AUTO_BACKUP, syncState.autoBackupEnabled)
        .putBoolean(PREF_WIFI_ONLY, syncState.wifiOnly)
        .putBoolean(PREF_SYNC_ON_EXIT, syncState.syncOnExitEnabled)
        .putBoolean(PREF_PERIODIC_BACKUP, syncState.periodicBackupEnabled)
        .putBoolean(PREF_TRUST_SELF_SIGNED, syncState.trustSelfSigned)
        .putLong(PREF_LAST_BACKUP, syncState.lastBackupAtMs)
        .apply()
}
