package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.ui.components.ai.completion.SourceCode01
import me.rerere.rikkahub.ui.hooks.ActionPill
import me.rerere.rikkahub.ui.hooks.ActionPillType
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.data.ai.AgentMode
import me.rerere.rikkahub.data.ai.ConversationAgentMode
import me.rerere.rikkahub.data.datastore.ActionIcons
import me.rerere.rikkahub.ui.components.ai.completion.SlashCommandCompletionProvider
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val skillManager: SkillManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    @Suppress("DEPRECATION")  // LocalWindowInfo replaces this in a future Compose bump
    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val chatService: ChatService = koinInject()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val assistant = setting.getCurrentAssistant()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        setting.getCurrentChatModel()?.findProvider(setting.providers) is ProviderSetting.Google

    val agentMode by ConversationAgentMode.getFlow(conversation.id).collectAsStateWithLifecycle()
    val completionProviders = remember(
        assistant.workspaceId,
        conversation.workspaceCwd,
        workspaceRepository,
        agentMode,
        conversation.id,
    ) {
        buildList {
            add(
                SlashCommandCompletionProvider(
                    currentMode = { ConversationAgentMode.get(conversation.id) },
                    listSkills = {
                        runCatching { skillManager.listSkills().map { it.name } }.getOrDefault(emptyList())
                    },
                    disabledActions = { setting.disabledBuiltInActions },
                    customActions = { setting.customActions.filter { it.enabled } },
                    customModes = { setting.customModes.filter { it.enabled } },
                    onModeSelected = { mode ->
                        ConversationAgentMode.set(conversation.id, mode)
                        inputState.addActionPill(
                            ActionPill(
                                id = "mode",
                                label = if (mode == AgentMode.PLAN) "Plan" else "Build",
                                icon = if (mode == AgentMode.PLAN) HugeIcons.Edit03 else HugeIcons.Tools,
                                type = ActionPillType.MODE,
                            )
                        )
                    },
                    onCustomModeSelected = { customMode ->
                        inputState.addActionPill(
                            ActionPill(
                                id = "mode_${customMode.id}",
                                label = customMode.name,
                                icon = ActionIcons.get(customMode.icon),
                                type = ActionPillType.MODE,
                            )
                        )
                    },
                    onSkillSelected = { skillName ->
                        inputState.addActionPill(
                            ActionPill(
                                id = "skill_$skillName",
                                label = skillName,
                                icon = HugeIcons.SourceCode01,
                                type = ActionPillType.SKILL,
                            )
                        )
                    },
                )
            )
            assistant.workspaceId?.let { workspaceId ->
                add(
                    WorkspaceCompletionProvider(
                        workspaceId = workspaceId.toString(),
                        repository = workspaceRepository,
                        currentCwd = conversation.workspaceCwd,
                    )
                )
            }
        }
    }

    LaunchedEffect(conversation.id, agentMode) {
        if (agentMode == AgentMode.PLAN) {
            inputState.addActionPill(
                ActionPill(
                    id = "mode",
                    label = "Plan",
                    icon = HugeIcons.Edit03,
                    type = ActionPillType.MODE,
                )
            )
        } else {
            val existing = inputState.actionPills.find { it.type == ActionPillType.MODE }
            if (existing != null && existing.label == "Plan") {
                inputState.removeActionPill(existing)
            }
        }
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    onNewChat = {
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                )
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                ChatInput(
                    onStartVoiceMode = onStartVoiceMode,
                    voiceState = voiceState,
                    onStopVoiceMode = vm.voiceSession::stop,
                    state = inputState,
                    messageQueue = messageQueue,
                    onRemoveQueuedMessage = vm::removeQueuedMessage,
                    onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                    onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                    onResumeMessageQueue = vm::resumeMessageQueue,
                    loading = loadingJob != null,
                    settings = setting,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    onRemovePill = { pill ->
                        if (pill.type == ActionPillType.MODE) {
                            ConversationAgentMode.set(conversation.id, AgentMode.BUILD)
                        }
                        inputState.removeActionPill(pill)
                    },
                    onCancelClick = {
                        vm.stopGeneration()
                    },
                    onUpdateSettings = { vm.updateSettings(it) },
                    enableSearch = enableWebSearch,
                    onUpdateSearchMode = { mode ->
                        val current = setting.getCurrentAssistant()
                        val model = setting.getCurrentChatModel()
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == current.id) {
                                        assistant.copy(enableWebSearch = mode == SearchMode.LOCAL)
                                    } else {
                                        assistant
                                    }
                                },
                                providers = if (model == null) {
                                    setting.providers
                                } else {
                                    setting.providers.map { provider ->
                                        provider.editModel(
                                            model.copy(
                                                tools = if (mode == SearchMode.BUILT_IN) {
                                                    model.tools + BuiltInTools.Search
                                                } else {
                                                    model.tools - BuiltInTools.Search
                                                }
                                            )
                                        )
                                    }
                                },
                            )
                        )
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show(
                                context.getString(R.string.chat_select_model_first),
                                type = ToastType.Error,
                            )
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            val contents = inputState.getContents()
                            val textPart = contents.firstOrNull() as? UIMessagePart.Text
                            val rawText = textPart?.text?.trim().orEmpty()

                            // Check slash commands for direct text entry
                            if (contents.size == 1 && textPart != null && rawText.startsWith("/mode", ignoreCase = true)) {
                                val sub = rawText.removePrefix("/mode").removePrefix("/Mode").trim()
                                if (sub.equals("plan", ignoreCase = true)) {
                                    ConversationAgentMode.set(conversation.id, AgentMode.PLAN)
                                    toaster.show("Switched to Plan mode", type = ToastType.Success)
                                    inputState.clearInput()
                                    return@ChatInput
                                } else if (sub.equals("build", ignoreCase = true)) {
                                    ConversationAgentMode.set(conversation.id, AgentMode.BUILD)
                                    toaster.show("Switched to Build mode", type = ToastType.Success)
                                    inputState.clearInput()
                                    return@ChatInput
                                }
                            }

                            // 1. /compact
                            val hasCompactPill = inputState.actionPills.any {
                                it.type == ActionPillType.COMPACT || it.id.equals("compact", ignoreCase = true)
                            }
                            val isCompactCmd = textPart != null && (rawText.equals("/compact", ignoreCase = true) || rawText.startsWith("/compact ", ignoreCase = true))
                            if (isCompactCmd || hasCompactPill) {
                                val extra = if (isCompactCmd) {
                                    rawText.replaceFirst(Regex("^/compact\\s*", RegexOption.IGNORE_CASE), "").trim()
                                } else {
                                    rawText
                                }
                                toaster.show("Compacting conversation context...", type = ToastType.Normal)
                                scope.launch {
                                    chatService.manualCompact(
                                        conversationId = conversation.id,
                                        additionalPrompt = extra,
                                    ).await()
                                }
                                inputState.clearInput()
                                return@ChatInput
                            }

                            // 2. /undo
                            val hasUndoPill = inputState.actionPills.any {
                                it.type == ActionPillType.UNDO || it.id.equals("undo", ignoreCase = true)
                            }
                            val isUndoCmd = textPart != null && (rawText.equals("/undo", ignoreCase = true) || rawText.startsWith("/undo ", ignoreCase = true))
                            if (isUndoCmd || hasUndoPill) {
                                val workspaceId = assistant.workspaceId
                                if (workspaceId != null) {
                                    scope.launch {
                                        val res = workspaceRepository.executeCommand(
                                            workspaceId.toString(),
                                            "git checkout -- .",
                                        )
                                        if (res.exitCode == 0) {
                                            toaster.show("Reverted uncommitted workspace edits", type = ToastType.Success)
                                        } else {
                                            toaster.show("Undo failed: ${res.stderr.ifBlank { "unknown error" }}", type = ToastType.Error)
                                        }
                                    }
                                } else {
                                    toaster.show("No active workspace bound to this assistant", type = ToastType.Normal)
                                }
                                inputState.clearInput()
                                return@ChatInput
                            }

                            // 3. /review
                            val hasReviewPill = inputState.actionPills.any {
                                it.type == ActionPillType.REVIEW || it.id.equals("review", ignoreCase = true)
                            }
                            val isReviewCmd = textPart != null && (rawText.equals("/review", ignoreCase = true) || rawText.startsWith("/review ", ignoreCase = true))
                            if (isReviewCmd || hasReviewPill) {
                                val extraText = if (isReviewCmd) {
                                    rawText.replaceFirst(Regex("^/review\\s*", RegexOption.IGNORE_CASE), "").trim()
                                } else {
                                    rawText
                                }
                                val workspaceId = assistant.workspaceId
                                scope.launch {
                                    val gitDiff = if (workspaceId != null) {
                                        runCatching {
                                            workspaceRepository.executeCommand(workspaceId.toString(), "git diff HEAD").stdout.trim()
                                        }.getOrDefault("")
                                    } else ""

                                    val reviewPrompt = if (gitDiff.isNotBlank()) {
                                        buildString {
                                            append("Review the following uncommitted workspace changes for bugs, syntax errors, and missing tests:\n\n```diff\n")
                                            append(gitDiff)
                                            append("\n```")
                                            if (extraText.isNotBlank()) {
                                                append("\n\n")
                                                append(extraText)
                                            }
                                        }
                                    } else {
                                        if (extraText.isNotBlank()) {
                                            "Review the following uncommitted workspace changes for bugs, syntax errors, and missing tests:\n\n$extraText"
                                        } else {
                                            toaster.show("No uncommitted workspace changes found to review", type = ToastType.Normal)
                                            return@launch
                                        }
                                    }

                                    val skillPills = inputState.actionPills.filter { it.type == ActionPillType.SKILL }
                                    val finalReviewText = if (skillPills.isNotEmpty()) {
                                        val skillInstruction = skillPills.joinToString(" ") { "Use skill: ${it.label}." }
                                        "$skillInstruction\n\n$reviewPrompt"
                                    } else {
                                        reviewPrompt
                                    }
                                    vm.handleMessageSend(listOf(UIMessagePart.Text(finalReviewText)) + contents.drop(1))
                                }
                                inputState.clearInput()
                                return@ChatInput
                            }

                            // Process prompt transformations for Actions
                            var actionProcessedText = rawText

                            // 4. /grill-me
                            val hasGrillMePill = inputState.actionPills.any {
                                it.type == ActionPillType.GRILL_ME || it.id.equals("grill-me", ignoreCase = true) || it.id.equals("grillme", ignoreCase = true)
                            }
                            val isGrillMeCmd = textPart != null && (rawText.equals("/grill-me", ignoreCase = true) || rawText.startsWith("/grill-me ", ignoreCase = true) || rawText.equals("/grillme", ignoreCase = true) || rawText.startsWith("/grillme ", ignoreCase = true))
                            if (isGrillMeCmd || hasGrillMePill) {
                                val taskText = if (isGrillMeCmd) {
                                    rawText.replaceFirst(Regex("^/grill-?me\\s*", RegexOption.IGNORE_CASE), "").trim()
                                } else {
                                    rawText
                                }
                                actionProcessedText = if (taskText.isNotBlank()) {
                                    "[Action: Grill-me] Before writing any code or modifying any files, interview me one question at a time to align on scope, edge cases, and design. Provide a recommended answer for each question.\n\n$taskText"
                                } else {
                                    "[Action: Grill-me] Before writing any code or modifying any files, interview me one question at a time to align on scope, edge cases, and design. Provide a recommended answer for each question."
                                }
                            }

                            // 5. /goal
                            val hasGoalPill = inputState.actionPills.any {
                                it.type == ActionPillType.GOAL || it.id.equals("goal", ignoreCase = true)
                            }
                            val isGoalCmd = textPart != null && (rawText.equals("/goal", ignoreCase = true) || rawText.startsWith("/goal ", ignoreCase = true))
                            if (isGoalCmd || hasGoalPill) {
                                val objective = if (isGoalCmd) {
                                    rawText.replaceFirst(Regex("^/goal\\s*", RegexOption.IGNORE_CASE), "").trim()
                                } else {
                                    rawText
                                }
                                actionProcessedText = "[Action: Goal] Autonomous objective: $objective\nExecute autonomously across turns until this objective is verified completely finished. Do not pause between turns."
                            }

                            // 6. /imagine
                            val hasImaginePill = inputState.actionPills.any {
                                it.type == ActionPillType.IMAGINE || it.id.equals("imagine", ignoreCase = true)
                            }
                            val isImagineCmd = textPart != null && (rawText.equals("/imagine", ignoreCase = true) || rawText.startsWith("/imagine ", ignoreCase = true))
                            if (isImagineCmd || hasImaginePill) {
                                val imagePrompt = if (isImagineCmd) {
                                    rawText.replaceFirst(Regex("^/imagine\\s*", RegexOption.IGNORE_CASE), "").trim()
                                } else {
                                    rawText
                                }
                                val configuredRatio = setting.imageAspectRatio.ifBlank { "auto" }
                                actionProcessedText = "[Action: Imagine] Please invoke the generate_image tool now to create an image. Prompt: \"$imagePrompt\", aspect_ratio: \"$configuredRatio\". Autonomously execute this tool call immediately."

                                // Ensure generate_image tool is enabled for the assistant so the model can invoke it
                                val currentAssistant = setting.getCurrentAssistant()
                                if (!currentAssistant.localTools.contains(LocalToolOption.ImageGeneration)) {
                                    vm.updateSettings(
                                        setting.copy(
                                            assistants = setting.assistants.map {
                                                if (it.id == currentAssistant.id) {
                                                    it.copy(localTools = (it.localTools + LocalToolOption.ImageGeneration).distinct())
                                                } else {
                                                    it
                                                }
                                            }
                                        )
                                    )
                                }
                            }

                            // 7. /subagent
                            val hasSubagentPill = inputState.actionPills.any {
                                it.type == ActionPillType.SUBAGENT || it.id.equals("subagent", ignoreCase = true)
                            }
                            val isSubagentCmd = textPart != null && (rawText.equals("/subagent", ignoreCase = true) || rawText.startsWith("/subagent ", ignoreCase = true))
                            if (isSubagentCmd || hasSubagentPill) {
                                val taskText = if (isSubagentCmd) {
                                    rawText.replaceFirst(Regex("^/subagent\\s*", RegexOption.IGNORE_CASE), "").trim()
                                } else {
                                    rawText
                                }
                                actionProcessedText = "[Action: Subagent] Dispatch a sub-agent to autonomously execute this task: $taskText"
                            }

                            // 8. /btw
                            val hasBtwPill = inputState.actionPills.any {
                                it.type == ActionPillType.BTW || it.id.equals("btw", ignoreCase = true)
                            }
                            val isBtwCmd = textPart != null && (rawText.equals("/btw", ignoreCase = true) || rawText.startsWith("/btw ", ignoreCase = true))
                            if (isBtwCmd || hasBtwPill) {
                                val question = if (isBtwCmd) {
                                    rawText.replaceFirst(Regex("^/btw\\s*", RegexOption.IGNORE_CASE), "").trim()
                                } else {
                                    rawText
                                }
                                actionProcessedText = "[Side Question (btw)]: $question"
                            }

                            // 9. Custom Actions
                            val matchedCustomAction = setting.customActions.firstOrNull { customAction ->
                                customAction.enabled && (
                                    inputState.actionPills.any {
                                        it.id == customAction.id || it.label.equals(customAction.name, ignoreCase = true)
                                    } || (textPart != null && (
                                        rawText.equals(customAction.prefix, ignoreCase = true) ||
                                        rawText.startsWith("${customAction.prefix} ", ignoreCase = true)
                                    ))
                                )
                            }
                            if (matchedCustomAction != null) {
                                val userArg = if (textPart != null && (
                                        rawText.equals(matchedCustomAction.prefix, ignoreCase = true) ||
                                        rawText.startsWith("${matchedCustomAction.prefix} ", ignoreCase = true)
                                    )) {
                                    rawText.substring(matchedCustomAction.prefix.length).trim()
                                } else {
                                    rawText
                                }
                                actionProcessedText = if (userArg.isNotBlank()) {
                                    "${matchedCustomAction.promptTemplate}\n\n$userArg"
                                } else {
                                    matchedCustomAction.promptTemplate
                                }
                            }

                            // 10. Custom Modes addendum
                            val customModePill = inputState.actionPills.find { pill ->
                                pill.type == ActionPillType.MODE && setting.customModes.any { it.name.equals(pill.label, ignoreCase = true) }
                            }
                            if (customModePill != null) {
                                val modeDef = setting.customModes.find { it.name.equals(customModePill.label, ignoreCase = true) }
                                if (modeDef != null && modeDef.systemPromptAddendum.isNotBlank()) {
                                    actionProcessedText = "[Mode: ${modeDef.name}]\n${modeDef.systemPromptAddendum}\n\n$actionProcessedText"
                                }
                            }

                            val skillPills = inputState.actionPills.filter { it.type == ActionPillType.SKILL }
                            val finalContents = if (skillPills.isNotEmpty() || actionProcessedText != rawText) {
                                val skillInstruction = if (skillPills.isNotEmpty()) {
                                    skillPills.joinToString(" ") { "Use skill: ${it.label}." }
                                } else ""
                                val textWithSkills = if (skillInstruction.isNotBlank()) {
                                    if (actionProcessedText.isNotBlank()) "$skillInstruction\n\n$actionProcessedText" else skillInstruction
                                } else {
                                    actionProcessedText
                                }
                                if (textPart != null) {
                                    listOf(UIMessagePart.Text(textWithSkills)) + contents.drop(1)
                                } else {
                                    listOf(UIMessagePart.Text(textWithSkills)) + contents
                                }
                            } else {
                                contents
                            }

                            vm.handleMessageSend(finalContents)
                            scope.launch {
                                delay(100.milliseconds)
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = {
                        vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it)
                    },
                    onUpdateAssistant = {
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == it.id) {
                                        it
                                    } else {
                                        assistant
                                    }
                                }
                            )
                        )
                    },
                    onUpdateSearchService = { index ->
                        vm.updateSettings(
                            setting.copy(
                                searchServiceSelected = index
                            )
                        )
                    },
                    onMoreClick = {
                        showFilesSheet = true
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = {
                    vm.regenerateAtMessage(it)
                },
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onDelete = {
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onUpdateMessage = { newNode ->
                    vm.updateConversation(
                        conversation.copy(
                            messageNodes = conversation.messageNodes.map { node ->
                                if (node.id == newNode.id) {
                                    newNode
                                } else {
                                    node
                                }
                            }
                        ))
                    vm.saveConversationAsync()
                },
                onClickSuggestion = { suggestion ->
                    inputState.editingMessage = null
                    inputState.setMessageText(suggestion)
                },
                onTranslate = { message, locale ->
                    vm.translateMessage(message, locale)
                },
                onClearTranslation = { message ->
                    vm.clearTranslationField(message.id)
                },
                onJumpToMessage = { index ->
                    previewMode = false
                    scope.launch {
                        chatListState.requestScrollToItem(index)
                    }
                },
                onToolApproval = { toolCallId, approved, reason, scope, toolName ->
                    vm.handleToolApproval(toolCallId, approved, reason, scope, toolName)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
                onRerunTool = { toolCallId ->
                    vm.rerunTool(toolCallId)
                },
                onToggleFavorite = { node ->
                    vm.toggleMessageFavorite(node)
                },
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                    vm.saveConversationAsync()
                },
            )
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
            },
            onUpdateAssistant = {
                vm.updateSettings(
                    setting.copy(
                        assistants = setting.assistants.map { assistant ->
                            if (assistant.id == it.id) {
                                it
                            } else {
                                assistant
                            }
                        }
                    )
                )
            },
            onUpdateConversation = {
                vm.updateConversation(it)
                vm.saveConversationAsync()
            },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    // 与抽屉的 Rename 菜单项共享同一个重命名对话框状态，见 ChatDrawerVM.conversationToRename
    val activity = LocalContext.current as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    }
                ) {
                    Icon(HugeIcons.Menu03, stringResource(R.string.menu))
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        drawerVm.requestRenameConversation(conversation)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Column {
                    val assistant = settings.getCurrentAssistant()
                    val model = settings.getCurrentChatModel()
                    val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, stringResource(R.string.accessibility_chat_options))
            }

            IconButton(
                onClick = {
                    onNewChat()
                }
            ) {
                Icon(HugeIcons.MessageAdd01, stringResource(R.string.chat_page_new_message))
            }
        },
    )
}
