package com.example.whatsapp.presentation.profile

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TextButton
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.whatsapp.R
import coil.compose.rememberAsyncImagePainter
import androidx.navigation.NavHostController
import coil.compose.rememberImagePainter
import com.example.whatsapp.presentation.navigation.Routes
import com.example.whatsapp.presentation.viewmodels.PhoneAuthViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun userProfilesetScreen(
    phoneAuthViewModel: PhoneAuthViewModel = hiltViewModel(),
    navHostController: NavHostController,
    isSetupMode: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmapImage by remember { mutableStateOf<Bitmap?>(null) }
    var isEditing by remember { mutableStateOf(isSetupMode) } // In setup mode, always editing

    val firebaseAuth = Firebase.auth
    val phoneNumber = firebaseAuth.currentUser?.phoneNumber ?: ""
    val userid = firebaseAuth.currentUser?.uid ?: ""
    val context = LocalContext.current

    // Load existing profile data when in edit mode (not setup mode)
    LaunchedEffect(userid, isSetupMode) {
        if (!isSetupMode && userid.isNotEmpty()) {
            phoneAuthViewModel.getUserProfile(userid) { userProfile ->
                if (userProfile != null) {
                    name = userProfile.name ?: ""
                    status = userProfile.status ?: ""
                    // Load profile image if exists
                    userProfile.profileImage?.let { base64Image ->
                        val bitmap = phoneAuthViewModel.base64ToBitmap(base64Image)
                        if (bitmap != null) {
                            bitmapImage = bitmap
                        }
                    }
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            profileImageUri = uri
            uri?.let {
                bitmapImage = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
            }
        }
    )

    Scaffold(
        topBar = {
            if (!isSetupMode) {
                // Only show top bar in edit mode (not setup mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(top = 30.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navHostController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_24),
                            contentDescription = "Back",
                            tint = colorResource(R.color.light_green)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Profile",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isEditing) {
                        TextButton(
                            onClick = { isEditing = true }
                        ) {
                            Text("Edit", color = colorResource(R.color.light_green))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(Color.White)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Add top padding for both modes
            Spacer(modifier = Modifier.height(if (isSetupMode) 32.dp else 16.dp))
            
            // Title for setup mode
            if (isSetupMode) {
                Text(
                    text = "Profile Setup",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please provide your information to get started",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Profile Picture
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .border(2.dp, color = colorResource(R.color.light_green), shape = CircleShape)
                    .clickable(enabled = isEditing || isSetupMode) { imagePickerLauncher.launch("image/*") }
            ) {
                if (bitmapImage != null) {
                    Image(
                        bitmap = bitmapImage!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (profileImageUri != null) {
                    Image(
                        painter = rememberImagePainter(profileImageUri),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.profile_placeholder),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = phoneNumber,
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Name Field
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEditing || isSetupMode,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = if (isEditing || isSetupMode) Color.White else Color(0xFFF5F5F5),
                    focusedContainerColor = Color.White,
                    focusedIndicatorColor = colorResource(R.color.light_green),
                    unfocusedIndicatorColor = if (isEditing || isSetupMode) Color.Gray else Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status/Bio Field
            TextField(
                value = status,
                onValueChange = { status = it },
                label = { Text(if (isSetupMode) "Status" else "About") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEditing || isSetupMode,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = if (isEditing || isSetupMode) Color.White else Color(0xFFF5F5F5),
                    focusedContainerColor = Color.White,
                    focusedIndicatorColor = colorResource(R.color.light_green),
                    unfocusedIndicatorColor = if (isEditing || isSetupMode) Color.Gray else Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save Button (when editing or in setup mode)
            if (isEditing || isSetupMode) {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            phoneAuthViewModel.saveUserProfile(userid, name, status, bitmapImage)
                            if (isSetupMode) {
                                // Navigate to Home screen after setup
                                navHostController.navigate(Routes.HomeScreen.route) {
                                    popUpTo(Routes.UserProfileSetScreen.route) { inclusive = true }
                                }
                            } else {
                                isEditing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.light_green))
                ) {
                    Text(
                        text = if (isSetupMode) "Continue" else "Save",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        }
    }
}

