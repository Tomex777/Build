package com.night.keyboard

import android.content.Intent
import android.view.inputmethod.InputMethod
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.night.keyboard.ime.KeyboardInputMethodService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImeRegistrationTest {
    @Test fun inputMethodServiceIsDiscoverable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val services = context.packageManager.queryIntentServices(Intent(InputMethod.SERVICE_INTERFACE), 0)
        assertTrue(services.any { it.serviceInfo.name == KeyboardInputMethodService::class.java.name })
    }
}
