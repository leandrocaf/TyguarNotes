package com.lcaf.tyguarnotes.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

class Util {
    companion object {
        fun isEmailValid(email: String): Boolean {
            val emailRegex = Regex("^\\w+([.-]?\\w+)*@\\w+([.-]?\\w+)*(\\.\\w{2,})+\$")
            return emailRegex.matches(email)
        }

        fun openIncognito(context: Context, url: String) {
            val fullUrl = if (url.startsWith("http")) url else "https://$url"
            val uri = Uri.parse(fullUrl)
            
            // Try different intents for incognito mode in various browsers
            val browsers = listOf(
                "com.android.chrome" to "com.google.android.apps.chrome.EXTRA_INCOGNITO",
                "org.mozilla.firefox" to "private_browsing_mode",
                "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser.EXTRA_INCOGNITO",
                "com.brave.browser" to "com.brave.browser.incognito"
            )

            for ((pkg, extra) in browsers) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setPackage(pkg)
                    intent.putExtra(extra, true)
                    intent.putExtra("org.chromium.chrome.browser.incognito.mode", true)
                    
                    // Removendo FLAG_ACTIVITY_NEW_TASK para manter na mesma pilha de navegação se possível
                    // e permitir que o botão 'voltar' do sistema funcione melhor
                    
                    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        context.startActivity(intent)
                        return
                    }
                } catch (_: Exception) { }
            }

            // Fallback to Custom Tabs with Ephemeral flag (modern Chrome)
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true) // Garante que a barra de título apareça com o botão de fechar
                    .build()

                customTabsIntent.intent.putExtra("androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING", true)
                customTabsIntent.launchUrl(context, uri)
            } catch (e: Exception) {
                // Final fallback: normal browser
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            }
        }
    }
}
