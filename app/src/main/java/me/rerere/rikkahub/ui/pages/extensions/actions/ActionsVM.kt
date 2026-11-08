package me.rerere.rikkahub.ui.pages.extensions.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.CustomAction
import me.rerere.rikkahub.data.datastore.CustomMode
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

class ActionsVM(
    private val settingsStore: SettingsStore
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun toggleBuiltInAction(actionId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { current ->
                val currentDisabled = current.disabledBuiltInActions.toMutableSet()
                val idLower = actionId.lowercase().removePrefix("/")
                if (enabled) {
                    currentDisabled.remove(idLower)
                    currentDisabled.remove("/$idLower")
                } else {
                    currentDisabled.add(idLower)
                }
                current.copy(disabledBuiltInActions = currentDisabled)
            }
        }
    }

    fun addCustomAction(name: String, prefix: String, icon: String, promptTemplate: String) {
        val cleanPrefix = if (prefix.trim().startsWith("/")) prefix.trim() else "/${prefix.trim()}"
        val newAction = CustomAction(
            id = Uuid.random().toString(),
            name = name.trim(),
            prefix = cleanPrefix,
            icon = icon,
            promptTemplate = promptTemplate.trim(),
            enabled = true,
        )
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(customActions = current.customActions + newAction)
            }
        }
    }

    fun updateCustomAction(updated: CustomAction) {
        val cleanPrefix = if (updated.prefix.trim().startsWith("/")) updated.prefix.trim() else "/${updated.prefix.trim()}"
        val sanitized = updated.copy(
            name = updated.name.trim(),
            prefix = cleanPrefix,
            promptTemplate = updated.promptTemplate.trim(),
        )
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(
                    customActions = current.customActions.map { if (it.id == sanitized.id) sanitized else it }
                )
            }
        }
    }

    fun deleteCustomAction(id: String) {
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(
                    customActions = current.customActions.filterNot { it.id == id }
                )
            }
        }
    }

    fun toggleCustomAction(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(
                    customActions = current.customActions.map { if (it.id == id) it.copy(enabled = enabled) else it }
                )
            }
        }
    }

    fun addCustomMode(name: String, icon: String, systemPromptAddendum: String) {
        val newMode = CustomMode(
            id = Uuid.random().toString(),
            name = name.trim(),
            icon = icon,
            systemPromptAddendum = systemPromptAddendum.trim(),
            enabled = true,
        )
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(customModes = current.customModes + newMode)
            }
        }
    }

    fun updateCustomMode(updated: CustomMode) {
        val sanitized = updated.copy(
            name = updated.name.trim(),
            systemPromptAddendum = updated.systemPromptAddendum.trim(),
        )
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(
                    customModes = current.customModes.map { if (it.id == sanitized.id) sanitized else it }
                )
            }
        }
    }

    fun deleteCustomMode(id: String) {
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(
                    customModes = current.customModes.filterNot { it.id == id }
                )
            }
        }
    }

    fun toggleCustomMode(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(
                    customModes = current.customModes.map { if (it.id == id) it.copy(enabled = enabled) else it }
                )
            }
        }
    }
}
