package eu.kanade.tachiyomi.extension.installer

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ShizukuReinstallInstallerService : Service() {

    private var downloadId: Long = -1L
    private lateinit var uri: Uri
    private lateinit var installer: ShizukuReinstallInstaller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
        uri = Uri.parse(intent.getStringExtra(EXTRA_URI))

        installer = ShizukuReinstallInstaller(this)
        installer.addToQueue(downloadId, uri)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        installer.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_URI = "uri"
    }
}
