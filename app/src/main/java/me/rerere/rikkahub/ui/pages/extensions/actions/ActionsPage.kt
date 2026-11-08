package me.rerere.rikkahub.ui.pages.extensions.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ActionIcons
import me.rerere.rikkahub.data.datastore.BuiltInActions
import me.rerere.rikkahub.data.datastore.CustomAction
import me.rerere.rikkahub.data.datastore.CustomMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun ActionsPage(vm: ActionsVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showAddActionDialog by rememberSaveable { mutableStateOf(false) }
    var showAddModeDialog by rememberSaveable { mutableStateOf(false) }
    var editActionTarget by remember { mutableStateOf<CustomAction?>(null) }
    var editModeTarget by remember { mutableStateOf<CustomMode?>(null) }
    var deleteActionTarget by remember { mutableStateOf<CustomAction?>(null) }
    var deleteModeTarget by remember { mutableStateOf<CustomMode?>(null) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.actions_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { showAddModeDialog = true },
                ) {
                    Icon(
                        HugeIcons.Edit03,
                        contentDescription = stringResource(R.string.actions_page_add_mode),
                    )
                }
                FloatingActionButton(
                    onClick = { showAddActionDialog = true },
                ) {
                    Icon(
                        HugeIcons.Add01,
                        contentDescription = stringResource(R.string.actions_page_add_action),
                    )
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: Built-in Actions
            item {
                CardGroup(
                    title = { Text(stringResource(R.string.actions_page_section_builtin)) },
                ) {
                    BuiltInActions.ALL.forEach { action ->
                        val isEnabled = action.id !in settings.disabledBuiltInActions
                        item(
                            leadingContent = {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = action.command,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        vm.toggleBuiltInAction(action.id, checked)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            // Section 2: Custom Actions
            item {
                CardGroup(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.actions_page_section_custom_actions))
                            IconButton(onClick = { showAddActionDialog = true }) {
                                Icon(
                                    HugeIcons.Add01,
                                    contentDescription = stringResource(R.string.actions_page_add_action),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                ) {
                    if (settings.customActions.isEmpty()) {
                        item(
                            leadingContent = {
                                Icon(
                                    imageVector = HugeIcons.Zap,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.actions_page_no_custom_actions),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.actions_page_empty_actions_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            },
                        )
                    } else {
                        settings.customActions.forEach { action ->
                            item(
                                onClick = { editActionTarget = action },
                                leadingContent = {
                                    Icon(
                                        imageVector = ActionIcons.get(action.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = action.prefix,
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${action.name} — ${action.promptTemplate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = action.enabled,
                                            onCheckedChange = { checked ->
                                                vm.toggleCustomAction(action.id, checked)
                                            },
                                        )
                                        IconButton(onClick = { deleteActionTarget = action }) {
                                            Icon(
                                                imageVector = HugeIcons.Delete01,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Section 3: Custom Modes
            item {
                CardGroup(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.actions_page_section_custom_modes))
                            IconButton(onClick = { showAddModeDialog = true }) {
                                Icon(
                                    HugeIcons.Add01,
                                    contentDescription = stringResource(R.string.actions_page_add_mode),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                ) {
                    if (settings.customModes.isEmpty()) {
                        item(
                            leadingContent = {
                                Icon(
                                    imageVector = HugeIcons.Tools,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.actions_page_no_custom_modes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.actions_page_empty_modes_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            },
                        )
                    } else {
                        settings.customModes.forEach { mode ->
                            item(
                                onClick = { editModeTarget = mode },
                                leadingContent = {
                                    Icon(
                                        imageVector = ActionIcons.get(mode.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (mode.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = mode.name,
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = mode.systemPromptAddendum,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = mode.enabled,
                                            onCheckedChange = { checked ->
                                                vm.toggleCustomMode(mode.id, checked)
                                            },
                                        )
                                        IconButton(onClick = { deleteModeTarget = mode }) {
                                            Icon(
                                                imageVector = HugeIcons.Delete01,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddActionDialog) {
        CustomActionDialog(
            title = stringResource(R.string.actions_page_add_action),
            initialAction = null,
            onDismiss = { showAddActionDialog = false },
            onConfirm = { name, prefix, icon, promptTemplate ->
                vm.addCustomAction(name, prefix, icon, promptTemplate)
                showAddActionDialog = false
            },
        )
    }

    editActionTarget?.let { target ->
        CustomActionDialog(
            title = stringResource(R.string.actions_page_edit_action),
            initialAction = target,
            onDismiss = { editActionTarget = null },
            onConfirm = { name, prefix, icon, promptTemplate ->
                vm.updateCustomAction(
                    target.copy(
                        name = name,
                        prefix = prefix,
                        icon = icon,
                        promptTemplate = promptTemplate,
                    )
                )
                editActionTarget = null
            },
        )
    }

    if (showAddModeDialog) {
        CustomModeDialog(
            title = stringResource(R.string.actions_page_add_mode),
            initialMode = null,
            onDismiss = { showAddModeDialog = false },
            onConfirm = { name, icon, systemPromptAddendum ->
                vm.addCustomMode(name, icon, systemPromptAddendum)
                showAddModeDialog = false
            },
        )
    }

    editModeTarget?.let { target ->
        CustomModeDialog(
            title = stringResource(R.string.actions_page_edit_mode),
            initialMode = target,
            onDismiss = { editModeTarget = null },
            onConfirm = { name, icon, systemPromptAddendum ->
                vm.updateCustomMode(
                    target.copy(
                        name = name,
                        icon = icon,
                        systemPromptAddendum = systemPromptAddendum,
                    )
                )
                editModeTarget = null
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteActionTarget != null,
        title = stringResource(R.string.actions_page_delete_action_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteActionTarget?.let { vm.deleteCustomAction(it.id) }
            deleteActionTarget = null
        },
        onDismiss = { deleteActionTarget = null },
    ) {
        Text(stringResource(R.string.actions_page_delete_action_message, deleteActionTarget?.name ?: ""))
    }

    RikkaConfirmDialog(
        show = deleteModeTarget != null,
        title = stringResource(R.string.actions_page_delete_mode_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteModeTarget?.let { vm.deleteCustomMode(it.id) }
            deleteModeTarget = null
        },
        onDismiss = { deleteModeTarget = null },
    ) {
        Text(stringResource(R.string.actions_page_delete_mode_message, deleteModeTarget?.name ?: ""))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomActionDialog(
    title: String,
    initialAction: CustomAction?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, prefix: String, icon: String, promptTemplate: String) -> Unit,
) {
    var name by rememberSaveable(initialAction?.id) { mutableStateOf(initialAction?.name ?: "") }
    var prefix by rememberSaveable(initialAction?.id) { mutableStateOf(initialAction?.prefix ?: "") }
    var selectedIcon by rememberSaveable(initialAction?.id) { mutableStateOf(initialAction?.icon ?: "Tools") }
    var promptTemplate by rememberSaveable(initialAction?.id) { mutableStateOf(initialAction?.promptTemplate ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName ->
                        name = newName
                        if (prefix.isBlank() || prefix == "/${name.lowercase().dropLast(1)}") {
                            prefix = "/${newName.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
                        }
                    },
                    label = { Text(stringResource(R.string.actions_page_name_label)) },
                    placeholder = { Text("audit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text(stringResource(R.string.actions_page_prefix_label)) },
                    placeholder = { Text("/audit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.actions_page_icon_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ActionIcons.AVAILABLE_ICONS.forEach { (iconKey, vector) ->
                        val selected = selectedIcon.equals(iconKey, ignoreCase = true)
                        Surface(
                            selected = selected,
                            onClick = { selectedIcon = iconKey },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = iconKey,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = promptTemplate,
                    onValueChange = { promptTemplate = it },
                    label = { Text(stringResource(R.string.actions_page_prompt_label)) },
                    placeholder = { Text("Audit this codebase for security vulnerabilities, logic flaws, and leaked credentials.") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), prefix.trim(), selectedIcon, promptTemplate.trim()) },
                enabled = name.isNotBlank() && prefix.isNotBlank() && promptTemplate.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomModeDialog(
    title: String,
    initialMode: CustomMode?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, systemPromptAddendum: String) -> Unit,
) {
    var name by rememberSaveable(initialMode?.id) { mutableStateOf(initialMode?.name ?: "") }
    var selectedIcon by rememberSaveable(initialMode?.id) { mutableStateOf(initialMode?.icon ?: "Edit03") }
    var systemPromptAddendum by rememberSaveable(initialMode?.id) { mutableStateOf(initialMode?.systemPromptAddendum ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.actions_page_mode_name_label)) },
                    placeholder = { Text("Architect") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.actions_page_icon_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ActionIcons.AVAILABLE_ICONS.forEach { (iconKey, vector) ->
                        val selected = selectedIcon.equals(iconKey, ignoreCase = true)
                        Surface(
                            selected = selected,
                            onClick = { selectedIcon = iconKey },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = iconKey,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = systemPromptAddendum,
                    onValueChange = { systemPromptAddendum = it },
                    label = { Text(stringResource(R.string.actions_page_system_prompt_label)) },
                    placeholder = {
                        Text(
                            "[AGENT MODE: ARCHITECT]\nFocus on high-level system architecture, data models, API boundaries, and trade-off analysis.",
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selectedIcon, systemPromptAddendum.trim()) },
                enabled = name.isNotBlank() && systemPromptAddendum.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
