package me.rerere.rikkahub.data.datastore

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Comment01
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.HelpCircle
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Minimize01
import me.rerere.hugeicons.stroke.Robot01
import me.rerere.hugeicons.stroke.Rocket01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Shield01
import me.rerere.hugeicons.stroke.SourceCode
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.TimeQuarterPass
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.Zap
import kotlin.uuid.Uuid

@Serializable
data class CustomAction(
    val id: String = Uuid.random().toString(),
    val name: String,
    val prefix: String,
    val icon: String = "Tools",
    val promptTemplate: String,
    val enabled: Boolean = true,
)

@Serializable
data class CustomMode(
    val id: String = Uuid.random().toString(),
    val name: String,
    val icon: String = "Edit03",
    val systemPromptAddendum: String,
    val enabled: Boolean = true,
)

data class BuiltInAction(
    val id: String,
    val command: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

object BuiltInActions {
    val ALL: List<BuiltInAction> = listOf(
        BuiltInAction(
            id = "mode",
            command = "/Mode",
            title = "Mode",
            description = "Switch between Plan and Build execution modes",
            icon = HugeIcons.Tools,
        ),
        BuiltInAction(
            id = "goal",
            command = "/Goal",
            title = "Goal",
            description = "Run an autonomous multi-turn goal loop",
            icon = HugeIcons.TimeQuarterPass,
        ),
        BuiltInAction(
            id = "skill",
            command = "/Skill",
            title = "Skill",
            description = "Inject specialized skills into assistant context",
            icon = HugeIcons.SourceCode,
        ),
        BuiltInAction(
            id = "grill-me",
            command = "/Grill-me",
            title = "Grill-me",
            description = "Scoping interview before writing code",
            icon = HugeIcons.Comment01,
        ),
        BuiltInAction(
            id = "subagent",
            command = "/Subagent",
            title = "Subagent",
            description = "Dispatch a focused background sub-agent",
            icon = HugeIcons.GitFork,
        ),
        BuiltInAction(
            id = "review",
            command = "/Review",
            title = "Review",
            description = "Review uncommitted workspace changes for bugs",
            icon = HugeIcons.CheckList,
        ),
        BuiltInAction(
            id = "undo",
            command = "/Undo",
            title = "Undo",
            description = "Revert uncommitted workspace changes with git",
            icon = HugeIcons.ArrowTurnBackward,
        ),
        BuiltInAction(
            id = "compact",
            command = "/Compact",
            title = "Compact",
            description = "Trigger immediate context window compaction",
            icon = HugeIcons.Minimize01,
        ),
        BuiltInAction(
            id = "imagine",
            command = "/Imagine",
            title = "Imagine",
            description = "Generate studio-grade images with image model",
            icon = HugeIcons.Image02,
        ),
        BuiltInAction(
            id = "btw",
            command = "/Btw",
            title = "Btw",
            description = "Ask a quick side question without context drift",
            icon = HugeIcons.HelpCircle,
        ),
    )
}

object ActionIcons {
    val AVAILABLE_ICONS: List<Pair<String, ImageVector>> = listOf(
        "Tools" to HugeIcons.Tools,
        "Edit" to HugeIcons.Edit03,
        "Code" to HugeIcons.SourceCode,
        "Zap" to HugeIcons.Zap,
        "Sparkles" to HugeIcons.Sparkles,
        "Search" to HugeIcons.Search01,
        "Shield" to HugeIcons.Shield01,
        "Bug" to HugeIcons.Bug01,
        "CheckList" to HugeIcons.CheckList,
        "Terminal" to HugeIcons.ComputerTerminal01,
        "Folder" to HugeIcons.Folder01,
        "Help" to HugeIcons.HelpCircle,
        "Robot" to HugeIcons.Robot01,
        "Brain" to HugeIcons.Brain02,
        "Rocket" to HugeIcons.Rocket01,
        "MagicWand" to HugeIcons.MagicWand01,
    )

    fun get(name: String): ImageVector {
        return AVAILABLE_ICONS.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second ?: HugeIcons.Tools
    }
}
