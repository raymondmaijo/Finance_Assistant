package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.util.FileSaveResult
import com.example.util.ReportExporter

@Composable
fun SpreadsheetViewerDialog(
    fileResult: FileSaveResult,
    onShare: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rows = remember(fileResult.rawContent) {
        ReportExporter.parseCsvTable(fileResult.rawContent)
            .filter { row ->
                row.isNotEmpty() && !row.first().startsWith("====") && !row.first().startsWith("----")
            }
    }
    val maxCols = remember(rows) {
        (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(2)
    }
    val horizontalScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 20.dp)
                .testTag("spreadsheet_viewer_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldPrimary.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = "Spreadsheet",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileResult.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = fileResult.filePath ?: "Saved to Downloads/FinanceCoach",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_spreadsheet_viewer_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Spreadsheet"
                        )
                    }
                }

                // Action Bar: Open in Sheets/Excel & Share
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val uri = fileResult.fileUri
                            if (uri != null) {
                                val opened = ReportExporter.openFileExternally(context, uri, fileResult.mimeType)
                                if (!opened) {
                                    val chooserOpened = ReportExporter.openWithChooser(
                                        context,
                                        uri,
                                        fileResult.mimeType,
                                        "Open Spreadsheet in App"
                                    )
                                    if (!chooserOpened) {
                                        Toast.makeText(
                                            context,
                                            "No external spreadsheet app installed. Viewing in Finance Coach.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Saved at ${fileResult.filePath}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_external_spreadsheet_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in Sheets / Excel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (onShare != null) {
                        OutlinedButton(
                            onClick = onShare,
                            modifier = Modifier.testTag("spreadsheet_share_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share CSV",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 13.sp)
                        }
                    }
                }

                HorizontalDivider()

                // Spreadsheet Column Letter Header (A, B, C...)
                val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Excel-style column headers
                        item {
                            Row(
                                modifier = Modifier.background(headerBg)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(38.dp)
                                        .height(32.dp)
                                        .border(0.5.dp, borderColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                for (colIndex in 0 until maxCols) {
                                    val colLetter = ('A' + (colIndex % 26)).toString()
                                    Box(
                                        modifier = Modifier
                                            .width(columnWidthFor(colIndex))
                                            .height(32.dp)
                                            .border(0.5.dp, borderColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = colLetter,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Data rows
                        itemsIndexed(rows) { rowIndex, row ->
                            val isSectionHeader = row.size == 1 && row.first().uppercase() == row.first() && row.first().length > 3
                            val isTableColumnHeader = row.size >= 3 && (
                                row.first().equals("Category", ignoreCase = true) ||
                                    row.first().equals("Payment Method", ignoreCase = true) ||
                                    row.first().equals("ID", ignoreCase = true) ||
                                    row.first().equals("Metric", ignoreCase = true)
                                )
                            val rowBg = when {
                                isSectionHeader -> EmeraldPrimary.copy(alpha = 0.14f)
                                isTableColumnHeader -> EmeraldLight.copy(alpha = 0.16f)
                                rowIndex % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                else -> Color.Transparent
                            }

                            Row(modifier = Modifier.background(rowBg)) {
                                // Row index cell
                                Box(
                                    modifier = Modifier
                                        .width(38.dp)
                                        .height(36.dp)
                                        .background(headerBg.copy(alpha = 0.55f))
                                        .border(0.5.dp, borderColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rowIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                for (colIndex in 0 until maxCols) {
                                    val cellText = row.getOrNull(colIndex) ?: ""
                                    Box(
                                        modifier = Modifier
                                            .width(columnWidthFor(colIndex))
                                            .height(36.dp)
                                            .border(0.5.dp, borderColor)
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cellText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            fontWeight = if (isSectionHeader || isTableColumnHeader || colIndex == 0) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = if (isSectionHeader) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun columnWidthFor(colIndex: Int) = when (colIndex) {
    0 -> 150.dp
    1 -> 130.dp
    2 -> 110.dp
    3 -> 160.dp
    4 -> 120.dp
    else -> 125.dp
}
