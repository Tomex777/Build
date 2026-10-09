package eu.kanade.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Mihon v0.19.9 TabbedDialog presentation port.
 *
 * The untouched upstream source lives in mihon-upstream/. This standalone
 * version keeps its original tab row, animated horizontal pager, and sheet
 * structure. Material3's built-in modal sheet replaces Mihon's shared
 * Voyager/AdaptiveSheet navigation dependencies in Yomi.
 */
object TabbedDialogPaddings {
    val Horizontal = 24.dp
    val Vertical = 8.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedDialog(
    onDismissRequest: () -> Unit,
    tabTitles: List<String>,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { tabTitles.size },
    content: @Composable (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        val scope = rememberCoroutineScope()
        Column {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                divider = {},
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HorizontalDivider()
            HorizontalPager(
                modifier = Modifier.animateContentSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
            ) { page -> content(page) }
        }
    }
}
