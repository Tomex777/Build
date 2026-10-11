package dev.velvet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The Chat tab has one clear entry point. Verified, fully paired accounts open
 * their real server-backed TEXT chat. Other users retain the local prototype.
 * Existing local messages are NOT automatically uploaded as if they were sent.
 */
@Composable
internal fun VelvetChatGateway(
    client:VelvetPairClient,
    onBack:()->Unit,
    onLocalPreview:()->Unit,
    localPreview:@Composable ()->Unit
) {
    var connected by remember {mutableStateOf<Boolean?>(null)}
    var error by remember {mutableStateOf("")}
    var checks by remember {mutableIntStateOf(0)}
    LaunchedEffect(checks) {
        connected=null
        error=""
        if(!client.configured) {
            connected=false
        } else {
            try {
                val hasAccount=client.hasSession()
                connected=hasAccount && (client.myPair()?.optInt("member_count")==2)
            } catch(e:Exception) {
                error="Couldn't check the connected chat. The local preview is still available."
                connected=false
            }
        }
    }
    when(connected) {
        true -> VelvetConnectedChat(onBack=onBack,
            onOpenLocalPreview=onLocalPreview,sharedClient=client)
        false -> {
            Column(Modifier.fillMaxSize().background(Color(0xFF171019))) {
                if(error.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(error,color=Color(0xFFF0C7D4),
                            modifier=Modifier.weight(1f))
                        TextButton(onClick={checks++}) {Text("Retry")}
                    }
                }
                Box(Modifier.weight(1f)) { localPreview() }
            }
        }
        null -> Box(Modifier.fillMaxSize().background(Color(0xFF171019)),
            contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                CircularProgressIndicator(color=Color(0xFFDDA1B7))
                Spacer(Modifier.height(14.dp))
                Text("Finding our space…",color=Color(0xFFFFE3EA))
            }
        }
    }
}
