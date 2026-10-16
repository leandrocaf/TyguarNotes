package com.lcaf.tyguarnotes.data.repository

import android.content.Context
import com.lcaf.tyguarnotes.ui.util.Constants

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(
        Constants.PREFERENCES_HELPER_SETTINGS_NAME,
        Context.MODE_PRIVATE
    )

    var useBiometric: Boolean
        get() = prefs.getBoolean(Constants.PREFERENCES_SETTINGS_USE_BIOMETRIC, false)
        set(value) {
            prefs.edit().putBoolean(Constants.PREFERENCES_SETTINGS_USE_BIOMETRIC, value).apply()
        }

    var useDeviceCredentials: Boolean
        get() = prefs.getBoolean(Constants.PREFERENCES_SETTINGS_USE_DEVICE_CREDENTIALS, false)
        set(value) {
            prefs.edit().putBoolean(Constants.PREFERENCES_SETTINGS_USE_DEVICE_CREDENTIALS, value).apply()
        }

    var notesViewGrid: Boolean
        get() = prefs.getBoolean("notes_view_grid", true)
        set(value) { prefs.edit().putBoolean("notes_view_grid", value).apply() }

    var notesSortOrder: String
        get() = prefs.getString("notes_sort_order", "MODIFIED") ?: "MODIFIED"
        set(value) { prefs.edit().putString("notes_sort_order", value).apply() }

    var galleryViewGrid: Boolean
        get() = prefs.getBoolean("gallery_view_grid", true)
        set(value) { prefs.edit().putBoolean("gallery_view_grid", value).apply() }

    var gallerySortOrder: String
        get() = prefs.getString("gallery_sort_order", "CREATED") ?: "CREATED"
        set(value) { prefs.edit().putString("gallery_sort_order", value).apply() }

    var documentsViewGrid: Boolean
        get() = prefs.getBoolean("documents_view_grid", true)
        set(value) { prefs.edit().putBoolean("documents_view_grid", value).apply() }

    var documentsSortOrder: String
        get() = prefs.getString("documents_sort_order", "CREATED") ?: "CREATED"
        set(value) { prefs.edit().putString("documents_sort_order", value).apply() }
}
