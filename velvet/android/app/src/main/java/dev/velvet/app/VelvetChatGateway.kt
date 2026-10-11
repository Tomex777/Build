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
            if(error.isNotEmpty()) {
                // Never silently substitute a LOCAL chat after failure to check
                // a saved online couple. Messages might appear sent but cannot
                // reach the partner. A local demo must be consciously opened.
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF171019))
                        .padding(23.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.Center
                ) {
                    Text("Couldn't reach our connected space",
                        color=Color(0xFFFFE3EA),
                        style=MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your previous conversation and sign-in remain safe. "+
                            "Check your connection and retry. Local preview messages "+
                            "are never delivered to your partner.",
                        color=Color(0xFFF0C7D4)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick={checks++},modifier=Modifier.fillMaxWidth()) {
                        Text("Try connected Chat again")
                    }
                    TextButton(onClick=onLocalPreview) {
                        Text("Open offline demo instead")
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().background(Color(0xFF171019))) {
                    Text(
                        if(!client.configured)
                            "LOCAL PREVIEW ONLY · Online messaging isn't configured"
                        else
                            "LOCAL PREVIEW ONLY · Pair two signed-in accounts to send messages",
                        color=Color(0xFFF0C7D4),
                        modifier=Modifier.fillMaxWidth()
                            .padding(horizontal=15.dp,vertical=9.dp),
                        style=MaterialTheme.typography.labelSmall
                    )
                    Box(Modifier.weight(1f)) { localPreview() }
                }
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
