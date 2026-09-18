package com.night.homira.call

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.night.homira.BuildConfig
import com.night.homira.data.HomiraLiveRepository
import java.util.UUID

object HomiraPushBootstrap {
    private const val PREFS_NAME = "homira_push"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_FCM_TARGET = "fcm_target"

    fun isConfigured(): Boolean =
        BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    @Synchronized
    fun initialize(context: Context): Boolean {
        val appContext = context.applicationContext

        runCatching {
            FirebaseApp.getInstance()
        }.getOrNull()?.let {
            return true
        }

        FirebaseApp.initializeApp(appContext)?.let {
            return true
        }

        if (!isConfigured()) return false

        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()

        FirebaseApp.initializeApp(appContext, options)
        return true
    }

    fun requestRegistration(context: Context) {
        if (!initialize(context)) return

        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        messaging.register()
    }

    fun storeTarget(
        context: Context,
        token: String
    ) {
        if (token.isBlank()) return

        preferences(context)
            .edit()
            .putString(KEY_FCM_TARGET, token)
            .apply()
    }

    fun storedTarget(context: Context): String? =
        preferences(context)
            .getString(KEY_FCM_TARGET, null)
            ?.takeIf { it.isNotBlank() }

    fun deviceId(context: Context): String {
        val preferences = preferences(context)
        preferences.getString(KEY_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val generated = UUID.randomUUID().toString()
        preferences.edit()
            .putString(KEY_DEVICE_ID, generated)
            .apply()
        return generated
    }

    suspend fun syncStoredToken(
        context: Context,
        repository: HomiraLiveRepository
    ) {
        val target = storedTarget(context) ?: return
        if (!repository.isSignedIn()) return

        repository.registerPushToken(
            deviceId = deviceId(context),
            token = target,
            platform = "android"
        )
    }

    suspend fun removeRegisteredToken(
        context: Context,
        repository: HomiraLiveRepository
    ) {
        if (!repository.isSignedIn()) return
        repository.removePushToken(deviceId(context))
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
}
