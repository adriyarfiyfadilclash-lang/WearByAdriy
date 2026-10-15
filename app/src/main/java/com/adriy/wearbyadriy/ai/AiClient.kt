package com.adriy.wearbyadriy.ai

import com.adriy.wearbyadriy.util.UserError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Calls the AI provider the user configured, directly from the device with their own key. */
class AiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    /** Sends one prompt and returns the model's text reply. Throws [UserError] with a readable reason. */
    suspend fun complete(config: AiConfig, prompt: String): String = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) throw UserError("API key ${config.provider.label} belum diisi di Pengaturan.")
        val request = when (config.provider) {
            AiProvider.OPENAI -> Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(buildJsonObject {
                    put("model", config.model)
                    putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", prompt) } }
                    putJsonObject("response_format") { put("type", "json_object") }
                }.toString().toRequestBody(jsonType))
                .build()
            AiProvider.GEMINI -> Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent")
                .header("x-goog-api-key", config.apiKey)
                .post(buildJsonObject {
                    putJsonArray("contents") { addJsonObject { putJsonArray("parts") { addJsonObject { put("text", prompt) } } } }
                    putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
                }.toString().toRequestBody(jsonType))
                .build()
            AiProvider.ANTHROPIC -> Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", config.apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(buildJsonObject {
                    put("model", config.model)
                    put("max_tokens", 3000)
                    putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", prompt) } }
                }.toString().toRequestBody(jsonType))
                .build()
        }

        val (code, body) = try {
            http.newCall(request).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
        } catch (e: SocketTimeoutException) {
            throw UserError("${config.provider.label} tidak menjawab tepat waktu. Coba lagi.")
        } catch (e: IOException) {
            throw UserError("Tidak bisa menghubungi ${config.provider.label}. Periksa koneksi internet.")
        }

        if (code !in 200..299) throw UserError(httpError(config, code, body))

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw UserError("Jawaban ${config.provider.label} tidak bisa dibaca.")
        val text = runCatching { extractText(config.provider, root) }.getOrNull()
        if (text.isNullOrBlank()) throw UserError("${config.provider.label} tidak mengembalikan jawaban. Coba lagi.")
        text
    }

    private fun extractText(provider: AiProvider, root: JsonObject): String? = when (provider) {
        AiProvider.OPENAI -> root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        AiProvider.GEMINI -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("")
        AiProvider.ANTHROPIC -> root["content"]?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("")
    }

    private fun httpError(config: AiConfig, code: Int, body: String): String {
        val detail = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            (o["error"]?.let { e -> runCatching { e.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: e.jsonPrimitive.contentOrNull })
        }.getOrNull()
        val name = config.provider.label
        return when (code) {
            400 -> "Permintaan ditolak oleh $name${detail?.let { ": $it" } ?: "."}"
            401, 403 -> "API key $name ditolak. Periksa kembali key di Pengaturan > API AI."
            404 -> "Model \"${config.model}\" tidak ditemukan di $name. Ganti nama model di Pengaturan."
            408 -> "$name tidak menjawab tepat waktu. Coba lagi."
            429 -> "Batas pemakaian atau kuota $name tercapai. Tunggu sebentar atau periksa saldo akun."
            in 500..599 -> "Layanan $name sedang bermasalah (kode $code). Coba lagi nanti."
            else -> "$name mengembalikan kesalahan (kode $code)${detail?.let { ": $it" } ?: "."}"
        }
    }

    /** A tiny round trip that proves the key and model work. */
    suspend fun test(config: AiConfig) {
        val reply = complete(config, "Reply with only this JSON and nothing else: {\"ok\": true}")
        if (!reply.contains("ok")) throw UserError("${config.provider.label} menjawab, tapi tidak sesuai format yang diharapkan.")
    }

}
