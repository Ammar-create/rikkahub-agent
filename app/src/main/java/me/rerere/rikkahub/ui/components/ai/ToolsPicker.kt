package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.AiSearch02
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getDefaultImageModel
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.SearchAbilityTagLine

enum class SearchMode {
    OFF,
    LOCAL,
    BUILT_IN,
}

@Composable
fun ToolsPickerButton(
    enableSearch: Boolean,
    settings: Settings,
    modifier: Modifier = Modifier,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onUpdateImageGeneration: (Boolean) -> Unit,
    onUpdateImageModel: (Model) -> Unit,
    onUpdateImageAspectRatio: (String) -> Unit,
    model: Model?,
    assistant: Assistant = settings.getCurrentAssistant(),
) {
    var showToolsPicker by remember { mutableStateOf(false) }
    val isSearchActive = enableSearch || model?.tools?.contains(BuiltInTools.Search) == true
    val isImageGenActive = assistant.localTools.contains(LocalToolOption.ImageGeneration)
    val isAnyActive = isSearchActive || isImageGenActive

    ToggleSurface(
        modifier = modifier,
        checked = isAnyActive,
        onClick = { showToolsPicker = true },
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HugeIcons.Tools,
                    contentDescription = stringResource(R.string.tools_picker_title),
                )
            }
        }
    }

    if (showToolsPicker) {
        ModalBottomSheet(
            onDismissRequest = { showToolsPicker = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
        ) {
            var selectingProvider by remember { mutableStateOf(false) }
            AnimatedContent(
                targetState = selectingProvider,
                transitionSpec = {
                    if (targetState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "ToolsPickerPage"
            ) { selecting ->
                if (selecting) {
                    SearchProviderPicker(
                        settings = settings,
                        onUpdateSearchService = { index ->
                            onUpdateSearchService(index)
                            selectingProvider = false
                        },
                        onBack = { selectingProvider = false }
                    )
                } else {
                    ToolsPickerContent(
                        enableSearch = enableSearch,
                        settings = settings,
                        assistant = assistant,
                        model = model,
                        onUpdateSearchMode = onUpdateSearchMode,
                        onUpdateImageGeneration = onUpdateImageGeneration,
                        onUpdateImageModel = onUpdateImageModel,
                        onUpdateImageAspectRatio = onUpdateImageAspectRatio,
                        onSelectSearchProvider = { selectingProvider = true },
                        onDismiss = { showToolsPicker = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsPickerContent(
    enableSearch: Boolean,
    settings: Settings,
    assistant: Assistant,
    model: Model?,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onUpdateImageGeneration: (Boolean) -> Unit,
    onUpdateImageModel: (Model) -> Unit,
    onUpdateImageAspectRatio: (String) -> Unit,
    onSelectSearchProvider: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navBackStack = LocalNavController.current
    val provider = model?.findProvider(settings.providers)
    val supportsBuiltInSearch = provider is ProviderSetting.Google ||
        provider is ProviderSetting.OpenAI && provider.useResponseApi
    val hasBuiltInSearchEnabled = model?.tools?.contains(BuiltInTools.Search) == true
    val showModelSearch = model != null && (supportsBuiltInSearch || hasBuiltInSearchEnabled)
    val isLocalSearchSelected = enableSearch && !hasBuiltInSearchEnabled
    val isSearchEnabled = enableSearch || hasBuiltInSearchEnabled
    val isImageGenEnabled = assistant.localTools.contains(LocalToolOption.ImageGeneration)

    val imageModelState = rememberModelListState(
        modelId = settings.imageGenerationModelId,
        providers = settings.providers,
        type = ModelType.IMAGE,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = HugeIcons.Tools,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.tools_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            IconButton(
                onClick = {
                    onDismiss()
                    navBackStack.navigate(Screen.SettingSearch)
                }
            ) {
                Icon(HugeIcons.Settings03, contentDescription = null)
            }
        }

        // Section 1: Web Search
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = HugeIcons.GlobalSearch,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tools_picker_web_search),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.search_picker_local_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSearchEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onUpdateSearchMode(SearchMode.LOCAL)
                            } else {
                                onUpdateSearchMode(SearchMode.OFF)
                            }
                        }
                    )
                }

                if (isSearchEnabled && showModelSearch) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchModeCard(
                            title = stringResource(R.string.search_picker_local_title),
                            description = stringResource(R.string.search_picker_local_description),
                            icon = HugeIcons.GlobalSearch,
                            selected = isLocalSearchSelected,
                            onClick = {
                                if (isLocalSearchSelected) {
                                    onUpdateSearchMode(SearchMode.OFF)
                                } else {
                                    onUpdateSearchMode(SearchMode.LOCAL)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        SearchModeCard(
                            title = stringResource(R.string.search_picker_model_title),
                            description = stringResource(R.string.search_picker_model_description),
                            icon = HugeIcons.AiSearch02,
                            selected = hasBuiltInSearchEnabled,
                            onClick = {
                                onUpdateSearchMode(
                                    if (hasBuiltInSearchEnabled) SearchMode.OFF else SearchMode.BUILT_IN
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                if (isLocalSearchSelected) {
                    val currentService = settings.searchServices.getOrNull(settings.searchServiceSelected)
                    Card(
                        onClick = onSelectSearchProvider,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    HugeIcons.Search01,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = buildString {
                                        append(stringResource(R.string.search_picker_select_provider))
                                        currentService?.let { append(" · ${it.displayName}") }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = HugeIcons.ArrowRight01,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Image Generation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = HugeIcons.Image02,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tools_picker_image_gen),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.setting_page_built_in_tools_image_generation_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isImageGenEnabled,
                        onCheckedChange = { onUpdateImageGeneration(it) }
                    )
                }

                // Image Model Selector
                val currentImageModel = settings.getDefaultImageModel()
                Card(
                    onClick = { imageModelState.open() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                HugeIcons.AiBrain01,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.tools_picker_image_model),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentImageModel?.displayName
                                        ?: stringResource(R.string.model_list_select_model),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Aspect Ratio Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.tools_picker_aspect_ratio),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val ratios = listOf(
                        "auto" to "Auto",
                        "1:1" to "1:1",
                        "16:9" to "16:9",
                        "9:16" to "9:16",
                        "2:3" to "2:3",
                        "3:2" to "3:2"
                    )
                    val activeRatio = settings.imageAspectRatio.ifBlank { "auto" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ratios.forEach { (value, label) ->
                            val isSelected = activeRatio.equals(value, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onUpdateImageAspectRatio(value) },
                                label = { Text(label) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            HugeIcons.CheckmarkCircle02,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    ModelListSheet(
        state = imageModelState,
        onSelect = { model ->
            onUpdateImageModel(model)
            imageModelState.close()
        }
    )
}

@Composable
private fun SearchModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    )
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = HugeIcons.CheckmarkCircle02,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SearchProviderPicker(
    settings: Settings,
    onUpdateSearchService: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = null)
            }
            Text(
                text = stringResource(R.string.search_picker_select_provider),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            columns = GridCells.Adaptive(150.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(settings.searchServices, key = { _, service -> service.id }) { index, service ->
                val containerColor = animateColorAsState(
                    if (settings.searchServiceSelected == index) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
                val textColor = animateColorAsState(
                    if (settings.searchServiceSelected == index) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor.value,
                        contentColor = textColor.value,
                    ),
                    onClick = {
                        onUpdateSearchService(index)
                    },
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AutoAIIcon(
                            name = service.displayName,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = service.displayName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            SearchAbilityTagLine(
                                options = service,
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}
