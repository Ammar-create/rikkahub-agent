package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Comment01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.HelpCircle
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Minimize01
import me.rerere.hugeicons.stroke.SourceCode
import me.rerere.hugeicons.stroke.TimeQuarterPass
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.ai.AgentMode
import me.rerere.rikkahub.data.datastore.ActionIcons
import me.rerere.rikkahub.data.datastore.CustomAction
import me.rerere.rikkahub.data.datastore.CustomMode

val HugeIcons.SourceCode01: ImageVector
    get() = SourceCode

class SlashCommandCompletionProvider(
    private val currentMode: () -> AgentMode = { AgentMode.BUILD },
    private val listSkills: () -> List<String> = { emptyList() },
    private val onModeSelected: ((AgentMode) -> Unit)? = null,
    private val onSkillSelected: ((String) -> Unit)? = null,
    private val disabledActions: () -> Set<String> = { emptySet() },
    private val customActions: () -> List<CustomAction> = { emptyList() },
    private val customModes: () -> List<CustomMode> = { emptyList() },
    private val onCustomModeSelected: ((CustomMode) -> Unit)? = null,
) : ChatCompletionProvider {
    override val id: String = "slash_commands"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection) return null
        val parsed = parseSlash(context.text, context.cursor) ?: return null

        val items: List<ChatCompletionItem> = when {
            parsed.isNested && parsed.commandName.equals("mode", ignoreCase = true) -> {
                val subQuery = parsed.argument.lowercase()
                val standardModes = listOf(
                    ChatCompletionItem(
                        label = "Plan",
                        insertText = "",
                        detail = null,
                        icon = HugeIcons.Edit03,
                        sortScore = 1000,
                        onSelect = { onModeSelected?.invoke(AgentMode.PLAN) },
                    ),
                    ChatCompletionItem(
                        label = "Build",
                        insertText = "",
                        detail = null,
                        icon = HugeIcons.Tools,
                        sortScore = 900,
                        onSelect = { onModeSelected?.invoke(AgentMode.BUILD) },
                    ),
                )
                val customModeItems = customModes().filter { it.enabled }.mapIndexed { index, mode ->
                    ChatCompletionItem(
                        label = mode.name,
                        insertText = "",
                        detail = null,
                        icon = ActionIcons.get(mode.icon),
                        sortScore = 850 - index,
                        onSelect = { onCustomModeSelected?.invoke(mode) },
                    )
                }
                (standardModes + customModeItems).filter { it.label.lowercase().startsWith(subQuery) }
            }

            parsed.isNested && parsed.commandName.equals("skill", ignoreCase = true) -> {
                val subQuery = parsed.argument.lowercase()
                val skills = listSkills()
                val skillItems = skills
                    .filter { it.lowercase().contains(subQuery) }
                    .mapIndexed { index, skillName ->
                        ChatCompletionItem(
                            label = skillName,
                            insertText = "",
                            detail = null,
                            icon = HugeIcons.SourceCode01,
                            sortScore = 1000 - index,
                            onSelect = { onSkillSelected?.invoke(skillName) },
                        )
                    }
                if (skillItems.isEmpty() && !parsed.hasTrailingSpace && subQuery.isEmpty()) {
                    listOf(
                        ChatCompletionItem(
                            label = "/Skill",
                            insertText = "/skill ",
                            detail = null,
                            icon = HugeIcons.SourceCode01,
                            sortScore = 980,
                        )
                    )
                } else {
                    skillItems
                }
            }

            !parsed.isNested -> {
                val query = parsed.commandName.lowercase()
                val disabled = disabledActions().map { it.lowercase().removePrefix("/") }.toSet()
                val activeBuiltIns = TOP_LEVEL_ACTIONS.filter { action ->
                    action.id.lowercase() !in disabled && action.label.removePrefix("/").lowercase() !in disabled
                }
                val activeCustom = customActions().filter { it.enabled }.mapIndexed { index, custom ->
                    TopLevelActionDef(
                        id = custom.id,
                        label = custom.prefix,
                        insertText = "${custom.prefix} ",
                        icon = ActionIcons.get(custom.icon),
                        sortScore = 900 - index,
                    )
                }
                (activeBuiltIns + activeCustom)
                    .filter { action ->
                        val nameWithoutSlash = action.label.removePrefix("/").lowercase()
                        nameWithoutSlash.startsWith(query) || action.label.lowercase().startsWith(query)
                    }
                    .map { action ->
                        ChatCompletionItem(
                            label = action.label,
                            insertText = action.insertText,
                            detail = null,
                            icon = action.icon,
                            sortScore = action.sortScore,
                        )
                    }
            }

            else -> emptyList()
        }

        if (items.isEmpty()) return null
        return ChatCompletionList(
            providerId = id,
            replacementRange = parsed.replacementRange,
            items = items,
        )
    }

    private data class ParsedSlash(
        val replacementRange: TextRange,
        val commandName: String,
        val isNested: Boolean,
        val hasTrailingSpace: Boolean,
        val argument: String,
    )

    private fun parseSlash(text: String, cursor: Int): ParsedSlash? {
        if (cursor < 0 || cursor > text.length) return null
        val prefix = text.substring(0, cursor)
        val start = prefix.lastIndexOf('/')
        if (start < 0) return null
        // Command slash must be at start of input or after whitespace/newline
        if (start > 0 && !prefix[start - 1].isWhitespace()) return null

        val afterSlash = prefix.substring(start + 1)
        val spaceIndex = afterSlash.indexOfFirst { it.isWhitespace() }

        return if (spaceIndex >= 0) {
            val cmd = afterSlash.substring(0, spaceIndex)
            val arg = afterSlash.substring(spaceIndex + 1)
            if (cmd.equals("mode", ignoreCase = true) || cmd.equals("skill", ignoreCase = true)) {
                if (arg.any { it.isWhitespace() }) return null
                ParsedSlash(
                    replacementRange = TextRange(start, cursor),
                    commandName = cmd.lowercase(),
                    isNested = true,
                    hasTrailingSpace = true,
                    argument = arg,
                )
            } else {
                null
            }
        } else {
            val cmd = afterSlash
            if (cmd.equals("mode", ignoreCase = true) || cmd.equals("skill", ignoreCase = true)) {
                ParsedSlash(
                    replacementRange = TextRange(start, cursor),
                    commandName = cmd.lowercase(),
                    isNested = true,
                    hasTrailingSpace = false,
                    argument = "",
                )
            } else {
                ParsedSlash(
                    replacementRange = TextRange(start, cursor),
                    commandName = cmd.lowercase(),
                    isNested = false,
                    hasTrailingSpace = false,
                    argument = "",
                )
            }
        }
    }

    private data class TopLevelActionDef(
        val id: String,
        val label: String,
        val insertText: String,
        val icon: ImageVector,
        val sortScore: Int,
    )

    private companion object {
        val TOP_LEVEL_ACTIONS = listOf(
            TopLevelActionDef("mode", "/Mode", "/mode ", HugeIcons.Tools, 1000),
            TopLevelActionDef("goal", "/Goal", "/goal ", HugeIcons.TimeQuarterPass, 990),
            TopLevelActionDef("skill", "/Skill", "/skill ", HugeIcons.SourceCode01, 980),
            TopLevelActionDef("grill-me", "/Grill-me", "/grill-me ", HugeIcons.Comment01, 970),
            TopLevelActionDef("subagent", "/Subagent", "/subagent ", HugeIcons.GitFork, 960),
            TopLevelActionDef("review", "/Review", "/review ", HugeIcons.CheckList, 950),
            TopLevelActionDef("undo", "/Undo", "/undo ", HugeIcons.ArrowTurnBackward, 940),
            TopLevelActionDef("compact", "/Compact", "/compact ", HugeIcons.Minimize01, 930),
            TopLevelActionDef("imagine", "/Imagine", "/imagine ", HugeIcons.Image02, 920),
            TopLevelActionDef("btw", "/Btw", "/btw ", HugeIcons.HelpCircle, 910),
        )
    }
}
