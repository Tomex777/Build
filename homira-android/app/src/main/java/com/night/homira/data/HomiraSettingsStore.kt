package com.night.homira.data

import android.content.Context

data class HomiraLocalSettings(
    val lowDataCalls: Boolean = false,
    val callNotifications: Boolean = true,
    val ringtoneUri: String? = null,
    val notificationPermissionRequested: Boolean = false
)

class HomiraSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): HomiraLocalSettings =
        runCatching {
            HomiraLocalSettings(
                lowDataCalls = preferences.getBoolean(KEY_LOW_DATA_CALLS, false),
                callNotifications = preferences.getBoolean(KEY_CALL_NOTIFICATIONS, true),
                ringtoneUri = preferences.getString(KEY_RINGTONE_URI, null),
                notificationPermissionRequested =
                    preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
            )
        }.getOrDefault(HomiraLocalSettings())

    fun setLowDataCalls(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_LOW_DATA_CALLS, enabled)
            .apply()
    }

    fun setCallNotifications(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_CALL_NOTIFICATIONS, enabled)
            .apply()
    }

    fun setRingtoneUri(uri: String?) {
        preferences.edit()
            .putString(KEY_RINGTONE_URI, uri)
            .apply()
    }

    fun setNotificationPermissionRequested(requested: Boolean) {
        preferences.edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, requested)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "homira_call_settings"
        private const val KEY_LOW_DATA_CALLS = "low_data_calls"
        private const val KEY_CALL_NOTIFICATIONS = "call_notifications"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED =
            "notification_permission_requested"
    }
}
