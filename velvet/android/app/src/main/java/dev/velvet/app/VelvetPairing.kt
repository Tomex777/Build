package dev.velvet.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * A separate online account/pairing surface. The existing chat remains a LOCAL
 * preview until messages, media and offline sync have been integrated.
 *
 * Only the Supabase publishable key is in BuildConfig. Tokens are encrypted by
 * Android Keystore; no service-role secret, password or partner ID is stored.
 */
internal class VelvetPairClient(private val context: Context) {
    private val endpoint = BuildConfig.VELVET_SUPABASE_URL.trimEnd('/')
    private val publishable = BuildConfig.VELVET_SUPABASE_PUBLISHABLE_KEY
    val configured: Boolean = endpoint.startsWith("https://") &&
        publishable.isNotBlank() && runCatching { URL(endpoint).host.isNotBlank() }.getOrDefault(false)
    private val vault = RefreshTokenVault(context)
    private var accessToken: String? = null
    private var expiresAtMs: Long = 0L

    private fun sendRaw(path: String, body: JSONObject, bearer: String? = null): String {
        check(configured) { "Online pairing has not been configured in this build." }
        val connection = URL(endpoint + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("apikey", publishable)
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if(status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    val json=JSONObject(response)
                    json.optString("msg").takeIf { it.isNotBlank() }
                        ?: json.optString("message").takeIf { it.isNotBlank() }
                        ?: json.optString("error_description").takeIf { it.isNotBlank() }
                }.getOrNull()
                throw IllegalStateException(when (status) {
                    401 -> "Session expired. Please sign in again."
                    429 -> "Too many attempts. Please try again later."
                    else -> detail ?: "Connection failed (HTTP $status)."
                })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun send(path: String, body: JSONObject, bearer: String? = null): JSONObject {
        val raw=sendRaw(path,body,bearer)
        return if(raw.trim().startsWith("{")) JSONObject(raw) else JSONObject()
    }

    private fun activeToken(): String {
        accessToken?.takeIf {System.currentTimeMillis()<expiresAtMs}?.let {return it}
        val refresh=vault.read() ?: throw IllegalStateException("Please sign in again.")
        val result=send("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",refresh))
        if(!acceptAuth(result)) throw IllegalStateException("Please sign in again.")
        return accessToken ?: throw IllegalStateException("Please sign in again.")
    }

    private fun acceptAuth(body: JSONObject): Boolean {
        val newAccess = body.optString("access_token")
        val newRefresh = body.optString("refresh_token")
        if (newAccess.isBlank() || newRefresh.isBlank()) return false
        accessToken = newAccess
        expiresAtMs = System.currentTimeMillis() + (body.optLong("expires_in",3600L)-60L).coerceAtLeast(1L)*1000L
        vault.write(newRefresh)
        return true
    }

    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        val refresh = vault.read() ?: return@withContext false
        runCatching {
            acceptAuth(send("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh)))
        }.getOrElse { error ->
            if(error.message?.contains("Invalid Refresh Token",true)==true || error.message?.contains("Refresh Token Not Found",true)==true) vault.clear()
            accessToken=null; false
        }
    }

    suspend fun signUp(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val response = send("/auth/v1/signup",JSONObject().put("email",email.trim())
            .put("password",password))
        // Email-confirmation projects do not return tokens at sign-up.
        acceptAuth(response)
    }

    suspend fun signIn(email: String,password: String): Unit = withContext(Dispatchers.IO) {
        val response = send("/auth/v1/token?grant_type=password",
            JSONObject().put("email",email.trim()).put("password",password))
        check(acceptAuth(response)) { "Sign-in did not return an active session." }
    }

    suspend fun myPair(): JSONObject? = withContext(Dispatchers.IO) {
        val data = rpc("velvet_my_pair")
        if(data.length()==0) null else data
    }
    suspend fun createInvite(): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_create_pair_invite")
    }
    suspend fun joinInvite(code: String): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_join_pair_invite",JSONObject().put("p_code",code))
    }
    suspend fun revokeInvite(): Boolean = withContext(Dispatchers.IO) {
        rpcBoolean("velvet_revoke_pair_invite")
    }

    suspend fun sendText(id:UUID, body:String): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_send_text",JSONObject().put("p_id",id.toString()).put("p_body",body))
    }

    suspend fun listText(afterAt:String?=null,afterId:String?=null): JSONArray =
        withContext(Dispatchers.IO) {
            val params=JSONObject()
            if(afterAt!=null && afterId!=null) {
                params.put("p_after_at",afterAt).put("p_after_id",afterId)
            }
            val raw=sendRaw("/rest/v1/rpc/velvet_list_text",params,activeToken())
            JSONArray(raw)
        }

    private fun rpc(name:String, data:JSONObject=JSONObject()): JSONObject {
        return send("/rest/v1/rpc/$name",data,activeToken())
    }
    private fun rpcBoolean(name:String): Boolean {
        val bearer=activeToken()
        // Supabase PostgREST returns a primitive JSON boolean for this RPC.
        val response = URL(endpoint+"/rest/v1/rpc/$name").openConnection() as HttpURLConnection
        try {
            response.requestMethod="POST"
            response.connectTimeout=15000
            response.readTimeout=15000
            response.doOutput=true
            response.setRequestProperty("apikey",publishable)
            response.setRequestProperty("Authorization","Bearer $bearer")
            response.setRequestProperty("Content-Type","application/json")
            response.outputStream.use { it.write("{}".toByteArray()) }
            val status=response.responseCode
            if(status !in 200..299) throw IllegalStateException("Could not revoke invitation (HTTP $status).")
            return response.inputStream.bufferedReader().use {it.readText()}.trim()=="true"
        } finally {response.disconnect()}
    }
    fun signOut() {
        accessToken=null
        expiresAtMs=0L
        vault.clear()
    }
}

/** AES-GCM envelope backed by a non-exportable Android Keystore key. */
private class RefreshTokenVault(context:Context) {
    private val prefs=context.getSharedPreferences("velvet_secure_session",Context.MODE_PRIVATE)
    private val alias="velvet_refresh_v1"
    private fun key():java.security.Key {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        ks.getKey(alias,null)?.let { return it }
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun write(token:String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val packet=cipher.iv+cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("token",Base64.encodeToString(packet,Base64.NO_WRAP)).apply()
    }
    fun read():String? {
        val payload=prefs.getString("token",null) ?: return null
        return runCatching {
            val bytes=Base64.decode(payload,Base64.NO_WRAP)
            require(bytes.size>28)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
        }.getOrElse { clear();null }
    }
    fun clear(){ prefs.edit().remove("token").apply() }
}

@Composable
internal fun VelvetPairingScreen(onBack:()->Unit,onConnectedChat:()->Unit) {
    val ctx=LocalContext.current
    val api=remember { VelvetPairClient(ctx.applicationContext) }
    val scope=rememberCoroutineScope()
    val clipboard=LocalClipboardManager.current
    var loading by remember { mutableStateOf(api.configured) }
    var signedIn by remember { mutableStateOf(false) }
    var newAccount by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    var pair by remember { mutableStateOf<JSONObject?>(null) }
    var notice by remember { mutableStateOf("") }
    val rose=Color(0xFFDDA1B7)
    val muted=Color(0xFFD1B7C1)
    fun runAction(action:suspend ()->Unit) {
        if(loading)return
        loading=true
        scope.launch {
            try { action() }
            catch(t:Exception) { notice=t.message ?: "Could not connect. Try again." }
            finally { loading=false }
        }
    }
    LaunchedEffect(Unit) {
        if(api.configured) {
            signedIn=api.restore()
            if(signedIn) pair=runCatching {api.myPair()}.getOrNull()
            loading=false
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF171019))
            .verticalScroll(rememberScrollState()).padding(horizontal=22.dp, vertical=18.dp)
    ) {
        TextButton(onClick=onBack) { Text("← Back to Velvet",color=rose) }
        Spacer(Modifier.height(18.dp))
        Text("Our little world,\nconnected.",fontSize=31.sp,lineHeight=37.sp,
            fontWeight=FontWeight.SemiBold,color=Color(0xFFFFE3EA))
        Spacer(Modifier.height(12.dp))
        Text("Two accounts. One private space. Invitation-only pairing.",
            color=muted,fontSize=14.sp)
        Spacer(Modifier.height(20.dp))
        if(!api.configured) {
            Text("Online pairing is not configured in this APK.",color=rose,fontSize=17.sp)
            Spacer(Modifier.height(10.dp))
            Text("An administrator needs to deploy the Velvet migrations and set the Supabase URL and publishable key in the build. Offline preview remains available.",
                color=muted)
        } else if(!signedIn) {
            Text(if(newAccount) "Create your account" else "Sign in",
                color=rose,fontSize=21.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(email,{ email=it },label={Text("Email")},singleLine=true,
                modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(password,{password=it},label={Text("Password")},
                singleLine=true,visualTransformation=PasswordVisualTransformation(),
                modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(enabled=!loading && email.isNotBlank() && password.length>=6,
                onClick={
                    runAction {
                        if(newAccount) {
                            signedIn=api.signUp(email,password)
                            notice=if(signedIn) "Account created. You are signed in."
                                   else "Check your email to confirm your account, then sign in."
                        } else {
                            api.signIn(email,password)
                            signedIn=true
                            pair=api.myPair()
                            password=""
                            notice="Signed in. Your online space is ready to pair."
                        }
                    }
                },modifier=Modifier.fillMaxWidth()){
                Text(if(loading) "Connecting…" else if(newAccount) "Create account" else "Sign in")
            }
            TextButton(onClick={newAccount=!newAccount},enabled=!loading) {
                Text(if(newAccount) "Already registered? Sign in" else "New to Velvet? Create an account",color=rose)
            }
        } else {
            val count=pair?.optInt("member_count",0) ?: 0
            val partner=pair?.optString("partner_name","")?.takeUnless {it.isBlank() || it=="null"}
            if(count==2) {
                Text("♥ You're paired",color=rose,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Text("Your space now has two members"+(partner?.let{": $it"} ?: "."),
                    color=muted)
                Spacer(Modifier.height(7.dp))
                Text("Pairing is ready. Your connected text chat is separate from offline Chat, games and media.",
                    color=muted,fontSize=12.sp)
                Spacer(Modifier.height(15.dp))
                Button(onClick=onConnectedChat,enabled=!loading,modifier=Modifier.fillMaxWidth()) {
                    Text("Open connected chat →")
                }
            } else {
                Text("Connect with your person",color=rose,fontSize=21.sp)
                Spacer(Modifier.height(8.dp))
                Text("Create a one-time code and send it privately, or enter the code they sent you. Codes expire after 20 minutes.",
                    color=muted,fontSize=13.sp)
                Spacer(Modifier.height(13.dp))
                Button(onClick={
                    runAction {
                        val result=api.createInvite()
                        invitation=result.optString("code")
                        pair=api.myPair()
                        notice="Fresh code generated. Any previous code is invalid."
                    }
                },enabled=!loading,modifier=Modifier.fillMaxWidth()){
                    Text(if(loading) "Working…" else "Generate invitation code")
                }
                if(invitation.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(invitation,color=Color.White,fontSize=23.sp,letterSpacing=1.5.sp)
                    TextButton(onClick={clipboard.setText(AnnotatedString(invitation))}){
                        Text("Copy code",color=rose)
                    }
                    TextButton(onClick={
                        runAction {
                            api.revokeInvite()
                            invitation=""
                            notice="Invitation revoked."
                        }
                    },enabled=!loading){Text("Revoke this code",color=muted)}
                }
                Spacer(Modifier.height(18.dp))
                Text("Have their code?",color=muted)
                OutlinedTextField(code,{code=it.uppercase()},label={Text("Invitation code")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick={
                    runAction {
                        val outcome=api.joinInvite(code)
                        if(outcome.optBoolean("ok")) {
                            pair=api.myPair()
                            invitation=""
                            code=""
                            notice="You've connected your two accounts ♥"
                        } else notice=when(outcome.optString("error")) {
                            "rate_limited" -> "Too many guesses. Try again in an hour."
                            "already_paired" -> "This account is already in a couple."
                            else -> "That code is invalid, expired or already used."
                        }
                    }
                },enabled=!loading && code.isNotBlank(),modifier=Modifier.fillMaxWidth()){
                    Text("Join their space")
                }
            }
            Spacer(Modifier.height(22.dp))
            TextButton(onClick={
                runAction {pair=api.myPair();notice="Pairing status refreshed."}
            },enabled=!loading){Text("Refresh pairing status",color=rose)}
            TextButton(onClick={
                api.signOut();signedIn=false;pair=null;invitation="";code=""
                notice="Signed out of this phone."
            },enabled=!loading){Text("Sign out",color=muted)}
        }
        if(notice.isNotEmpty()) {
            Spacer(Modifier.height(17.dp))
            Text(notice,color=rose,fontSize=13.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text("Connected text chat only: local Chat history, photos, voice notes and games are not automatically shared.",
            color=muted,fontSize=11.sp,lineHeight=17.sp)
        Spacer(Modifier.height(20.dp))
    }
}
