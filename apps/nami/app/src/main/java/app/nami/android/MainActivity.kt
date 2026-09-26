package app.nami.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val app = application as NamiApplication

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }

        // If Android recreated the process while work was queued, reconnect the persisted
        // queue to the foreground download engine as soon as the user returns to Nami.
        if (app.downloadManager.statuses.value.values.any {
                it.state == NamiDownloadState.QUEUED ||
                    it.state == NamiDownloadState.DOWNLOADING ||
                    it.state == NamiDownloadState.WAITING_FOR_NETWORK
            }
        ) {
            app.downloadManager.startBackgroundEngine()
        }

        setContent {
            NamiApp(
                sourceRegistry = app.sourceRegistry,
                installedSourceRegistry = app.installedSourceRegistry,
                sourceEnablementStore = app.sourceEnablementStore,
                database = app.database,
                downloadManager = app.downloadManager,
            )
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 4101
    }
}
