package com.night.pahebatcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.night.pahebatcher.ui.PaheApp
import com.night.pahebatcher.ui.PaheViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PaheViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestLegacyStoragePermissionIfNeeded()
        setContent {
            PaheApp(viewModel)
        }
    }

    private fun requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            LEGACY_STORAGE_REQUEST,
        )
    }

    companion object {
        private const val LEGACY_STORAGE_REQUEST = 2601
    }
}
