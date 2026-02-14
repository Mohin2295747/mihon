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
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import mihon.app.shizuku.IShellInterface
import mihon.app.shizuku.ShellInterface
import rikka.shizuku.Shizuku
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class ShizukuInstaller(private val service: Service) : Installer(service) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = Injekt.get<BasePreferences>()
    private val reinstallOnFailure get() = preferences.shizukuReinstallOnFailure().get()

    private var shellInterface: IShellInterface? = null
    private val pendingInstallations = mutableMapOf<String, CompletableDeferred<Boolean>>()

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

            if (packageName != null) {
                val deferred = pendingInstallations.remove(packageName)
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    deferred?.complete(true)
                } else {
                    logcat(LogPriority.ERROR) { "Failed to install extension $packageName: $message" }
                    deferred?.complete(false)
                }
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

        scope.launch {
            val result = if (reinstallOnFailure) {
                installWithRetry(entry)
            } else {
                performInstallWithResult(entry)
            }

            withContext(Dispatchers.Main) {
                continueQueue(if (result) InstallStep.Installed else InstallStep.Error)
            }
        }
    }

    private suspend fun performInstallWithResult(entry: Entry): Boolean {
        val packageName = extractPackageName(entry.uri.toString()) ?: return false

        val deferred = CompletableDeferred<Boolean>()
        pendingInstallations[packageName] = deferred

        return try {
            service.contentResolver.openAssetFileDescriptor(entry.uri, "r").use { fd ->
                shellInterface?.install(fd)
            }

            withTimeoutOrNull(30.seconds) {
                deferred.await()
            } ?: false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId}" }
            false
        } finally {
            pendingInstallations.remove(packageName)
        }
    }

    private suspend fun installWithRetry(entry: Entry): Boolean {
        var success = performInstallWithResult(entry)

        if (!success && reinstallOnFailure) {
            val packageName = extractPackageName(entry.uri.toString())
            if (packageName != null) {
                logcat { "Installation failed for $packageName, attempting uninstall and reinstall" }

                val actualPackageName = getActualPackageName(entry, packageName)
                val uninstallSuccess = uninstallPackage(actualPackageName)

                if (uninstallSuccess) {
                    logcat { "Successfully uninstalled $actualPackageName, retrying install" }
                    kotlinx.coroutines.delay(500)
                    success = performInstallWithResult(entry)
                } else {
                    logcat { "Failed to uninstall $actualPackageName" }
                }
            }
        }

        return success
    }

    private suspend fun getActualPackageName(entry: Entry, fallback: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val packagePrefix = fallback.substringBeforeLast('.')
                val result = shellInterface?.runCommand("pm list packages | grep $packagePrefix")

                if (!result.isNullOrEmpty()) {
                    result.lines()
                        .firstOrNull { it.contains(packagePrefix) }
                        ?.substringAfter("package:")
                        ?.trim() ?: fallback
                } else {
                    fallback
                }
            } catch (e: Exception) {
                fallback
            }
        }
    }

    private suspend fun uninstallPackage(packageName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = shellInterface?.runCommand("pm uninstall $packageName")
                result?.let {
                    it.contains("Success") ||
                        it.contains("success") ||
                        (it.contains("Failure") && it.contains("not installed"))
                } == true
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to uninstall $packageName" }
                false
            }
        }
    }

    private fun extractPackageName(uriString: String): String? {
        val filename = uriString.substringAfterLast('/')
        val nameWithoutExt = filename.removeSuffix(".apk")
        return nameWithoutExt
            .substringAfterLast('-', "")
            .takeIf { it.isNotEmpty() }
            ?: nameWithoutExt
                .substringAfter('-', nameWithoutExt)
                .substringBeforeLast('-')
                .replace('-', '.')
    }

    override fun cancelEntry(entry: Entry): Boolean = getActiveEntry() != entry

    override fun onDestroy() {
        pendingInstallations.values.forEach { it.complete(false) }
        pendingInstallations.clear()

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
        logcat { "ShizukuInstaller destroy" }
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

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
const val ACTION_INSTALL_RESULT = "${BuildConfig.APPLICATION_ID}.ACTION_INSTALL_RESULT"
