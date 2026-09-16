package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class ApiKeyManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_api_keys", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_GEMINI = "user_gemini_api_key"
        private const val KEY_USER_MAPS = "user_maps_api_key"
        private const val KEY_USER_OPENWEATHER = "user_openweather_api_key"
        private const val KEY_USER_GEOAPIFY = "user_geoapify_api_key"
    }

    fun getGeminiApiKey(): String {
        val userKey = prefs.getString(KEY_USER_GEMINI, "")?.trim() ?: ""
        if (userKey.isNotBlank()) return userKey

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_USER_GEMINI, key.trim()).apply()
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_USER_GEMINI).apply()
    }

    fun hasCustomGeminiKey(): Boolean {
        return !prefs.getString(KEY_USER_GEMINI, "").isNullOrBlank()
    }

    fun isGeminiKeyConfigured(): Boolean {
        return getGeminiApiKey().isNotBlank()
    }

    fun getMapsApiKey(): String {
        val userKey = prefs.getString(KEY_USER_MAPS, "")?.trim() ?: ""
        if (userKey.isNotBlank()) return userKey

        val buildKey = BuildConfig.MAPS_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_MAPS_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveMapsApiKey(key: String) {
        prefs.edit().putString(KEY_USER_MAPS, key.trim()).apply()
    }

    fun isMapsKeyConfigured(): Boolean = getMapsApiKey().isNotBlank()

    fun getOpenWeatherApiKey(): String {
        val userKey = prefs.getString(KEY_USER_OPENWEATHER, "")?.trim() ?: ""
        if (userKey.isNotBlank()) return userKey

        val buildKey = try {
            BuildConfig.OPENWEATHER_API_KEY.trim()
        } catch (_: Exception) { "" }
        if (buildKey.isNotBlank() && buildKey != "MY_OPENWEATHER_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveOpenWeatherApiKey(key: String) {
        prefs.edit().putString(KEY_USER_OPENWEATHER, key.trim()).apply()
    }

    fun isOpenWeatherKeyConfigured(): Boolean = getOpenWeatherApiKey().isNotBlank()

    fun getGeoapifyApiKey(): String {
        val userKey = prefs.getString(KEY_USER_GEOAPIFY, "")?.trim() ?: ""
        if (userKey.isNotBlank()) return userKey

        val buildKey = try {
            BuildConfig.GEOAPIFY_API_KEY.trim()
        } catch (_: Exception) { "" }
        if (buildKey.isNotBlank() && buildKey != "MY_GEOAPIFY_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveGeoapifyApiKey(key: String) {
        prefs.edit().putString(KEY_USER_GEOAPIFY, key.trim()).apply()
    }

    fun isGeoapifyKeyConfigured(): Boolean = getGeoapifyApiKey().isNotBlank()

}
