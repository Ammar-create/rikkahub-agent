package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.ai.AgentMode
import me.rerere.rikkahub.data.datastore.CustomAction
import me.rerere.rikkahub.data.datastore.CustomMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandCompletionProviderTest {

    @Test
    fun typingSlash_offersTopLevelActionsInOrder() = runBlocking {
        val provider = SlashCommandCompletionProvider()

        val result = provider.complete(ChatCompletionContext("/", TextRange(1)))
        assertNotNull(result)
        assertEquals(10, result!!.items.size)
        assertEquals("/Mode", result.items[0].label)
        assertEquals("/Goal", result.items[1].label)
        assertEquals("/Skill", result.items[2].label)
        assertEquals("/Grill-me", result.items[3].label)
        assertEquals("/Subagent", result.items[4].label)
        assertEquals("/Review", result.items[5].label)
        assertEquals("/Undo", result.items[6].label)
        assertEquals("/Compact", result.items[7].label)
        assertEquals("/Imagine", result.items[8].label)
        assertEquals("/Btw", result.items[9].label)
    }

    @Test
    fun typingPrefix_filtersTopLevelActions() = runBlocking {
        val provider = SlashCommandCompletionProvider()

        val mResult = provider.complete(ChatCompletionContext("/m", TextRange(2)))
        assertNotNull(mResult)
        assertEquals(1, mResult!!.items.size)
        assertEquals("/Mode", mResult.items[0].label)

        val gResult = provider.complete(ChatCompletionContext("/g", TextRange(2)))
        assertNotNull(gResult)
        assertEquals(2, gResult!!.items.size)
        assertEquals("/Goal", gResult.items[0].label)
        assertEquals("/Grill-me", gResult.items[1].label)

        val sResult = provider.complete(ChatCompletionContext("/s", TextRange(2)))
        assertNotNull(sResult)
        assertEquals(2, sResult!!.items.size)
        assertEquals("/Skill", sResult.items[0].label)
        assertEquals("/Subagent", sResult.items[1].label)

        val uResult = provider.complete(ChatCompletionContext("/u", TextRange(2)))
        assertNotNull(uResult)
        assertEquals(1, uResult!!.items.size)
        assertEquals("/Undo", uResult.items[0].label)
    }

    @Test
    fun typingModeWithTrailingSpace_offersPlanAndBuild() = runBlocking {
        val provider = SlashCommandCompletionProvider()

        val result = provider.complete(ChatCompletionContext("/mode ", TextRange(6)))
        assertNotNull(result)
        assertEquals(2, result!!.items.size)
        assertEquals("Plan", result.items[0].label)
        assertEquals("Build", result.items[1].label)
    }

    @Test
    fun typingModeWithoutTrailingSpace_offersPlanAndBuild() = runBlocking {
        val provider = SlashCommandCompletionProvider()

        val result = provider.complete(ChatCompletionContext("/mode", TextRange(5)))
        assertNotNull(result)
        assertEquals(2, result!!.items.size)
        assertEquals("Plan", result.items[0].label)
        assertEquals("Build", result.items[1].label)
    }

    @Test
    fun typingModeSubQuery_filtersPlanOrBuild() = runBlocking {
        val provider = SlashCommandCompletionProvider()

        val pResult = provider.complete(ChatCompletionContext("/mode p", TextRange(7)))
        assertNotNull(pResult)
        assertEquals(1, pResult!!.items.size)
        assertEquals("Plan", pResult.items[0].label)

        val bResult = provider.complete(ChatCompletionContext("/mode b", TextRange(7)))
        assertNotNull(bResult)
        assertEquals(1, bResult!!.items.size)
        assertEquals("Build", bResult.items[0].label)
    }

    @Test
    fun typingSkill_offersInstalledSkills() = runBlocking {
        val skills = listOf("unslop", "apple-design")
        val provider = SlashCommandCompletionProvider(listSkills = { skills })

        val result = provider.complete(ChatCompletionContext("/skill ", TextRange(7)))
        assertNotNull(result)
        assertEquals(2, result!!.items.size)
        assertEquals("unslop", result.items[0].label)
        assertEquals("apple-design", result.items[1].label)

        val filtered = provider.complete(ChatCompletionContext("/skill uns", TextRange(10)))
        assertNotNull(filtered)
        assertEquals(1, filtered!!.items.size)
        assertEquals("unslop", filtered.items[0].label)
    }

    @Test
    fun selectingMode_invokesCallback() = runBlocking {
        var selectedMode: AgentMode? = null
        val provider = SlashCommandCompletionProvider(
            onModeSelected = { selectedMode = it }
        )

        val result = provider.complete(ChatCompletionContext("/mode ", TextRange(6)))
        assertNotNull(result)
        result!!.items.first { it.label == "Plan" }.onSelect?.invoke()
        assertEquals(AgentMode.PLAN, selectedMode)

        result.items.first { it.label == "Build" }.onSelect?.invoke()
        assertEquals(AgentMode.BUILD, selectedMode)
    }

    @Test
    fun selectingSkill_invokesCallback() = runBlocking {
        var selectedSkill: String? = null
        val provider = SlashCommandCompletionProvider(
            listSkills = { listOf("unslop") },
            onSkillSelected = { selectedSkill = it }
        )

        val result = provider.complete(ChatCompletionContext("/skill ", TextRange(7)))
        assertNotNull(result)
        result!!.items.first().onSelect?.invoke()
        assertEquals("unslop", selectedSkill)
    }

    @Test
    fun nonSlashInput_returnsNull() = runBlocking {
        val provider = SlashCommandCompletionProvider()

        assertNull(provider.complete(ChatCompletionContext("hello", TextRange(5))))
        assertNull(provider.complete(ChatCompletionContext("path/sub", TextRange(8))))
    }

    @Test
    fun disabledActions_filtersOutDisabledActions() = runBlocking {
        val provider = SlashCommandCompletionProvider(
            disabledActions = { setOf("undo", "imagine") }
        )

        val result = provider.complete(ChatCompletionContext("/", TextRange(1)))
        assertNotNull(result)
        assertEquals(8, result!!.items.size)
        assertTrue(result.items.none { it.label == "/Undo" })
        assertTrue(result.items.none { it.label == "/Imagine" })
        assertTrue(result.items.any { it.label == "/Mode" })
    }

    @Test
    fun customActions_includedInAutocomplete() = runBlocking {
        val custom = CustomAction(
            id = "audit-1",
            name = "audit",
            prefix = "/audit",
            promptTemplate = "Audit codebase",
            enabled = true,
        )
        val provider = SlashCommandCompletionProvider(
            customActions = { listOf(custom) }
        )

        val result = provider.complete(ChatCompletionContext("/", TextRange(1)))
        assertNotNull(result)
        assertEquals(11, result!!.items.size)
        assertTrue(result.items.any { it.label == "/audit" })

        val filtered = provider.complete(ChatCompletionContext("/au", TextRange(3)))
        assertNotNull(filtered)
        assertEquals(1, filtered!!.items.size)
        assertEquals("/audit", filtered.items[0].label)
    }

    @Test
    fun customModes_includedInModeAutocomplete() = runBlocking {
        var selectedMode: CustomMode? = null
        val customMode = CustomMode(
            id = "arch-1",
            name = "Architect",
            systemPromptAddendum = "Focus on architecture",
            enabled = true,
        )
        val provider = SlashCommandCompletionProvider(
            customModes = { listOf(customMode) },
            onCustomModeSelected = { selectedMode = it }
        )

        val result = provider.complete(ChatCompletionContext("/mode ", TextRange(6)))
        assertNotNull(result)
        assertEquals(3, result!!.items.size)
        assertEquals("Plan", result.items[0].label)
        assertEquals("Build", result.items[1].label)
        assertEquals("Architect", result.items[2].label)

        result.items.first { it.label == "Architect" }.onSelect?.invoke()
        assertEquals("Architect", selectedMode?.name)
    }
}
