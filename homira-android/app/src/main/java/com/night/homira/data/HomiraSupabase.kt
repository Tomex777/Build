package com.night.homira.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    }
}

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
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("media_type") val mediaType: String
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

class HomiraLiveRepository {
    private val client get() = HomiraSupabase.client

    suspend fun initialize() {
        client.auth.awaitInitialization()
    }

    fun isSignedIn(): Boolean = client.auth.currentUserOrNull() != null

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

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

    suspend fun startCall(calleeId: String, video: Boolean): LiveCallSession {
        val callerId = requireNotNull(currentUserId()) { "Not signed in" }
        return client.from("call_sessions")
            .insert(
                NewCallSession(
                    callerId = callerId,
                    calleeId = calleeId,
                    mediaType = if (video) "video" else "audio"
                )
            )
            .decodeSingle<LiveCallSession>()
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

    suspend fun setCallState(callId: String, state: String): LiveCallSession =
        client.from("call_sessions")
            .update({
                set("state", state)
            }) {
                filter { eq("id", callId) }
            }
            .decodeSingle<LiveCallSession>()

    fun observeIncomingCallChanges(): Flow<LiveCallSession> = flow {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
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

    suspend fun saveVoicemailGreeting(audioFile: File): LiveProfile {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        require(audioFile.exists() && audioFile.length() > 0L) { "Greeting audio is empty" }

        val current = loadMyProfile()
        val oldPath = current?.voicemailGreetingPath
        val newPath = "$userId/greetings/${UUID.randomUUID()}.m4a"
        val bucket = client.storage["voicemail"]

        bucket.upload(newPath, audioFile, upsert = false) {
            contentType = ContentType.parse("audio/mp4")
        }

        return try {
            val updated = client.from("profiles")
                .update({
                    set("voicemail_greeting_mode", "voice")
                    set("voicemail_greeting_path", newPath)
                }) {
                    filter { eq("id", userId) }
                }
                .decodeSingle<LiveProfile>()

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

        val updated = client.from("profiles")
            .update({
                set("voicemail_greeting_mode", "default")
                set("voicemail_greeting_path", null as String?)
            }) {
                filter { eq("id", userId) }
            }
            .decodeSingle<LiveProfile>()

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
        require(durationMs in 1..300_000) { "Voicemail must be between 1 ms and 5 minutes" }
        require(audioFile.exists() && audioFile.length() > 0L) { "Voicemail audio is empty" }

        val path = "$senderId/${UUID.randomUUID()}.m4a"
        val bucket = client.storage["voicemail"]

        bucket.upload(path, audioFile, upsert = false) {
            contentType = ContentType.parse("audio/mp4")
        }

        return try {
            client.from("voicemails")
                .insert(
                    NewVoicemail(
                        senderId = senderId,
                        recipientId = recipientId,
                        callSessionId = callSessionId,
                        storagePath = path,
                        durationMs = durationMs
                    )
                )
                .decodeSingle<LiveVoicemail>()
        } catch (error: Throwable) {
            runCatching { bucket.delete(path) }
            throw error
        }
    }

    suspend fun downloadVoicemail(voicemail: LiveVoicemail): ByteArray =
        client.storage["voicemail"]
            .downloadAuthenticated(voicemail.storagePath)

    suspend fun markVoicemailListened(voicemailId: String): LiveVoicemail =
        client.from("voicemails")
            .update({
                set("listened_at", Instant.now().toString())
            }) {
                filter { eq("id", voicemailId) }
            }
            .decodeSingle<LiveVoicemail>()

    suspend fun deleteVoicemail(voicemail: LiveVoicemail) {
        client.storage["voicemail"].delete(voicemail.storagePath)
        client.from("voicemails").delete {
            filter { eq("id", voicemail.id) }
        }
    }

    suspend fun updateMyProfile(
        displayName: String,
        username: String,
        about: String,
        email: String
    ): LiveProfile {
        val userId = requireNotNull(currentUserId()) { "Not signed in" }
        return client.from("profiles")
            .update({
                set("display_name", displayName.trim())
                set("username", username.trim().lowercase().ifBlank { null })
                set("about", about.trim())
                set("email", email.trim().ifBlank { null })
            }) {
                filter { eq("id", userId) }
            }
            .decodeSingle<LiveProfile>()
    }
}
