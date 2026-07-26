package com.pdfapp.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfapp.data.RecentFile
import com.pdfapp.ui.common.ReaderSemantics
import com.pdfapp.ui.common.appVersionLabel
import com.pdfapp.ui.common.scaledDp
import com.pdfapp.ui.common.touchTargetFloor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Landing screen when no document is open: the picker button plus the
 * recently opened list with resume positions (plan 2.7).
 */
@Composable
fun HomeScreen(
    recents: List<RecentFile>,
    onOpenClick: () -> Unit,
    onRecentClick: (RecentFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize().padding(24.dp),
    ) {
        Text(
            "View, sign and annotate PDFs — entirely on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Sideloaded builds arrive without a store listing to check, so the app
        // states which one it is: the same string CI stamps as the versionName
        // ("1.4.1", or "1.4.1 (build 102)" for a rolling build).
        Text(
            appVersionLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenClick, modifier = Modifier.touchTargetFloor()) { Text("Open PDF") }
        if (recents.isEmpty()) {
            Text(
                "Documents you open will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Recent files",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(recents, key = { it.uri }) { recent ->
                    // One spoken label for the row, so TalkBack reads
                    // "name, 12 pages, last read page 3" instead of sounding out
                    // the "·" separator (mobile-ui-plan Phase F.2).
                    val label =
                        ReaderSemantics.recentFileLabel(
                            displayName = recent.displayName,
                            pageCount = recent.pageCount,
                            lastPageIndex = recent.lastPageIndex,
                        )
                    ListItem(
                        leadingContent = { RecentThumbnail(recent.thumbnailPath) },
                        headlineContent = {
                            // Two lines: file names are long and the row already
                            // grows to fit them (Phase F.1).
                            Text(recent.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text("${recent.pageCount} pages · last read page ${recent.lastPageIndex + 1}")
                        },
                        tonalElevation = 1.dp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable(role = Role.Button) { onRecentClick(recent) }
                                .semantics(mergeDescendants = true) { contentDescription = label },
                    )
                }
            }
        }
    }
}

/** Page-1 thumbnail loaded off the main thread; blank until decoded. */
@Composable
@Suppress("ProduceStateDoesNotAssignValue") // value is assigned; lint misreads the nested lambda
private fun RecentThumbnail(path: String?) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value =
            path?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val file = File(it)
                        if (file.exists()) BitmapFactory.decodeFile(it)?.asImageBitmap() else null
                    }.getOrNull()
                }
            }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Grows with the font scale so it stays in proportion to the row's
            // text rather than shrinking into a stamp at 2× (Phase F.1).
            modifier = Modifier.width(scaledDp(THUMB_WIDTH_DP)).height(scaledDp(THUMB_HEIGHT_DP)),
        )
    }
}

private const val THUMB_WIDTH_DP = 44
private const val THUMB_HEIGHT_DP = 56
