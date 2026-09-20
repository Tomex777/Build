package com.night.homira.call

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object HomiraCallUiState {
    @Volatile
    var videoCallActive: Boolean = false

    var pictureInPictureActive by mutableStateOf(false)
}
