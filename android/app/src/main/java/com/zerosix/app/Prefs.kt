package com.zerosix.app

import android.content.Context

object Prefs {
    /** TheSportsDB's public free key. */
    const val FREE_KEY = "123"

    private fun sp(c: Context) = c.getSharedPreferences("zero_six", Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = (sp(c).getString("api_key", "") ?: "").ifBlank { FREE_KEY }

    fun saveApiKey(c: Context, key: String) {
        sp(c).edit().putString("api_key", key.trim()).apply()
    }

    fun isFreeKey(key: String): Boolean = key == FREE_KEY || key == "3"
}
