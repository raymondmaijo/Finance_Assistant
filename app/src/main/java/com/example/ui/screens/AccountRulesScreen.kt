package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AccountRuleEntity
import com.example.data.model.LearnedMappingEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIcons
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.SkyTertiary
import com.example.ui.viewmodel.MainViewModel

private val ALL_RULE_CATEGORIES = listOf(
    "Food",
    "People",
    "Entertainment",
    "Transportation",
    "Shopping",
    "Investments",
    "Education",
    "Bills",
    "Healthcare",
    "Miscellaneous"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountRulesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val rules by viewModel.allRules.collectAsStateWithLifecycle()
    val learnedMappings by viewModel.allLearnedMappings.collectAsStateWithLifecycle()
    var isAddingRule by remember { mutableStateOf(false) }

    var ruleType by remember { mutableStateOf("RECEIVER") } // "RECEIVER" or "ACCOUNT"
    var accountIdInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var ruleDescription by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("account_rules_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)
    ) {
        // Explainer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rules_explainer_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SkyTertiary.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Rule,
                        contentDescription = null,
                        tint = SkyTertiary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Permanent Receiver & Account Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Define permanent rules like 'Rahul Kumar → Food', 'ABC Stores → Shopping', or 'Groww → Investments'. Whenever these appear in an SMS, Finance Coach automatically assigns the saved category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Preset Chips
        item {
            Column {
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                ruleType = "RECEIVER"
                                accountIdInput = "Rahul Kumar"
                                selectedCategory = "Food"
                                ruleDescription = "Always categorize Rahul Kumar as Food"
                                isAddingRule = true
                            },
                            label = { Text("Rahul Kumar → Food") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                ruleType = "RECEIVER"
                                accountIdInput = "ABC Stores"
                                selectedCategory = "Shopping"
                                ruleDescription = "Always categorize ABC Stores as Shopping"
                                isAddingRule = true
                            },
                            label = { Text("ABC Stores → Shopping") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                ruleType = "RECEIVER"
                                accountIdInput = "Groww"
                                selectedCategory = "Investments"
                                ruleDescription = "Always categorize Groww as Investments"
                                isAddingRule = true
                            },
                            label = { Text("Groww → Investments") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                ruleType = "ACCOUNT"
                                accountIdInput = "X1234"
                                selectedCategory = "Food"
                                ruleDescription = "Campus dining account X1234"
                                isAddingRule = true
                            },
                            label = { Text("X1234 → Food") }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Active Rules Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permanent Rules (${rules.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        ruleType = "RECEIVER"
                        accountIdInput = ""
                        selectedCategory = "Food"
                        ruleDescription = ""
                        isAddingRule = true
                    },
                    modifier = Modifier.testTag("create_new_rule_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Rule")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Rules List
        if (rules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SkyTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Permanent Rules Defined",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create rules for recurring receivers or accounts (e.g. 'Rahul Kumar → Food' or 'ABC Stores → Shopping').",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = {
                            viewModel.saveRule("Rahul Kumar", "Food", "RECEIVER")
                            viewModel.saveRule("ABC Stores", "Shopping", "RECEIVER")
                            viewModel.saveRule("Groww", "Investments", "RECEIVER")
                        }) {
                            Text("Add Default Rules (Rahul Kumar, ABC Stores, Groww)")
                        }
                    }
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                PermanentRuleItemCard(
                    rule = rule,
                    onToggle = { viewModel.updateAccountRule(rule.copy(isEnabled = !rule.isEnabled)) },
                    onDelete = { viewModel.deleteAccountRule(rule.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Learned From User Corrections Section
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = EmeraldLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Learned From Your Corrections (${learnedMappings.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Whenever you fix a transaction's category during PDF/CSV import or in Transactions, Finance Coach remembers it here for future imports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (learnedMappings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_learned_rules_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "No learned rules yet. Tap any transaction to correct its category and teach Finance Coach automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(learnedMappings, key = { "learned_${it.merchantNormalized}" }) { mapping ->
                LearnedMappingCard(
                    mapping = mapping,
                    onDelete = { viewModel.deleteLearnedMapping(mapping.merchantNormalized) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Add Rule Dialog
    if (isAddingRule) {
        AlertDialog(
            onDismissRequest = { isAddingRule = false },
            title = { Text("Create Permanent Rule") },
            text = {
                Column {
                    Text(
                        text = "Rule Target Type",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = ruleType == "RECEIVER",
                            onClick = { ruleType = "RECEIVER" },
                            label = { Text("Receiver Name") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = ruleType == "ACCOUNT",
                            onClick = { ruleType = "ACCOUNT" },
                            label = { Text("Account / Card") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (ruleType == "RECEIVER") "Receiver Name" else "Account / Card / UPI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = accountIdInput,
                        onValueChange = { accountIdInput = it },
                        label = {
                            Text(if (ruleType == "RECEIVER") "e.g. Rahul Kumar, ABC Stores, Groww" else "e.g. X1234 or rahul@upi")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rule_account_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Target Category",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("rule_category_selector")
                        )
                        ExposedDropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            ALL_RULE_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Description (Optional)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = ruleDescription,
                        onValueChange = { ruleDescription = it },
                        label = { Text("e.g. Daily lunch dues or SIP") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldLight.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Future SMS with '${accountIdInput.ifBlank { "this name" }}' will automatically be categorized as '$selectedCategory'.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (accountIdInput.isNotBlank()) {
                            viewModel.saveRule(accountIdInput, selectedCategory, ruleType, ruleDescription)
                            isAddingRule = false
                        }
                    },
                    modifier = Modifier.testTag("save_rule_confirm_btn")
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddingRule = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PermanentRuleItemCard(
    rule: AccountRuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CategoryIcons.getColor(rule.categoryName).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryIcons.getIcon(rule.categoryName),
                    contentDescription = null,
                    tint = CategoryIcons.getColor(rule.categoryName),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.accountIdentifier,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (rule.ruleType == "ACCOUNT") "Account" else "Receiver",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    CategoryBadge(category = rule.categoryName)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = rule.description.ifBlank { "Automatically assigns ${rule.categoryName}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("rule_switch_${rule.id}")
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Rule",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LearnedMappingCard(
    mapping: LearnedMappingEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learned_rule_${mapping.merchantNormalized}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(EmeraldLight.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryIcons.getIcon(mapping.categoryName),
                    contentDescription = null,
                    tint = CategoryIcons.getColor(mapping.categoryName),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mapping.sampleMerchant.ifBlank { mapping.merchantNormalized },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CategoryBadge(category = mapping.categoryName)
                }
                Spacer(modifier = Modifier.height(2.dp))
                val detail = buildString {
                    append("Pattern: '${mapping.merchantNormalized}' • Applied ${mapping.correctionCount}x")
                    if (mapping.originalCategory.isNotBlank() && mapping.originalCategory != mapping.categoryName) {
                        append(" (was ${mapping.originalCategory})")
                    }
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("delete_learned_${mapping.merchantNormalized}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Forget learned rule",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
