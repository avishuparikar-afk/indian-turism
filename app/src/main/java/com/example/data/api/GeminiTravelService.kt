package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiAdviceResult(
    val text: String,
    val isLiveAi: Boolean,
    val errorMessage: String? = null
)

class GeminiTravelService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateTravelAdvice(
        userPrompt: String,
        contextInfo: String = "",
        apiKey: String = ""
    ): GeminiAdviceResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()

        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY" || cleanKey == "MY_OPENROUTER_API_KEY") {
            val fallback = generateLocalTravelAdvice(userPrompt)
            return@withContext GeminiAdviceResult(
                text = "⚡ Notice: Running in Offline Guide mode. To activate live dynamic AI responses, add a free Gemini API Key from Google AI Studio (aistudio.google.com) or OpenRouter (openrouter.ai).\n\n" + fallback,
                isLiveAi = false
            )
        }

        val systemInstruction = """
            You are 'Namaste India AI', an expert, helpful, and culturally authentic India Tourism Guide.
            Provide clear, practical, inspiring advice for travelers.
            Always include:
            - Realistic prices in Indian Rupees (₹)
            - Best time to visit and seasonal advice
            - Transportation suggestions (Vande Bharat trains, domestic flights, metro)
            - Local culinary specialties & street foods
            - Cultural etiquette (clothing, photography, temple customs)
            Use clean markdown formatting with bullet points and emojis.
        """.trimIndent()

        val promptWithContext = if (contextInfo.isNotBlank()) {
            "$systemInstruction\n\n[Destination Context: $contextInfo]\n\nUser Question: $userPrompt"
        } else {
            "$systemInstruction\n\nUser Question: $userPrompt"
        }

        val isOpenRouter = cleanKey.startsWith("sk-or-") || cleanKey.startsWith("sk-")

        if (isOpenRouter) {
            return@withContext callOpenRouter(cleanKey, promptWithContext, userPrompt)
        } else {
            return@withContext callGoogleGemini(cleanKey, promptWithContext, userPrompt)
        }
    }

    private fun callOpenRouter(apiKey: String, fullPrompt: String, userPrompt: String): GeminiAdviceResult {
        try {
            val url = "https://openrouter.ai/api/v1/chat/completions"
            val requestJson = JSONObject().apply {
                put("model", "google/gemini-2.5-flash")
                val messagesArray = JSONArray().apply {
                    val userMsg = JSONObject().apply {
                        put("role", "user")
                        put("content", fullPrompt)
                    }
                    put(userMsg)
                }
                put("messages", messagesArray)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://ai.studio")
                .addHeader("X-Title", "India Tourism Explorer")
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                var errorDetail = "HTTP ${response.code}"
                try {
                    if (responseBody != null) {
                        val errObj = JSONObject(responseBody).optJSONObject("error")
                        val msg = errObj?.optString("message")
                        if (!msg.isNullOrBlank()) errorDetail = msg
                    }
                } catch (_: Exception) {}

                Log.e("GeminiTravelService", "OpenRouter call failed ($errorDetail)")
                return GeminiAdviceResult(
                    text = "⚠️ OpenRouter AI Error ($errorDetail).\n\n--- Offline Travel Advice ---\n" + generateLocalTravelAdvice(userPrompt),
                    isLiveAi = false,
                    errorMessage = errorDetail
                )
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val msg = choice.optJSONObject("message")
                val content = msg?.optString("content")
                if (!content.isNullOrBlank()) {
                    return GeminiAdviceResult(
                        text = content.trim(),
                        isLiveAi = true
                    )
                }
            }

            return GeminiAdviceResult(
                text = generateLocalTravelAdvice(userPrompt),
                isLiveAi = false
            )
        } catch (e: Exception) {
            Log.e("GeminiTravelService", "Exception during OpenRouter call", e)
            return GeminiAdviceResult(
                text = "⚠️ Network connection error: ${e.localizedMessage ?: "Unable to reach OpenRouter AI"}.\n\n--- Offline Guide ---\n" + generateLocalTravelAdvice(userPrompt),
                isLiveAi = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun callGoogleGemini(apiKey: String, fullPrompt: String, userPrompt: String): GeminiAdviceResult {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val userObj = JSONObject().apply {
                        put("role", "user")
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", fullPrompt) })
                        }
                        put("parts", partsArray)
                    }
                    put(userObj)
                }
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                    put("topK", 40)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                var errorDetail = "HTTP ${response.code}"
                try {
                    if (responseBody != null) {
                        val errObj = JSONObject(responseBody).optJSONObject("error")
                        val msg = errObj?.optString("message")
                        if (!msg.isNullOrBlank()) {
                            errorDetail = msg
                        }
                    }
                } catch (_: Exception) {}

                Log.e("GeminiTravelService", "API call failed ($errorDetail)")
                return GeminiAdviceResult(
                    text = "⚠️ Gemini API Error ($errorDetail).\n\nPlease verify your API key in the top banner or enter a valid free key from Google AI Studio (aistudio.google.com).\n\n--- Offline Travel Advice ---\n" + generateLocalTravelAdvice(userPrompt),
                    isLiveAi = false,
                    errorMessage = errorDetail
                )
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val replyText = parts.getJSONObject(0).optString("text")
                    if (replyText.isNotBlank()) {
                        return GeminiAdviceResult(
                            text = replyText.trim(),
                            isLiveAi = true
                        )
                    }
                }
            }

            return GeminiAdviceResult(
                text = generateLocalTravelAdvice(userPrompt),
                isLiveAi = false
            )
        } catch (e: Exception) {
            Log.e("GeminiTravelService", "Network/Exception during Gemini call", e)
            return GeminiAdviceResult(
                text = "⚠️ Network connection error: ${e.localizedMessage ?: "Unable to reach Google Gemini servers"}.\n\n--- Offline Guide ---\n" + generateLocalTravelAdvice(userPrompt),
                isLiveAi = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    suspend fun testApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext Result.failure(Exception("API Key cannot be empty"))

        val isOpenRouter = cleanKey.startsWith("sk-or-") || cleanKey.startsWith("sk-")
        if (isOpenRouter) {
            try {
                val url = "https://openrouter.ai/api/v1/chat/completions"
                val requestJson = JSONObject().apply {
                    put("model", "google/gemini-2.5-flash")
                    val messagesArray = JSONArray().apply {
                        val userMsg = JSONObject().apply {
                            put("role", "user")
                            put("content", "Say: 'Namaste! OpenRouter Gemini is connected.' in under 10 words.")
                        }
                        put(userMsg)
                    }
                    put("messages", messagesArray)
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $cleanKey")
                    .addHeader("HTTP-Referer", "https://ai.studio")
                    .addHeader("X-Title", "India Tourism Explorer")
                    .post(requestJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    var err = "HTTP ${response.code}"
                    if (responseBody != null) {
                        val errObj = JSONObject(responseBody).optJSONObject("error")
                        val msg = errObj?.optString("message")
                        if (!msg.isNullOrBlank()) err = msg
                    }
                    return@withContext Result.failure(Exception(err))
                }

                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                val text = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                Result.success(text?.trim() ?: "Connected to OpenRouter Gemini successfully!")
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$cleanKey"
                val requestJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val userObj = JSONObject().apply {
                            put("role", "user")
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply { put("text", "Say: 'Namaste! Gemini 3.5 Flash is connected.' in under 10 words.") })
                            }
                            put("parts", partsArray)
                        }
                        put(userObj)
                    }
                    put("contents", contentsArray)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    var err = "HTTP ${response.code}"
                    if (responseBody != null) {
                        val errObj = JSONObject(responseBody).optJSONObject("error")
                        val msg = errObj?.optString("message")
                        if (!msg.isNullOrBlank()) err = msg
                    }
                    return@withContext Result.failure(Exception(err))
                }

                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                Result.success(text?.trim() ?: "Connected successfully!")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun generateLocalTravelAdvice(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("jaipur") || lower.contains("rajasthan") -> """
                🏰 **Jaipur & Royal Rajasthan Travel Guide**:
                • **Best Time**: October to March (cool pleasant weather 18°C–25°C).
                • **Top Highlights**: Amber Fort (catch the morning light), Hawa Mahal, City Palace, and Jal Mahal.
                • **Must-Try Cuisine**: Dal Baati Churma, Pyaaz Kachori at Rawat Mishtan Bhandar, and authentic Ghewar.
                • **Travel Tip**: Get the Composite Ticket for major monuments to skip individual queues!
                • **Budget**: ₹2,500 – ₹4,500/day for mid-range comfortable stay, cab, and dining.
            """.trimIndent()

            lower.contains("kerala") || lower.contains("alleppey") || lower.contains("munnar") -> """
                🌴 **Kerala 'God's Own Country' Travel Guide**:
                • **Best Time**: September to March; monsoons (June–August) are great for Ayurvedic retreats.
                • **Top Highlights**: Alleppey Houseboat cruise, Munnar tea plantation hills, and Fort Kochi colonial art streets.
                • **Must-Try Cuisine**: Karimeen Pollichathu, Appam with vegetable stew, Malabar Parotta, and tender coconut water.
                • **Local Tip**: Book government KTDC accredited houseboats for safety and verified rates.
                • **Budget**: ₹3,000 – ₹6,000/day including houseboat experience.
            """.trimIndent()

            lower.contains("taj mahal") || lower.contains("agra") -> """
                🏛️ **Taj Mahal & Agra Insider Guide**:
                • **Timing**: Open sunrise to sunset. Closed on Fridays for prayers!
                • **Best Photography Spot**: East Gate sunrise, and across the river at Mehtab Bagh for sunset silhouettes.
                • **Cultural Etiquette**: Shoe covers are provided; large bags and tripods are not permitted inside.
                • **Local Flavor**: Agra Petha (try Angoori or Kesar flavor at Panchhi Petha), Bedai & Jalebi breakfast.
                • **Transport**: Take the Gatimaan Express or Vande Bharat train from Delhi (1 hr 40 min).
            """.trimIndent()

            lower.contains("varanasi") || lower.contains("kashi") || lower.contains("ghat") -> """
                🪔 **Varanasi Spiritual & Heritage Guide**:
                • **Sacred Ritual**: Attend the 6:45 PM evening Maha Ganga Aarti at Dashashwamedh Ghat from a traditional boat.
                • **Sunrise Experience**: Take an early morning hand-rowed boat ride from Assi Ghat to Manikarnika Ghat.
                • **Cultural Etiquette**: Dress modestly covering shoulders and knees; maintain respectful silence at cremation ghats.
                • **Culinary Delights**: Banarasi Paan, Malaiyo winter sweet, Tamatar Chaat at Kashi Chaat Bhandar.
                • **Excursion**: Visit Sarnath (10 km away) where Lord Buddha delivered his first sermon.
            """.trimIndent()

            lower.contains("budget") || lower.contains("cost") || lower.contains("money") -> """
                💰 **India Travel Budget Breakdown**:
                • **Budget Backpacking**: ₹1,200 – ₹1,800/day (hostels, sleeper/3AC trains, local thalis).
                • **Mid-Range Comfort**: ₹3,000 – ₹5,500/day (3-star boutique hotels, AC cabs, guided monument visits).
                • **Luxury Heritage**: ₹10,000+/day (palace hotels, private chauffeurs, fine dining).
                • **Payment**: UPI (PhonePe, Google Pay) is accepted across 99% of stalls, shops, and taxis. Keep ₹1,000 cash for rural sites.
            """.trimIndent()

            lower.contains("food") || lower.contains("eat") || lower.contains("dish") -> """
                🍛 **India Culinary Explorer**:
                • **North**: Butter Chicken, Rogan Josh, Chole Bhature, Amritsari Kulcha.
                • **South**: Masala Dosa, Idli-Vada with Sambhar, Chettinad Chicken, Filter Coffee.
                • **West**: Pav Bhaji, Goan Prawn Curry, Dhokla, Vada Pav.
                • **East**: Kolkata Biryani, Momos, Shorshe Ilish, Rasgulla.
                • **Hygiene Rule**: Drink sealed bottled water (Bisleri, Kinley), eat at busy venues with high turnover, and enjoy hot freshly prepared food.
            """.trimIndent()

            else -> """
                🇮🇳 **Namaste! Your India Travel Recommendation**:
                India offers an incredible blend of architectural wonders, spiritual sanctuaries, lush landscapes, and diverse culinary traditions.

                • **Recommended Circuit**: The classic **Golden Triangle** (Delhi - Agra - Jaipur) for first-timers, or **Southern Serenity** (Kerala - Hampi - Madurai) for tranquil nature and temple architecture.
                • **Connectivity**: Fast booking via IRCTC for trains, and widespread domestic flights connecting all major heritage hubs.
                • **Offline Support**: All 20+ top landmarks are saved in your offline database with timings, tickets, and routes!
                • **Pro Tip**: You can ask me to generate a 3-day or 5-day custom itinerary for any destination in India.
            """.trimIndent()
        }
    }
}
