package com.example.whatsapp.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.canhub.cropper.CropImageView
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NightProfileScreen(
    displayName: String,
    avatarPath: String? = null,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveAvatar: (String?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(displayName) { mutableStateOf(displayName) }
    var cropSource by remember { mutableStateOf<Uri?>(null) }
    var cropView by remember { mutableStateOf<CropImageView?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var savingAvatar by remember { mutableStateOf(false) }

    val avatarBitmap = remember(avatarPath) {
        avatarPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) cropSource = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { captured ->
        val source = pendingCameraFile
        pendingCameraFile = null
        if (captured && source != null && source.isFile && source.length() > 0L) {
            cropSource = Uri.fromFile(source)
        } else {
            source?.delete()
        }
    }

    fun launchCamera() {
        val source = File(
            context.cacheDir,
            "night_profile_camera_" + System.currentTimeMillis() + ".jpg",
        )
        pendingCameraFile?.delete()
        pendingCameraFile = source
        cameraLauncher.launch(
            FileProvider.getUriForFile(
                context,
                context.packageName + ".files",
                source,
            )
        )
    }

    fun saveCrop() {
        if (savingAvatar) return
        val bitmap = runCatching { cropView?.getCroppedImage() }.getOrNull() ?: return
        savingAvatar = true
        scope.launch {
            val output = withContext(Dispatchers.IO) {
                runCatching {
                    val squareSize = minOf(bitmap.width, bitmap.height)
                    val left = ((bitmap.width - squareSize) / 2).coerceAtLeast(0)
                    val top = ((bitmap.height - squareSize) / 2).coerceAtLeast(0)
                    val square = Bitmap.createBitmap(
                        bitmap,
                        left,
                        top,
                        squareSize,
                        squareSize,
                    )
                    val dir = File(context.filesDir, "night_profile").apply { mkdirs() }
                    val file = File(dir, "avatar.jpg")
                    FileOutputStream(file).use { stream ->
                        check(square.compress(Bitmap.CompressFormat.JPEG, 94, stream))
                    }
                    if (square !== bitmap) square.recycle()
                    file.absolutePath
                }.getOrNull()
            }
            bitmap.recycle()
            savingAvatar = false
            if (output != null) {
                pendingCameraFile?.delete()
                cropSource = null
                cropView = null
                onSaveAvatar(output)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F11))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE7EAEC))
            }
            Text("Profile", color = Color(0xFFE7EAEC), fontSize = 22.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = Color(0xFF171C1F),
                shape = CircleShape,
                modifier = Modifier.size(112.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap.asImageBitmap(),
                            contentDescription = "Your profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF21C063),
                            modifier = Modifier.size(54.dp),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfilePhotoAction(
                    label = "Gallery",
                    icon = Icons.Default.Image,
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                )
                ProfilePhotoAction(
                    label = "Camera",
                    icon = Icons.Default.PhotoCamera,
                    onClick = ::launchCamera,
                )
                if (avatarPath != null) {
                    ProfilePhotoAction(
                        label = "Remove",
                        icon = Icons.Default.Delete,
                        onClick = {
                            runCatching { File(avatarPath).delete() }
                            onSaveAvatar(null)
                        },
                    )
                }
            }

            if (cropSource != null) {
                Surface(
                    color = Color(0xFF111719),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Crop profile photo",
                            color = Color(0xFFE7EAEC),
                            fontSize = 15.sp,
                        )
                        AndroidView(
                            factory = { ctx ->
                                CropImageView(ctx).also { view ->
                                    view.guidelines = CropImageView.Guidelines.ON
                                    view.setImageUriAsync(cropSource)
                                    cropView = view
                                }
                            },
                            update = { view ->
                                if (cropView !== view) cropView = view
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    pendingCameraFile?.delete()
                                    cropSource = null
                                    cropView = null
                                },
                            ) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = ::saveCrop,
                                enabled = !savingAvatar,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF21C063),
                                    contentColor = Color(0xFF07110B),
                                ),
                            ) {
                                Text(if (savingAvatar) "Saving…" else "Use photo")
                            }
                        }
                    }
                }
            }

            Text(
                "This is the name Night uses for you.",
                color = Color(0xFF9CA5A9),
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(0xFFE7EAEC),
                    unfocusedTextColor = Color(0xFFE7EAEC),
                    cursorColor = Color(0xFF21C063),
                ),
            )

            Button(
                onClick = { onSaveName(name.trim()) },
                enabled = name.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF21C063),
                    contentColor = Color(0xFF07110B),
                ),
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text("Save name")
            }
        }
    }
}

@Composable
private fun ProfilePhotoAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        color = Color(0xFF171C1F),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF9CA5A9),
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = Color(0xFFE7EAEC), fontSize = 11.sp)
        }
    }
}
