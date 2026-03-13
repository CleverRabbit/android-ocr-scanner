package com.contract.scanner

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

/**
 * Helper class for managing app settings using SharedPreferences
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        private const val KEY_URL_TEMPLATE = "url_template"
        private const val KEY_AUTO_OPEN = "auto_open"
        
        const val DEFAULT_URL_TEMPLATE = "https://order.matrixhome.net/social/family/view/{NUMBER}"
    }

    var urlTemplate: String
        get() = prefs.getString(KEY_URL_TEMPLATE, DEFAULT_URL_TEMPLATE) ?: DEFAULT_URL_TEMPLATE
        set(value) = prefs.edit().putString(KEY_URL_TEMPLATE, value).apply()

    var autoOpen: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OPEN, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_OPEN, value).apply()
}
