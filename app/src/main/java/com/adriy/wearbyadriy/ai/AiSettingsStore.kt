package com.adriy.wearbyadriy.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiProvider(val key: String, val label: String, val defaultModel: String, val keyHint: String, val keyUrl: String) {
    OPENAI("openai", "OpenAI", "gpt-4o-mini", "sk-…", "https://platform.openai.com/api-keys"),
    GEMINI("gemini", "Google Gemini", "gemini-2.0-flash", "AIza…", "https://aistudio.google.com/app/apikey"),
    ANTHROPIC("anthropic", "Anthropic Claude", "claude-haiku-5-5", "sk-ant-…", "https://console.anthropic.com/settings/keys");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: OPENAI
    }
}

data class AiConfig(val provider: AiProvider, val model: String, val apiKey: String, val enabled: Boolean) {
    val ready: Boolean get() = enabled && apiKey.isNotBlank() && model.isNotBlank()
}

/**
 * API keys are stored on this device only, encrypted with a key held in the Android Keystore,
 * and namespaced per signed-in account so another account on the same phone cannot use them.
 * They are never sent to Supabase and are excluded from Android backup.
 */
class AiSettingsStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy { open() }

    private fun open(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // Keystore entry can be lost after a device restore; start fresh rather than crash.
        context.deleteSharedPreferences(FILE)
        create()
    }

    private fun create(): SharedPreferences {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(userId: String): AiConfig {
        val provider = AiProvider.from(prefs.getString("$userId.provider", null))
        return AiConfig(
            provider = provider,
            model = prefs.getString("$userId.${provider.key}.model", null) ?: provider.defaultModel,
            apiKey = prefs.getString("$userId.${provider.key}.key", null) ?: "",
            enabled = prefs.getBoolean("$userId.enabled", true),
        )
    }

    fun loadFor(userId: String, provider: AiProvider): AiConfig = AiConfig(
        provider = provider,
        model = prefs.getString("$userId.${provider.key}.model", null) ?: provider.defaultModel,
        apiKey = prefs.getString("$userId.${provider.key}.key", null) ?: "",
        enabled = prefs.getBoolean("$userId.enabled", true),
    )

    fun save(userId: String, config: AiConfig) {
        prefs.edit()
            .putString("$userId.provider", config.provider.key)
            .putString("$userId.${config.provider.key}.model", config.model.trim())
            .putString("$userId.${config.provider.key}.key", config.apiKey.trim())
            .putBoolean("$userId.enabled", config.enabled)
            .apply()
    }

    fun deleteKey(userId: String, provider: AiProvider) {
        prefs.edit().remove("$userId.${provider.key}.key").apply()
    }

    fun deleteAllFor(userId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$userId.") }.forEach { editor.remove(it) }
        editor.apply()
    }

    private companion object { const val FILE = "ai_secure_prefs" }
}
