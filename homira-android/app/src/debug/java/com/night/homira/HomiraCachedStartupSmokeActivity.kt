package com.night.homira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class HomiraCachedStartupSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getSharedPreferences("homira_profile_cache", Context.MODE_PRIVATE)
            .edit()
            .putString("id", "smoke-user")
            .putString("display_name", "Smoke User")
            .putString("username", "smoke")
            .putString("phone_e164", "+2348012345678")
            .putString("email", "smoke@example.com")
            .putString("about", "")
            .putBoolean("voicemail_enabled", true)
            .putString("voicemail_greeting_mode", "default")
            .commit()

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }
}
