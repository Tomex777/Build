package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import reader.shared.android.model.ChapterTransition
import reader.shared.android.model.ViewerChapter

/**
 * Presentation-port of Mihon v0.19.9 eu.kanade.presentation.reader.ChapterTransition:
 * same top/bottom chapter sections, chapter-gap warning, missing chapter card,
 * small heading/large title typography and the downloaded check icon.
 * Domain conversions/resource strings are localized to Yomi's offline model.
 */
@Composable
fun ReaderChapterTransition(transition: ChapterTransition) {
    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        when (transition) {
            is ChapterTransition.Prev -> ChapterTransitionText(
                topLabel = "Previous chapter",
                topChapter = transition.to,
                bottomLabel = "Current chapter",
                bottomChapter = transition.from,
                missingLabel = "No previous chapter",
                chapterGap = transition.to?.let {
                    (transition.from.chapter.order - it.chapter.order - 1).coerceAtLeast(0)
                } ?: 0,
            )
            is ChapterTransition.Next -> ChapterTransitionText(
                topLabel = "Finished",
                topChapter = transition.from,
                bottomLabel = "Next chapter",
                bottomChapter = transition.to,
                missingLabel = "No next chapter",
                chapterGap = transition.to?.let {
                    (it.chapter.order - transition.from.chapter.order - 1).coerceAtLeast(0)
                } ?: 0,
            )
        }
    }
}

@Composable
private fun ChapterTransitionText(
    topLabel: String,
    topChapter: ViewerChapter?,
    bottomLabel: String,
    bottomChapter: ViewerChapter?,
    missingLabel: String,
    chapterGap: Int,
) {
    Column(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
        if (topChapter != null) {
            ChapterText(topLabel, topChapter)
            Spacer(Modifier.height(24.dp))
        } else {
            MissingChapter(missingLabel, Modifier.align(Alignment.CenterHorizontally))
        }
        if (bottomChapter != null) {
            if (chapterGap > 0) {
                OutlinedCard(
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Warning, tint = MaterialTheme.colorScheme.error, contentDescription = null)
                        Text("$chapterGap missing chapter(s)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(24.dp))
            }
            ChapterText(bottomLabel, bottomChapter)
        } else {
            MissingChapter(missingLabel, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun MissingChapter(text: String, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChapterText(label: String, chapter: ViewerChapter) {
    Column {
        Text(
            label,
            modifier = Modifier.padding(bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Available offline",
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = chapter.chapter.title,
                fontSize = 20.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
