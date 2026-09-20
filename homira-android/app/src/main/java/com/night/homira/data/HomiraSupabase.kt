package com.night.homira.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.client.call.body
import io.ktor.http.ContentType
import java.io.File
import java.time.Instant
import java.util.UUID

object HomiraSupabase {
    const val url = "https://uhyeopkbamwtgjgeqlyj.supabase.co"
    const val publishableKey = "sb_publishable_LWuP4oSicae9jds0fKIDEg_5s-yvhoV"

    val client = createSupabaseClient(
        supabaseUrl = url,
        supabaseKey = publishableKey
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
        install(Functions)
    }
}

@Serializable
data class IncomingCallPushResult(
    val configured: Boolean = true,
    val delivered: Int = 0,
    val failed: Int = 0,
    val attempts: Int = 0,
    val reason: String? = null,
    val error: String? = null
)

@Serializable
data class LiveDialTarget(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("phone_e164") val phoneE164: String? = null,
    val about: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("call_card_path") val callCardPath: String? = null
)

@Serializable
private data class ResolveDialTargetParams(
    @SerialName("p_phone") val phone: String
)

@Serializable
data class LiveContact(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("phone_e164") val phoneE164: String? = null,
    val about: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("call_card_path") val callCardPath: String? = null,
    val favorite: Boolean = false,
    @SerialName("local_name") val localName: String? = null
)

@Serializable
private data class AddContactParams(
    @SerialName("p_query") val query: String,
    @SerialName("p_local_name") val localName: String? = null
)

@Serializable
private data class BlockRow(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("blocked_user_id") val blockedUserId: String
)

@Serializable
data class LiveCallSession(
    val id: String,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("media_type") val mediaType: String,
    val state: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
private data class NewCallSession(
    val id: String,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("media_type") val mediaType: String
)

@Serializable
private data class DevicePushTokenRow(
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
    val platform: String,
    val token: String,
    @SerialName("updated_at") val updatedAt: String = Instant.now().toString()
)

@Serializable
data class LiveVoicemail(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("call_session_id") val callSessionId: String? = null,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("duration_ms") val durationMs: Int,
    @SerialName("listened_at") val listenedAt: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewVoicemail(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("call_session_id") val callSessionId: String? = null,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("duration_ms") val durationMs: Int
)

@Serializable
data class LiveProfile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("phone_e164") val phoneE164: String? = null,
    val email: String? = null,
    val about: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("call_card_path") val callCardPath: String? = null,
    @SerialName("voicemail_enabled") val voicemailEnabled: Boolean = true,
    @SerialName("voicemail_greeting_mode") val voicemailGreetingMode: String = "default",
    @SerialName("voicemail_greeting_path") val voicemailGreetingPath: String? = null
)

@Serializable
data class HomiraIceServerConfig(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

@Serializable
data class HomiraTurnConfiguration(
    val configured: Boolean = false,
    val provider: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("ice_servers") val iceServers: List<HomiraIceServerConfig> = emptyList()
)

class HomiraLiveRepository {
    private val client get() = HomiraSupabase.client

    suspend fun initialize() {
        client.auth.awaitInitialization()
    }

    fun isSignedIn(): Boolean = client.auth.currentUserOrNull() != null

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun currentUserEmail(): String? = client.auth.currentUserOrNull()?.email

    suspend fun sendPhoneOtp(phone: String) {
        client.auth.signInWith(OTP) {
            this.phone = phone
        }
    }

    suspend fun verifyPhoneOtp(phone: String, code: String) {
        client.auth.verifyPhoneOtp(
            type = OtpType.Phone.SMS,
            phone = phone,
            token = code
        )
    }

    suspend fun sendEmailOtp(email: String) {
        client.auth.signInWith(OTP) {
            this.email = email
        }
    }

    suspend fun verifyEmailOtp(email: String, code: String) {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = email,
            token = code
        )
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun loadMyProfile(): LiveProfile? {
        val userId = currentUserId() ?: return null
        return runCatching {
            client.from("profiles")
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingle<LiveProfile>()
        }.getOrNull()
    }

    suspend fun resolveDialTarget(phoneE164: String): LiveDialTarget? =
        client.postgrest
            .rpc(
                "resolve_homira_call_target",
                buildJsonObject {
                    put("p_phone", phoneE164)
                }
            )
            .decodeList<LiveDialTarget>()
            .firstOrNull()

    suspend fun loadContacts(): List<LiveContact> =
        client.postgrest
            .rpc("list_my_contacts")
            .decodeList<LiveContact>()

    suspend fun addContact(query: String, localName: String? = null): LiveContact? =
        client.postgrest
            .rpc(
                "add_homira_contact",
                buildJsonObject {
                    put("p_query", query.trim())
                    put("p_local_name", localName?.trim())
                }
            )
            .decodeList<LiveContact>()
            .firstOrNull()

    suspend fun setContactFavorite(
        contactUserId: String,
        favorite: Boolean
    ) {
        val ownerId = requireNotNull(currentUserId()) { "Not signed in" }
        client.from("contacts")
            .update({
                set("favorite", favorite)
            }) {
                filter {
                    eq("owner_id", ownerId)
                    eq("contact_user_id", contactUserId)
                }
            }
    }

    suspend fun deleteContact(contactUserId: String) {
        val ownerId = requireNotNull(currentUserId()) { "Not signed in" }
        client.from("contacts")
            .delete {
                filter {
                    eq("owner_id", ownerId)
                    eq("contact_user_id", contactUserId)
                }
            }
    }

    suspend fun registerPushToken(
        deviceId: String,
        token: String,
        platform: String = "android"
    ) {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        require(deviceId.isNotBlank()) { "Device ID is required" }
        require(token.isNotBlank()) { "Push token is required" }
        require(platform == "android" || platform == "ios") {
            "Unsupported push platform"
        }

        client.postgrest["device_push_tokens"].upsert(
            DevicePushTokenRow(
                userId = userId,
                deviceId = deviceId,
                platform = platform,
                token = token
            )
        ) {
            onConflict = "user_id,device_id"
        }
    }

    suspend fun removePushToken(deviceId: String) {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        client.from("device_push_tokens").delete {
            filter {
                eq("user_id", userId)
                eq("device_id", deviceId)
            }
        }
    }

    suspend fun listBlockedUserIds(): Set<String> {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        return client.from("blocks")
            .select {
                filter { eq("owner_id", userId) }
            }
            .decodeList<BlockRow>()
            .mapTo(linkedSetOf()) { it.blockedUserId }
    }

    suspend fun blockUser(userId: String) {
        val ownerId = requireNotNull(currentUserId()) { "Not signed in" }
        require(userId != ownerId) { "Cannot block yourself" }

        client.from("blocks").insert(
            BlockRow(
                ownerId = ownerId,
                blockedUserId = userId
            )
        )
    }

    suspend fun unblockUser(userId: String) {
        val ownerId = requireNotNull(currentUserId()) { "Not signed in" }
        client.from("blocks").delete {
            filter {
                eq("owner_id", ownerId)
                eq("blocked_user_id", userId)
            }
        }
    }

    suspend fun requestIncomingCallPush(
        callId: String
    ): IncomingCallPushResult {
        require(callId.isNotBlank()) { "Call ID is required" }

        return client.functions.invoke(
            function = "push-incoming-call",
            body = buildJsonObject {
                put("call_id", callId)
            }
        ).body<IncomingCallPushResult>()
    }

    suspend fun loadTurnConfiguration(): HomiraTurnConfiguration {
        requireNotNull(currentUserId()) { "Not signed in" }
        return client.functions
            .invoke(function = "turn-credentials")
            .body<HomiraTurnConfiguration>()
    }

    suspend fun startCall(calleeId: String, video: Boolean): LiveCallSession {
        val callerId = requireNotNull(currentUserId()) { "Not signed in" }
        val callId = UUID.randomUUID().toString()

        client.from("call_sessions")
            .insert(
                NewCallSession(
                    id = callId,
                    callerId = callerId,
                    calleeId = calleeId,
                    mediaType = if (video) "video" else "audio"
                )
            )

        return requireNotNull(loadCallSessionById(callId)) {
            "Call was created but could not be reloaded."
        }
    }

    suspend fun loadCallSessionById(callId: String): LiveCallSession? {
        if (currentUserId() == null || callId.isBlank()) return null

        return runCatching {
            client.from("call_sessions")
                .select {
                    filter { eq("id", callId) }
                }
                .decodeList<LiveCallSession>()
                .firstOrNull()
        }.getOrNull()
    }

    suspend fun loadIncomingCallById(callId: String): LiveCallSession? {
        val userId = currentUserId() ?: return null
        if (callId.isBlank()) return null

        return runCatching {
            client.from("call_sessions")
                .select {
                    filter {
                        eq("id", callId)
                        eq("callee_id", userId)
                        eq("state", "ringing")
                    }
                }
                .decodeList<LiveCallSession>()
                .firstOrNull()
        }.getOrNull()
    }

    suspend fun loadPendingIncomingCall(): LiveCallSession? {
        if (currentUserId() == null) return null
        return client.postgrest
            .rpc("latest_pending_incoming_call")
            .decodeList<LiveCallSession>()
            .firstOrNull()
    }

    suspend fun loadProfileById(userId: String): LiveProfile? =
        runCatching {
            client.from("profiles")
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingle<LiveProfile>()
        }.getOrNull()

    suspend fun setCallState(callId: String, state: String): LiveCallSession {
        client.from("call_sessions")
            .update({
                set("state", state)
            }) {
                filter { eq("id", callId) }
            }

        val updated = requireNotNull(loadCallSessionById(callId)) {
            "Call state changed but the call could not be reloaded."
        }
        require(updated.state == state) {
            "Call state update was not applied."
        }
        return updated
    }

    fun observeIncomingCallChanges(): Flow<LiveCallSession> = flow {
        val userId = currentUserId() ?: return@flow
        val channel = client.channel("incoming-calls-$userId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "call_sessions"
            filter = "callee_id=eq.$userId"
        }

        client.realtime.connect()
        channel.subscribe(blockUntilSubscribed = true)

        try {
            changes.collect { action ->
                val session = when (action) {
                    is PostgresAction.Insert -> action.decodeRecordOrNull<LiveCallSession>()
                    is PostgresAction.Update -> action.decodeRecordOrNull<LiveCallSession>()
                    else -> null
                }
                if (session != null) emit(session)
            }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    fun observeCallSession(callId: String): Flow<LiveCallSession> = flow {
        val channel = client.channel("call-session-$callId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "call_sessions"
            filter = "id=eq.$callId"
        }

        client.realtime.connect()
        channel.subscribe(blockUntilSubscribed = true)

        try {
            changes.collect { action ->
                val session = when (action) {
                    is PostgresAction.Insert -> action.decodeRecordOrNull<LiveCallSession>()
                    is PostgresAction.Update -> action.decodeRecordOrNull<LiveCallSession>()
                    else -> null
                }
                if (session != null) emit(session)
            }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    fun observeReceivedVoicemailChanges(): Flow<LiveVoicemail> = flow {
        val userId = currentUserId() ?: return@flow
        val channel = client.channel("received-voicemails-$userId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "voicemails"
            filter = "recipient_id=eq.$userId"
        }

        client.realtime.connect()
        channel.subscribe(blockUntilSubscribed = true)

        try {
            changes.collect { action ->
                val voicemail = when (action) {
                    is PostgresAction.Insert -> action.decodeRecordOrNull<LiveVoicemail>()
                    is PostgresAction.Update -> action.decodeRecordOrNull<LiveVoicemail>()
                    else -> null
                }
                if (voicemail != null) emit(voicemail)
            }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    suspend fun setVoicemailEnabled(enabled: Boolean): LiveProfile {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }

        client.from("profiles")
            .update({
                set("voicemail_enabled", enabled)
            }) {
                filter { eq("id", userId) }
            }

        return requireNotNull(loadMyProfile()) {
            "Voicemail setting was saved but the profile could not be reloaded."
        }
    }

    suspend fun saveVoicemailGreeting(audioFile: File): LiveProfile {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        require(audioFile.exists() && audioFile.length() > 0L) { "Greeting audio is empty" }

        val current = loadMyProfile()
        val oldPath = current?.voicemailGreetingPath
        val newPath = "$userId/greetings/${UUID.randomUUID()}.m4a"
        val bucket = client.storage["voicemail"]

        bucket.upload(newPath, audioFile) {
            upsert = false
            contentType = ContentType.parse("audio/mp4")
        }

        return try {
            client.from("profiles")
                .update({
                    set("voicemail_greeting_mode", "voice")
                    set("voicemail_greeting_path", newPath)
                }) {
                    filter { eq("id", userId) }
                }

            val updated = requireNotNull(loadMyProfile()) {
                "Greeting was saved but the profile could not be reloaded."
            }

            if (!oldPath.isNullOrBlank() && oldPath != newPath) {
                runCatching { bucket.delete(oldPath) }
            }

            updated
        } catch (error: Throwable) {
            runCatching { bucket.delete(newPath) }
            throw error
        }
    }

    suspend fun useDefaultVoicemailGreeting(): LiveProfile {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        val current = loadMyProfile()
        val oldPath = current?.voicemailGreetingPath

        client.from("profiles")
            .update({
                set("voicemail_greeting_mode", "default")
                set("voicemail_greeting_path", null as String?)
            }) {
                filter { eq("id", userId) }
            }

        val updated = requireNotNull(loadMyProfile()) {
            "Greeting setting was saved but the profile could not be reloaded."
        }

        if (!oldPath.isNullOrBlank()) {
            runCatching { client.storage["voicemail"].delete(oldPath) }
        }

        return updated
    }

    suspend fun downloadVoicemailAudio(storagePath: String): ByteArray =
        client.storage["voicemail"].downloadAuthenticated(storagePath)

    suspend fun listReceivedVoicemails(): List<LiveVoicemail> {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        return client.from("voicemails")
            .select {
                filter { eq("recipient_id", userId) }
            }
            .decodeList<LiveVoicemail>()
            .sortedByDescending { it.createdAt }
    }

    suspend fun uploadVoicemail(
        recipientId: String,
        callSessionId: String?,
        audioFile: File,
        durationMs: Int
    ): LiveVoicemail {
        val senderId = requireNotNull(currentUserId()) { "Not signed in" }
        require(senderId != recipientId) { "Cannot leave voicemail for yourself" }
        require(durationMs in 1..120_000) { "Voicemail must be between 1 ms and 2 minutes" }
        require(audioFile.exists() && audioFile.length() > 0L) { "Voicemail audio is empty" }

        val voicemailId = UUID.randomUUID().toString()
        val path = "$senderId/$voicemailId.m4a"
        val bucket = client.storage["voicemail"]

        bucket.upload(path, audioFile) {
            upsert = false
            contentType = ContentType.parse("audio/mp4")
        }

        return try {
            client.from("voicemails")
                .insert(
                    NewVoicemail(
                        id = voicemailId,
                        senderId = senderId,
                        recipientId = recipientId,
                        callSessionId = callSessionId,
                        storagePath = path,
                        durationMs = durationMs
                    )
                )

            requireNotNull(loadVoicemailById(voicemailId)) {
                "Voicemail was saved but could not be reloaded."
            }
        } catch (error: Throwable) {
            runCatching { bucket.delete(path) }
            throw error
        }
    }

    suspend fun downloadVoicemail(voicemail: LiveVoicemail): ByteArray =
        client.storage["voicemail"]
            .downloadAuthenticated(voicemail.storagePath)

    private suspend fun loadVoicemailById(voicemailId: String): LiveVoicemail? =
        runCatching {
            client.from("voicemails")
                .select {
                    filter { eq("id", voicemailId) }
                }
                .decodeList<LiveVoicemail>()
                .firstOrNull()
        }.getOrNull()

    suspend fun markVoicemailListened(voicemailId: String): LiveVoicemail {
        client.from("voicemails")
            .update({
                set("listened_at", Instant.now().toString())
            }) {
                filter { eq("id", voicemailId) }
            }

        return requireNotNull(loadVoicemailById(voicemailId)) {
            "Voicemail was updated but could not be reloaded."
        }
    }

    suspend fun deleteVoicemail(voicemail: LiveVoicemail) {
        client.storage["voicemail"].delete(voicemail.storagePath)
        client.from("voicemails").delete {
            filter { eq("id", voicemail.id) }
        }
    }

    suspend fun uploadProfileMedia(
        jpegBytes: ByteArray,
        kind: String
    ): String {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        require(jpegBytes.isNotEmpty()) { "Profile image is empty" }

        val safeKind = when (kind) {
            "avatar" -> "avatar"
            "call-card" -> "call-card"
            else -> error("Unsupported profile media kind")
        }

        val path = "$userId/$safeKind/${UUID.randomUUID()}.jpg"
        client.storage["profile-media"].upload(path, jpegBytes) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        return path
    }

    suspend fun downloadProfileMedia(storagePath: String): ByteArray =
        client.storage["profile-media"]
            .downloadAuthenticated(storagePath)

    suspend fun deleteProfileMedia(storagePath: String) {
        if (storagePath.isBlank()) return
        client.storage["profile-media"].delete(storagePath)
    }

    suspend fun completeMyProfile(
        displayName: String,
        username: String,
        phoneE164: String
    ): LiveProfile {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        val accountEmail = currentUserEmail()

        client.from("profiles")
            .update({
                set("display_name", displayName.trim())
                set("username", username.trim().lowercase().ifBlank { null })
                set("phone_e164", phoneE164.trim())
                set("email", accountEmail)
            }) {
                filter { eq("id", userId) }
            }

        return requireNotNull(loadMyProfile()) {
            "Profile was saved but could not be reloaded."
        }
    }

    suspend fun updateMyProfile(
        displayName: String,
        username: String,
        phoneE164: String,
        about: String,
        email: String,
        avatarPath: String?,
        callCardPath: String?
    ): LiveProfile {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }

        client.from("profiles")
            .update({
                set("display_name", displayName.trim())
                set("username", username.trim().lowercase().ifBlank { null })
                set("phone_e164", phoneE164.trim().ifBlank { null })
                set("about", about.trim())
                set("email", email.trim().ifBlank { null })
                set("avatar_path", avatarPath)
                set("call_card_path", callCardPath)
            }) {
                filter { eq("id", userId) }
            }

        return requireNotNull(loadMyProfile()) {
            "Profile was saved but could not be reloaded."
        }
    }
}
