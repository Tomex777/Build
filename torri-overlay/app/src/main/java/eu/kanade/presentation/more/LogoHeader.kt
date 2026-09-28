package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

/**
 * The single Compose entry point for Torri's canonical brand artwork.
 *
 * Keep branded surfaces on this component/resource instead of redrawing a torii
 * independently in each screen.
 */
@Composable
fun TorriBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(R.drawable.ic_torri),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
fun LogoHeader(
    iconPadding: PaddingValues = PaddingValues(),
    markSize: Dp = 96.dp,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TorriBrandMark(
            modifier = Modifier
                .padding(iconPadding)
                .size(markSize),
        )

        HorizontalDivider()
    }
}
