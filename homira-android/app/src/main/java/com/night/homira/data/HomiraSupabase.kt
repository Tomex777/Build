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
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
