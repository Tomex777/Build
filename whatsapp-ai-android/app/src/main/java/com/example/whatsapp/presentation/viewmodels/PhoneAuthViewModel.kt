package com.example.whatsapp.presentation.viewmodels

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.whatsapp.models.PhoneAuthUser
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

import android.util.Base64

@HiltViewModel
class PhoneAuthViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val database: FirebaseDatabase

) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Ideal)
    val authState = _authState.asStateFlow()

    private val userRef = database.reference.child("users")


    // fun for send verification code
    fun sendVerificationCode(phoneNumber: String,activity: Activity) {

        _authState.value= AuthState.Loading

        val option = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks(){

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                super.onCodeSent(id, token)
                Log.d("PhoneAuth", "onCodeSend triggered. varification ID: $id")
                _authState.value= AuthState.CodeSent(verificationId = id)
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {

                signWithCredential(credential, context = activity)
            }

            override fun onVerificationFailed(exception: FirebaseException) {

                Log.e("PhoneAuth", "Verification Failed: ${exception.message}")
                _authState.value= AuthState.Error(exception.message ?: "verification Failed")
            }


        }

        val phoneAuthOptions = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(option)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(phoneAuthOptions)


    }
//fun for sign user or not
    private fun signWithCredential(credential: PhoneAuthCredential, context: Context) {

        _authState.value= AuthState.Loading

        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->

                if (task.isSuccessful){
                    val user = firebaseAuth.currentUser
                    val phoneAuthUser = PhoneAuthUser(
                        userId = user?.uid?:"",
                        phoneNumber = user?.phoneNumber?:""
                    )

                    markUserAsSignedIn(context)
                    _authState.value= AuthState.Success(phoneAuthUser)

                    fetchUserProfile(user?.uid?:"")
                }
                else{
                    _authState.value= AuthState.Error(task.exception?.message?:"Sign In Failed")

                }
            }
    }
// fun for user is signed in
    private fun markUserAsSignedIn(context: Context) {

        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isSignedIn", true).apply()
    }

    // fun for fetch user profile
    private fun fetchUserProfile(userId: String) {
        val userRef = userRef.child(userId)
        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val userProfile = snapshot.getValue(PhoneAuthUser::class.java)
                if (userProfile != null) {
                    Log.d("PhoneAuth", "User profile found: ${userProfile.name}")
                }
            }
        }.addOnFailureListener { error ->
            Log.e("PhoneAuth", "Profile fetch failed: ${error.message}")
        }
    }

    // Public function to fetch user profile with callback
    fun getUserProfile(userId: String, callback: (PhoneAuthUser?) -> Unit) {
        val userRef = database.reference.child("users").child(userId)
        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val userProfile = snapshot.getValue(PhoneAuthUser::class.java)
                callback(userProfile)
            } else {
                callback(null)
            }
        }.addOnFailureListener { error ->
            Log.e("PhoneAuth", "Profile fetch failed: ${error.message}")
            callback(null)
        }
    }


    // fun for verify otp
    fun verifyCode(otp: String, context: Context) {

        val currentAuthState = _authState.value

        if (currentAuthState !is AuthState.CodeSent || currentAuthState.verificationId.isEmpty()) {

            Log.e("PhoneAuth", "Attempting to Verify OTP Without a valid Verification ID")

            _authState.value = AuthState.Error("Verification not Start or Invalid ID")
            return
        }

        val credential = PhoneAuthProvider.getCredential(currentAuthState.verificationId, otp)
        signWithCredential(credential, context)

    }
// fun for save user profile
    fun saveUserProfile(userId: String, name: String, status: String, profileImage: Bitmap?){

        val database = FirebaseDatabase.getInstance().reference

        val encodedImage = profileImage?.let { convertBitmapToBase64(it)  }
        val userProfile = PhoneAuthUser(
            userId = userId,
            name = name,
            status = status,
            phoneNumber = Firebase.auth.currentUser?.phoneNumber?:"",
            profileImage = encodedImage
        )

        database.child("users").child(userId).setValue(userProfile)

    }
    // fun for convert bitmap to base64
    private fun convertBitmapToBase64(bitmap: Bitmap): String {

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)

    }

    // fun for convert base64 to bitmap
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedByte = Base64.decode(base64String, Base64.DEFAULT)
            val inputStream: InputStream = ByteArrayInputStream(decodedByte)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("PhoneAuthViewModel", "Error converting base64 to bitmap: ${e.message}")
            null
        }
    }

    // fun for reset auth state
    fun resetAuthState() {

        _authState.value = AuthState.Ideal

    }

    // fun for sign out
    fun signOut(context: Context) {

        firebaseAuth.signOut()

        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isSignedIn", false).apply()
    }

}

sealed class AuthState{
    object Ideal : AuthState()
    object Loading : AuthState()
    data class CodeSent(val verificationId : String) : AuthState()
    data class Success(val user : PhoneAuthUser) : AuthState()
    data class Error(val message : String) : AuthState()
}


