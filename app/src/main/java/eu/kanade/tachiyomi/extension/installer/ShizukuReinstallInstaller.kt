package eu.kanade.tachiyomi.extension.installer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.shizuku.IShellInterface
import mihon.app.shizuku.ShellInterface
import rikka.shizuku.Shizuku
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

class ShizukuReinstallInstaller(private val service: Service) : Installer(service) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var shellInterface: IShellInterface? = null

    private val shizukuArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(service, ShellInterface::class.java),
        )
            .tag("shizuku_service")
            .processNameSuffix("shizuku_service")
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellInterface = IShellInterface.Stub.asInterface(service)
            ready = true
            checkQueue()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellInterface = null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

            if (status == PackageInstaller.STATUS_SUCCESS) {
                continueQueue(InstallStep.Installed)
            } else {
                logcat(LogPriority.ERROR) { "Failed to install extension $packageName: $message" }
                continueQueue(InstallStep.Error)
            }
        }
    }

    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        logcat { "Shizuku was killed prematurely" }
        service.stopSelf()
    }

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    checkQueue()
                    Shizuku.bindUserService(shizukuArgs, connection)
                } else {
                    service.stopSelf()
                }
                Shizuku.removeRequestPermissionResultListener(this)
            }
        }
    }

    fun initShizuku() {
        if (ready) return
        if (!Shizuku.pingBinder()) {
            logcat(LogPriority.ERROR) { "Shizuku is not ready to use" }
            service.toast(MR.strings.ext_installer_shizuku_stopped)
            service.stopSelf()
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Shizuku.bindUserService(shizukuArgs, connection)
        } else {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    override var ready = false

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        // Launch installation in coroutine to handle retry logic
        scope.launch {
            installWithRetry(entry)
        }
    }

    private suspend fun installWithRetry(entry: Entry) {
        val result = try {
            // First attempt: normal install
            val firstAttempt = performInstall(entry)
            if (firstAttempt) {
                InstallStep.Installed
            } else {
                // Check if it's a signature mismatch
                withContext(Dispatchers.Main) {
                    // Extract package name from filename
                    val packageName = extractPackageName(entry.uri.toString())
                    if (packageName != null) {
                        logcat { "Signature mismatch detected for $packageName, attempting uninstall and reinstall" }
                        // Uninstall the package first
                        val uninstallSuccess = uninstallPackage(packageName)
                        if (uninstallSuccess) {
                            logcat { "Successfully uninstalled $packageName, retrying install" }
                            // Retry installation after uninstall
                            val retrySuccess = performInstall(entry)
                            if (retrySuccess) {
                                InstallStep.Installed
                            } else {
                                InstallStep.Error
                            }
                        } else {
                            logcat(LogPriority.ERROR) { "Failed to uninstall $packageName" }
                            InstallStep.Error
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Could not determine package name from ${entry.uri}" }
                        InstallStep.Error
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId}" }
            InstallStep.Error
        }
        withContext(Dispatchers.Main) {
            continueQueue(result)
        }
    }

    private suspend fun performInstall(entry: Entry): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                service.contentResolver.openAssetFileDescriptor(entry.uri, "r").use { fd ->
                    shellInterface?.install(fd)
                }
                true
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Install attempt failed" }
                false
            }
        }
    }

    private suspend fun uninstallPackage(packageName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Use pm uninstall command through shell interface
                val result = shellInterface?.runCommand("pm uninstall $packageName")
                result?.contains("Success") == true
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to uninstall $packageName" }
                false
            }
        }
    }

    private fun extractPackageName(uriString: String): String? {
        // Extract package name from the APK filename
        // Common pattern: extension-name-v1.2.3.apk -> package name might be in the format
        return uriString.substringAfterLast('/')
            .substringBeforeLast('-')
            .replace('-', '.')
            .takeIf { it.isNotBlank() }
    }

    // Don't cancel if entry is already started installing
    override fun cancelEntry(entry: Entry): Boolean = getActiveEntry() != entry

    override fun onDestroy() {
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(shizukuArgs, connection, true)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to unbind shizuku service" }
            }
        }
        service.unregisterReceiver(receiver)
        logcat { "ShizukuReinstallInstaller destroy" }
        scope.cancel()
        super.onDestroy()
    }

    init {
        Shizuku.addBinderDeadListener(shizukuDeadListener)

        ContextCompat.registerReceiver(
            service,
            receiver,
            IntentFilter(ACTION_INSTALL_RESULT),
            ContextCompat.RECEIVER_EXPORTED,
        )

        initShizuku()
    }
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 14046
const val ACTION_INSTALL_RESULT = "${BuildConfig.APPLICATION_ID}.ACTION_INSTALL_RESULT"
