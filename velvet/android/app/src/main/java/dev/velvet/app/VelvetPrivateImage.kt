package dev.velvet.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun decodePrivatePhoto(bytes:ByteArray,maxPx:Int):ImageBitmap {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    require(bounds.outWidth>0 && bounds.outHeight>0) {"The photo is damaged."}
    var sample=1
    while(bounds.outWidth/sample>maxPx || bounds.outHeight/sample>maxPx) sample*=2
    val options=BitmapFactory.Options().apply{inSampleSize=sample}
    val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        ?: throw IllegalStateException("Could not decode this photo.")
    return decoded.asImageBitmap()
}

/** In-memory-only previews of private Storage objects. Not a public URL or Story copy. */
@Composable
internal fun VelvetPrivateImage(
    client:VelvetPairClient,
    storagePath:String,
    modifier:Modifier=Modifier.width(242.dp).height(188.dp),
    thumbMaxDimension:Int=2048
) {
    var bitmap by remember(storagePath){mutableStateOf<ImageBitmap?>(null)}
    var error by remember(storagePath){mutableStateOf<String?>(null)}
    var loading by remember(storagePath){mutableStateOf(true)}
    var expanded by remember(storagePath){mutableStateOf(false)}
    var largeBitmap by remember(storagePath){mutableStateOf<ImageBitmap?>(null)}
    var expanding by remember(storagePath){mutableStateOf(false)}
    var attempt by remember(storagePath){mutableIntStateOf(0)}
    LaunchedEffect(storagePath,attempt) {
        error=null
        loading=true
        bitmap=null
        try {
            val decoded=withContext(Dispatchers.IO) {
                decodePrivatePhoto(client.downloadImage(storagePath),thumbMaxDimension)
            }
            bitmap=decoded
        } catch(e:Exception) {
            error=e.message ?: "Could not load this private photo."
        } finally { loading=false }
    }
    LaunchedEffect(expanded,storagePath) {
        if(!expanded) {
            largeBitmap=null
            expanding=false
        } else if(thumbMaxDimension<2048) {
            expanding=true
            try {
                largeBitmap=withContext(Dispatchers.IO) {
                    decodePrivatePhoto(client.downloadImage(storagePath),2048)
                }
            } catch(_:Exception) {
                // The already-loaded thumbnail remains available if offline.
            } finally {expanding=false}
        }
    }
    Box(modifier,contentAlignment=Alignment.Center) {
        if(bitmap!=null) {
            Image(bitmap!!,contentDescription="Private chat photo, open fullscreen",
                contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize().clickable{expanded=true})
        } else if(loading) {
            CircularProgressIndicator(color=Color(0xFFE4A1B6))
        } else {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(error?.take(80) ?: "Photo unavailable",color=Color(0xFFE4A1B6))
                TextButton(onClick={attempt++}) {
                    Text("Retry image",color=Color(0xFFFFEAF0))
                }
            }
        }
    }
    if(expanded && bitmap!=null) {
        Dialog(onDismissRequest={expanded=false},
            properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF120E16)),
                contentAlignment=Alignment.Center) {
                Image(largeBitmap ?: bitmap!!,
                    contentDescription="Full screen private chat photo",
                    contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())
                if(expanding) {
                    CircularProgressIndicator(
                        color=Color(0xFFE4A1B6),
                        modifier=Modifier.align(Alignment.BottomCenter).padding(42.dp)
                    )
                }
                IconButton(onClick={expanded=false},
                    modifier=Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                    Icon(Icons.Outlined.Close,contentDescription="Close photo",
                        tint=Color.White)
                }
            }
        }
    }
}
