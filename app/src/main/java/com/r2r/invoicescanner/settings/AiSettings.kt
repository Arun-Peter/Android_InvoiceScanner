package com.r2r.invoicescanner.settings

import android.content.Context
import com.r2r.invoicescanner.BuildConfig

enum class AiProvider(val label: String, val hint: String) {
    NONE("On-device only", "Works offline. Rule-based reading — fine for clear printed receipts."),
    AZURE_OPENAI("Azure OpenAI", "Recommended for company use — your organisation's own Azure OpenAI resource (e.g. GPT-4o)."),
    OPENAI("OpenAI-compatible", "OpenAI or any service with an OpenAI-compatible chat API that accepts images.");

    companion object {
        fun parse(value: String?): AiProvider {
            val v = value?.trim().orEmpty()
            entries.firstOrNull { it.name.equals(v, ignoreCase = true) }?.let { return it }
            return when (v.lowercase()) {
                "azure", "azureopenai", "azure_openai" -> AZURE_OPENAI
                "openai" -> OPENAI
                else -> NONE
            }
        }
    }
}

data class AiSettings(
    val provider: AiProvider = AiProvider.NONE,
    val azureEndpoint: String = "",
    val azureDeployment: String = "",
    val azureApiVersion: String = DEFAULT_AZURE_API_VERSION,
    val openAiBaseUrl: String = DEFAULT_OPENAI_BASE_URL,
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
    val apiKey: String = "",
) {
    val isConfigured: Boolean
        get() = when (provider) {
            AiProvider.NONE -> false
            AiProvider.AZURE_OPENAI -> azureEndpoint.isNotBlank() && azureDeployment.isNotBlank() && apiKey.isNotBlank()
            AiProvider.OPENAI -> openAiBaseUrl.isNotBlank() && openAiModel.isNotBlank() && apiKey.isNotBlank()
        }

    val modelLabel: String
        get() = when (provider) {
            AiProvider.NONE -> ""
            AiProvider.AZURE_OPENAI -> azureDeployment.trim()
            AiProvider.OPENAI -> openAiModel.trim()
        }

    fun chatUrl(): String = when (provider) {
        AiProvider.AZURE_OPENAI -> {
            // Accept "https://res.openai.azure.com", with or without a trailing path.
            val full = withScheme(azureEndpoint).trimEnd('/')
            val hostStart = full.indexOf("://").let { if (it >= 0) it + 3 else 0 }
            val cut = full.indexOf("/openai", startIndex = hostStart)
            val base = (if (cut >= 0) full.substring(0, cut) else full).trimEnd('/')
            val version = azureApiVersion.trim().ifBlank { DEFAULT_AZURE_API_VERSION }
            "$base/openai/deployments/${azureDeployment.trim()}/chat/completions?api-version=$version"
        }
        else -> withScheme(openAiBaseUrl).trimEnd('/').removeSuffix("/chat/completions") + "/chat/completions"
    }

    private fun withScheme(url: String): String {
        val u = url.trim()
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    }

    companion object {
        const val DEFAULT_AZURE_API_VERSION = "2024-10-21"
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    }
}

/**
 * Stores the AI settings privately on the phone (app storage is excluded from backup and
 * kept across app updates). The API key is additionally encrypted with a key held in the
 * Android Keystore, which never leaves the phone's secure hardware.
 */
object SettingsStore {
    private const val PREFS = "ai_settings"

    fun load(context: Context): AiSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("provider")) return buildDefaults()
        // Version 1.1 kept the key as plain text; move it to the encrypted entry.
        p.getString("apiKey", null)?.let { plain ->
            val sealed = SecretBox.seal(plain.trim())
            if (sealed != null) p.edit().putString("apiKeyEnc", sealed).remove("apiKey").apply()
        }
        return AiSettings(
            provider = AiProvider.parse(p.getString("provider", null)),
            azureEndpoint = p.getString("azureEndpoint", "").orEmpty(),
            azureDeployment = p.getString("azureDeployment", "").orEmpty(),
            azureApiVersion = p.getString("azureApiVersion", AiSettings.DEFAULT_AZURE_API_VERSION).orEmpty(),
            openAiBaseUrl = p.getString("openAiBaseUrl", AiSettings.DEFAULT_OPENAI_BASE_URL).orEmpty(),
            openAiModel = p.getString("openAiModel", AiSettings.DEFAULT_OPENAI_MODEL).orEmpty(),
            apiKey = p.getString("apiKey", null)
                ?: p.getString("apiKeyEnc", null)?.let { SecretBox.open(it) }.orEmpty(),
        )
    }

    fun save(context: Context, s: AiSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("provider", s.provider.name)
            .putString("azureEndpoint", s.azureEndpoint.trim())
            .putString("azureDeployment", s.azureDeployment.trim())
            .putString("azureApiVersion", s.azureApiVersion.trim())
            .putString("openAiBaseUrl", s.openAiBaseUrl.trim())
            .putString("openAiModel", s.openAiModel.trim())
            .apply {
                val sealed = SecretBox.seal(s.apiKey.trim())
                // If the Keystore is unavailable on this phone, fall back to private storage only.
                if (sealed != null) putString("apiKeyEnc", sealed).remove("apiKey") else putString("apiKey", s.apiKey.trim())
            }
            .apply()
    }

    /** Values baked in at build time from local.properties (optional). */
    private fun buildDefaults(): AiSettings = AiSettings(
        provider = AiProvider.parse(BuildConfig.DEFAULT_AI_PROVIDER),
        azureEndpoint = BuildConfig.DEFAULT_AZURE_ENDPOINT,
        azureDeployment = BuildConfig.DEFAULT_AZURE_DEPLOYMENT,
        azureApiVersion = BuildConfig.DEFAULT_AZURE_API_VERSION.ifBlank { AiSettings.DEFAULT_AZURE_API_VERSION },
        openAiBaseUrl = BuildConfig.DEFAULT_OPENAI_BASE_URL.ifBlank { AiSettings.DEFAULT_OPENAI_BASE_URL },
        openAiModel = BuildConfig.DEFAULT_OPENAI_MODEL.ifBlank { AiSettings.DEFAULT_OPENAI_MODEL },
        apiKey = BuildConfig.DEFAULT_AI_API_KEY,
    )
}
