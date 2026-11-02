package com.r2r.invoicescanner.extract

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.settings.AiProvider
import com.r2r.invoicescanner.settings.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.math.max

class AiException(message: String) : Exception(message)

/** Sends the cropped invoice image (plus the on-device OCR text) to a vision LLM. */
class AiExtractor(private val settings: AiSettings) {

    suspend fun extract(
        imageFile: File,
        ocrText: String,
        categories: List<ExpenseCategory> = ExpenseCategory.BUILT_IN,
    ): AiResult = withContext(Dispatchers.IO) {
        val imageB64 = encodeForUpload(imageFile)
        val userContent = JSONArray()
            .put(JSONObject().put("type", "text").put("text", AiPrompt.userText(ocrText)))
            .put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$imageB64").put("detail", "high")
                )
            )
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", AiPrompt.system(categories)))
            .put(JSONObject().put("role", "user").put("content", userContent))
        AiResponseParser.parse(chat(messages), categories)
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", "Reply with this JSON object only: {\"ok\": true}")
        )
        chat(messages)
        "Connected to ${settings.provider.label} (${settings.modelLabel})"
    }

    private fun chat(messages: JSONArray): String {
        val body = JSONObject()
            .put("messages", messages)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0)
        if (settings.provider == AiProvider.OPENAI) body.put("model", settings.openAiModel.trim())
        return try {
            post(body)
        } catch (e: AiException) {
            // Some newer (reasoning) models reject a custom temperature — retry without it.
            if (e.message?.contains("temperature", ignoreCase = true) == true) {
                body.remove("temperature")
                post(body)
            } else {
                throw e
            }
        }
    }

    private fun post(body: JSONObject): String {
        val builder = Request.Builder()
            .url(settings.chatUrl())
            .post(body.toString().toRequestBody(JSON_MEDIA))
        when (settings.provider) {
            AiProvider.AZURE_OPENAI -> builder.header("api-key", settings.apiKey.trim())
            else -> builder.header("Authorization", "Bearer ${settings.apiKey.trim()}")
        }
        return client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw AiException(httpError(response.code, text))
            val choices = JSONObject(text).optJSONArray("choices")
            if (choices == null || choices.length() == 0) throw AiException("the AI service returned no answer")
            val choice = choices.getJSONObject(0)
            if (choice.optString("finish_reason") == "content_filter") {
                throw AiException("the AI service's content filter blocked this image")
            }
            val content = choice.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) throw AiException("the AI service returned an empty answer")
            content
        }
    }

    private fun httpError(code: Int, body: String): String {
        val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
            .getOrNull().orEmpty().take(200)
        val base = when (code) {
            400 -> "the AI service rejected the request"
            401, 403 -> "the API key was rejected"
            404 -> "the endpoint or deployment/model name was not found"
            429 -> "the AI service is busy (rate limit) — try again shortly"
            in 500..599 -> "the AI service had an internal error"
            else -> "the AI service returned an error"
        }
        return "HTTP $code, $base" + if (detail.isNotBlank()) " — $detail" else ""
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        fun friendlyError(e: Throwable): String = when (e) {
            is AiException -> e.message ?: "AI error"
            is UnknownHostException -> "no internet connection, or the endpoint address is wrong"
            is SocketTimeoutException -> "the AI service took too long to respond"
            is SSLException -> "a secure connection could not be made (check the endpoint or company network)"
            is IOException -> "network problem: ${e.localizedMessage ?: "unknown"}"
            is JSONException -> "the AI answer could not be read"
            else -> e.localizedMessage ?: e.javaClass.simpleName
        }

        /** Downscales to max 1600 px on the long side and returns base64 JPEG. */
        fun encodeForUpload(file: File, maxSide: Int = 1600): String {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: throw IOException("the scanned image could not be read")
            val longSide = max(bitmap.width, bitmap.height)
            val scaled = if (longSide > maxSide) {
                val r = maxSide.toFloat() / longSide
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * r).toInt(), (bitmap.height * r).toInt(), true)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }
}
