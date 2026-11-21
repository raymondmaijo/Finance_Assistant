package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.CategorizationSource
import com.example.ui.components.CategoryBadge
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ImportedTransactionDraft
import com.example.util.StatementImportEngine
import com.example.util.StatementSourceFormat

@Composable
fun StatementImportScreen(
    viewModel: MainViewModel,
    onImportCompleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preview by viewModel.importPreview.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    var selectedFormat by remember { mutableStateOf(StatementSourceFormat.CSV) }
    var pastedText by remember { mutableStateOf("") }
    var draftBeingEdited by remember { mutableStateOf<ImportedTransactionDraft?>(null) }

    // Zero-permission Android SAF Document Pickers for PDF and CSV
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = resolveFileName(context, uri) ?: "BankStatement.pdf"
            viewModel.parseStatementUri(context, uri, StatementSourceFormat.PDF, name)
        }
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = resolveFileName(context, uri) ?: "BankStatement.csv"
            viewModel.parseStatementUri(context, uri, StatementSourceFormat.CSV, name)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("statement_import_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header explainer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Messy Bank Statement Importer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ingest bank PDFs, messy CSVs (with preambles, quoted commas, Dr/Cr columns), or bulk SMS. Fix any category in preview and Finance Coach learns the rule for future imports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Format Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormatChip(
                    selected = selectedFormat == StatementSourceFormat.CSV,
                    label = "CSV",
                    icon = Icons.Default.TableChart,
                    onClick = { selectedFormat = StatementSourceFormat.CSV },
                    modifier = Modifier.weight(1f).testTag("import_format_csv")
                )
                FormatChip(
                    selected = selectedFormat == StatementSourceFormat.PDF,
                    label = "PDF",
                    icon = Icons.Default.PictureAsPdf,
                    onClick = { selectedFormat = StatementSourceFormat.PDF },
                    modifier = Modifier.weight(1f).testTag("import_format_pdf")
                )
                FormatChip(
                    selected = selectedFormat == StatementSourceFormat.SMS,
                    label = "Bulk SMS",
                    icon = Icons.Default.Sms,
                    onClick = { selectedFormat = StatementSourceFormat.SMS },
                    modifier = Modifier.weight(1f).testTag("import_format_sms")
                )
            }
        }

        // File Picker & Quick Sample Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (selectedFormat) {
                            StatementSourceFormat.PDF -> "Upload Bank Statement PDF"
                            StatementSourceFormat.CSV -> "Upload Bank Statement CSV"
                            StatementSourceFormat.SMS -> "Paste Bulk Bank / UPI SMS Messages"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (selectedFormat) {
                            StatementSourceFormat.PDF -> "Reads text & compressed PDF streams locally on device using zero-permission document picker."
                            StatementSourceFormat.CSV -> "Handles HDFC, SBI, ICICI, Axis & international CSVs with header preambles, semicolons, and messy UPI narrations."
                            StatementSourceFormat.SMS -> "Paste multiple SMS messages at once. OTPs, promos & failed alerts are automatically filtered out."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedFormat != StatementSourceFormat.SMS) {
                        Button(
                            onClick = {
                                if (selectedFormat == StatementSourceFormat.PDF) {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                } else {
                                    csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_statement_file_btn")
                        ) {
                            Icon(
                                imageVector = if (selectedFormat == StatementSourceFormat.PDF) Icons.Default.PictureAsPdf else Icons.Default.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedFormat == StatementSourceFormat.PDF) "Choose PDF File" else "Choose CSV File",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Paste messy raw text option
                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        label = {
                            Text(
                                when (selectedFormat) {
                                    StatementSourceFormat.PDF -> "Or paste copied PDF statement text..."
                                    StatementSourceFormat.CSV -> "Or paste raw messy CSV lines..."
                                    StatementSourceFormat.SMS -> "Paste one or more bank/UPI SMS messages..."
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("paste_statement_input"),
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val sample = when (selectedFormat) {
                                    StatementSourceFormat.CSV -> StatementImportEngine.sampleMessyCsv()
                                    StatementSourceFormat.PDF -> StatementImportEngine.sampleMessyPdfText()
                                    StatementSourceFormat.SMS -> StatementImportEngine.sampleBulkMessySms()
                                }
                                pastedText = sample
                                viewModel.parseRawStatementText(
                                    rawText = sample,
                                    format = selectedFormat,
                                    sourceLabel = "Sample ${selectedFormat.label}"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("load_messy_sample_btn")
                        ) {
                            Text("Try Messy Sample")
                        }

                        Button(
                            onClick = {
                                viewModel.parseRawStatementText(
                                    rawText = pastedText,
                                    format = selectedFormat,
                                    sourceLabel = "Pasted ${selectedFormat.label}"
                                )
                            },
                            enabled = pastedText.isNotBlank() && !isImporting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parse_pasted_statement_btn")
                        ) {
                            Text("Parse & Preview")
                        }
                    }
                }
            }
        }

        if (isImporting) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Parsing & auto-categorizing statement...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Parsed Preview Results
        if (preview != null) {
            val activePreview = preview!!
            val selectedCount = activePreview.drafts.count { it.isSelected }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("import_preview_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = EmeraldPrimary.copy(alpha = 0.12f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Preview: ${activePreview.fileName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${activePreview.drafts.size} parsed • ${activePreview.duplicateCount} duplicate(s) skipped by default",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.clearImportPreview() }) {
                                Text("Clear")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tap any category chip below to fix it & teach Finance Coach for future imports.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                viewModel.commitImportedDrafts()
                                onImportCompleted()
                            },
                            enabled = selectedCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("commit_import_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import $selectedCount Selected Transaction(s)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(activePreview.drafts, key = { it.id }) { draft ->
                ImportedDraftCard(
                    draft = draft,
                    onToggleSelect = { viewModel.toggleDraftSelection(draft.id) },
                    onEditCategory = { draftBeingEdited = draft }
                )
            }
        }
    }

    // Inline Category Fix & Rule Learning Dialog
    if (draftBeingEdited != null) {
        val target = draftBeingEdited!!
        var learnRule by remember(target.id) { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { draftBeingEdited = null },
            title = { Text("Fix Category & Teach Rule") },
            text = {
                Column {
                    Text(
                        text = "${target.cleanedMerchant} (${Formatters.formatRupee(target.amount)})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Raw: ${target.rawNarration}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { learnRule = !learnRule }
                    ) {
                        Checkbox(checked = learnRule, onCheckedChange = { learnRule = it })
                        Text(
                            text = "Learn rule for future PDF/CSV/SMS imports",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ALL_CATEGORIES) { cat ->
                            val isCurrent = cat.equals(target.category, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateDraftCategory(target.id, cat, learnRule)
                                        draftBeingEdited = null
                                    }
                                    .testTag("pick_draft_cat_$cat")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { draftBeingEdited = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun FormatChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        label = { Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1) },
        modifier = modifier
    )
}

@Composable
private fun ImportedDraftCard(
    draft: ImportedTransactionDraft,
    onToggleSelect: () -> Unit,
    onEditCategory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("import_draft_${draft.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (draft.isDuplicate) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = draft.isSelected, onCheckedChange = { onToggleSelect() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.cleanedMerchant,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${draft.dateDisplay} • ${draft.paymentMethod}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val isDebit = draft.type.equals("DEBIT", ignoreCase = true)
                Text(
                    text = "${if (isDebit) "- " else "+ "}${Formatters.formatRupee(draft.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDebit) ExpenseRed else IncomeGreen
                )
            }

            Text(
                text = draft.rawNarration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEditCategory() }
                        .padding(vertical = 2.dp)
                        .testTag("fix_draft_category_btn")
                ) {
                    CategoryBadge(category = draft.category)
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Fix Category",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    val sourceLabel = when {
                        draft.userCorrected -> "Learned"
                        draft.categorizationSource == CategorizationSource.USER_LEARNED -> "Learned Rule"
                        draft.categorizationSource == CategorizationSource.RECEIVER_RULE ||
                            draft.categorizationSource == CategorizationSource.ACCOUNT_RULE -> "Permanent Rule"
                        else -> "Auto"
                    }
                    Text(
                        text = "• $sourceLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draft.categorizationSource == CategorizationSource.USER_LEARNED || draft.userCorrected) EmeraldLight else SkyTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (draft.isDuplicate) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = WarningOrange.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Duplicate", style = MaterialTheme.typography.labelSmall, color = WarningOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}
