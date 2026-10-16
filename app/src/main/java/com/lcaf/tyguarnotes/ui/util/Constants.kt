package com.lcaf.tyguarnotes.ui.util

class Constants {
    companion object {
        const val TAG = "TyguarNotesDebug"
        const val PREFERENCES_HELPER_SETTINGS_NAME = "preferences_helper_settings_name"

        const val PREFERENCES_SETTINGS_USE_BIOMETRIC = "PREFERENCES_SETTINGS_USE_BIOMETRIC"
        const val PREFERENCES_SETTINGS_USE_DEVICE_CREDENTIALS = "PREFERENCES_SETTINGS_USE_DEVICE_CREDENTIALS"

        // Decrypted copies handed to external viewers; wiped on every app start.
        const val SHARED_DOCS_DIR = "shared_docs"
    }
}