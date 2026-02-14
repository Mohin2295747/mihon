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
    private val pendingInstallations = mutableMapOf<String, CompletableDeferred<InstallResult>>()
    private val signatureMismatchPackages = mutableSetOf<String>()

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
                    logcat { "Installation success for $packageName" }
                    deferred?.complete(InstallResult.Success)
                } else {
                    logcat(LogPriority.ERROR) { "Failed to install extension $packageName: $message" }

                    val isSignatureMismatch = message?.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") == true ||
                        message?.contains("signatures do not match") == true

                    if (isSignatureMismatch) {
                        logcat { "Signature mismatch detected for $packageName, adding to retry set" }
                        signatureMismatchPackages.add(packageName)
                        deferred?.complete(InstallResult.SignatureMismatch(message ?: "Signature mismatch"))
                    } else {
                        deferred?.complete(InstallResult.Failure(message ?: "Unknown error"))
                    }
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
            logcat { "Processing entry: ${entry.uri}" }
            val result = if (reinstallOnFailure) {
                logcat { "Reinstall on failure enabled, using smart retry" }
                installWithSmartRetry(entry)
            } else {
                logcat { "Reinstall on failure disabled, single attempt only" }
                performInstallWithResult(entry) is InstallResult.Success
            }

            withContext(Dispatchers.Main) {
                logcat { "Install result: $result, continuing queue" }
                continueQueue(if (result) InstallStep.Installed else InstallStep.Error)
            }
        }
    }

    private suspend fun performInstallWithResult(entry: Entry): InstallResult {
        val packageName = extractPackageName(entry.uri.toString())
            ?: return InstallResult.Failure("Could not extract package name")

        logcat { "Performing install for package: $packageName" }

        val deferred = CompletableDeferred<InstallResult>()
        pendingInstallations[packageName] = deferred

        return try {
            service.contentResolver.openAssetFileDescriptor(entry.uri, "r").use { fd ->
                logcat { "Starting installation via Shizuku" }
                shellInterface?.install(fd)
            }

            logcat { "Waiting for installation result with timeout" }
            withTimeoutOrNull(30.seconds) {
                deferred.await()
            } ?: InstallResult.Failure("Installation timeout")
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId}" }
            InstallResult.Failure(e.message ?: "Unknown error")
        } finally {
            pendingInstallations.remove(packageName)
        }
    }

    private suspend fun installWithSmartRetry(entry: Entry): Boolean {
        logcat { "Starting smart retry for entry" }
        val firstResult = performInstallWithResult(entry)
        logcat { "First attempt result: $firstResult" }

        return when (firstResult) {
            is InstallResult.Success -> {
                logcat { "First attempt succeeded" }
                true
            }
            is InstallResult.SignatureMismatch -> {
                logcat { "Signature mismatch detected, attempting uninstall and reinstall" }
                val result = handleSignatureMismatch(entry)
                logcat { "Signature mismatch handling result: $result" }
                result
            }
            is InstallResult.Failure -> {
                logcat { "Installation failed with message: ${firstResult.message}" }
                if (reinstallOnFailure) {
                    logcat { "Reinstall on failure enabled, attempting uninstall and reinstall" }
                    val packageName = extractPackageName(entry.uri.toString())
                    if (packageName != null) {
                        logcat { "Attempting to uninstall $packageName" }
                        val uninstallSuccess = uninstallPackage(packageName)
                        logcat { "Uninstall result: $uninstallSuccess" }
                        if (uninstallSuccess) {
                            logcat { "Waiting 500ms before retry" }
                            kotlinx.coroutines.delay(500)
                            val retryResult = performInstallWithResult(entry) is InstallResult.Success
                            logcat { "Retry result: $retryResult" }
                            retryResult
                        } else {
                            false
                        }
                    } else {
                        logcat { "Could not extract package name" }
                        false
                    }
                } else {
                    false
                }
            }
        }
    }

    private suspend fun handleSignatureMismatch(entry: Entry): Boolean {
        val packageName = extractPackageName(entry.uri.toString()) ?: return false
        logcat { "Handling signature mismatch for package: $packageName" }

        val actualPackageName = signatureMismatchPackages.firstOrNull {
            packageName.contains(it) || it.contains(packageName)
        } ?: packageName

        logcat { "Actual package name to uninstall: $actualPackageName" }

        logcat { "Attempting to uninstall $actualPackageName" }
        val uninstallSuccess = uninstallPackage(actualPackageName)
        logcat { "Uninstall result: $uninstallSuccess" }

        if (uninstallSuccess) {
            logcat { "Successfully uninstalled $actualPackageName, waiting 500ms before retry" }
            kotlinx.coroutines.delay(500)
            signatureMismatchPackages.remove(actualPackageName)
            logcat { "Retrying installation" }
            val retryResult = performInstallWithResult(entry) is InstallResult.Success
            logcat { "Retry result: $retryResult" }
            return retryResult
        } else {
            logcat { "Failed to uninstall $actualPackageName" }
            return false
        }
    }

    private suspend fun uninstallPackage(packageName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logcat { "Running: pm uninstall $packageName" }
                val result = shellInterface?.runCommand("pm uninstall $packageName")
                logcat { "Uninstall result: $result" }

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
        pendingInstallations.values.forEach { it.complete(InstallResult.Failure("Installer destroyed")) }
        pendingInstallations.clear()
        signatureMismatchPackages.clear()

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

    sealed class InstallResult {
        object Success : InstallResult()
        data class SignatureMismatch(val message: String) : InstallResult()
        data class Failure(val message: String) : InstallResult()
    }
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
const val ACTION_INSTALL_RESULT = "${BuildConfig.APPLICATION_ID}.ACTION_INSTALL_RESULT"
