package me.rerere.ai.provider.providers.ninerouter

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.provider.providers.openai.OpenAIProvider
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import okhttp3.OkHttpClient

class NineRouterProvider(
    client: OkHttpClient,
    context: Context? = null,
    private val nineRouterService: NineRouterService,
) : Provider<ProviderSetting.NineRouter> {

    private val openAIProvider = OpenAIProvider(client, context)

    private fun toOpenAISetting(setting: ProviderSetting.NineRouter): ProviderSetting.OpenAI {
        return ProviderSetting.OpenAI(
            id = setting.id,
            enabled = setting.enabled,
            name = setting.name,
            models = setting.models,
            balanceOption = setting.balanceOption,
            builtIn = setting.builtIn,
            apiKey = setting.apiKey,
            baseUrl = setting.baseUrl,
            chatCompletionsPath = "/chat/completions",
            useResponseApi = false,
        )
    }

    override suspend fun listModels(providerSetting: ProviderSetting.NineRouter): List<Model> {
        return runCatching {
            nineRouterService.fetchModels(providerSetting.baseUrl)
        }.getOrElse {
            // Fallback to OpenAI listModels if custom parser fails
            openAIProvider.listModels(toOpenAISetting(providerSetting))
        }
    }

    override suspend fun getBalance(providerSetting: ProviderSetting.NineRouter): String {
        return "Local Gateway"
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.NineRouter,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): TextGenerationResult {
        return openAIProvider.generateText(
            toOpenAISetting(providerSetting),
            messages,
            params
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.NineRouter,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<StreamChunk> {
        return openAIProvider.streamText(
            toOpenAISetting(providerSetting),
            messages,
            params
        )
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): Flow<ImageGenerationItem> {
        val openAISetting = if (providerSetting is ProviderSetting.NineRouter) {
            toOpenAISetting(providerSetting)
        } else {
            providerSetting
        }
        return openAIProvider.generateImage(openAISetting, params)
    }

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams
    ): Flow<ImageGenerationItem> {
        val openAISetting = if (providerSetting is ProviderSetting.NineRouter) {
            toOpenAISetting(providerSetting)
        } else {
            providerSetting
        }
        return openAIProvider.editImage(openAISetting, params)
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.NineRouter,
        params: EmbeddingGenerationParams
    ): EmbeddingGenerationResult {
        return openAIProvider.generateEmbedding(
            toOpenAISetting(providerSetting),
            params
        )
    }
}
