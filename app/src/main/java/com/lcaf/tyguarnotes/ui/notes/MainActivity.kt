package com.lcaf.tyguarnotes.ui.notes

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.data.repository.SettingsRepository
import com.lcaf.tyguarnotes.ui.components.LocalAppLocked
import com.lcaf.tyguarnotes.ui.navigation.AppNavigation
import com.lcaf.tyguarnotes.ui.theme.TyguarNotesTheme
import com.lcaf.tyguarnotes.ui.util.AppLockGrace
import java.io.File
import com.lcaf.tyguarnotes.ui.util.Constants

class MainActivity : FragmentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: PromptInfo
    private var isAuthenticated by mutableStateOf(false)
    private var biometricFailed by mutableStateOf(false)
    private var isAuthenticating = false

    private var sharedText: String? = null
    private var sharedImageBytes: ByteArray? by mutableStateOf(null)
    private var sharedImageUri: Uri? by mutableStateOf(null)
    private var sharedDocBytes: ByteArray? by mutableStateOf(null)
    private var sharedDocName: String? by mutableStateOf(null)
    private var sharedDocMimeType: String? by mutableStateOf(null)

    private fun isLockEnabled(): Boolean {
        return settingsRepository.useBiometric || settingsRepository.useDeviceCredentials
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds allow screenshots (to capture the app); release builds always block them.
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        settingsRepository = SettingsRepository(this)
        Repositories.init(applicationContext)
        File(cacheDir, Constants.SHARED_DOCS_DIR).deleteRecursively()
        handleIncomingIntent(intent)
        initBiometric()

        setContent {
            TyguarNotesTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val locked = isLockEnabled() && !isAuthenticated
                    // Stays composed so navigation state survives, but is neither drawn nor
                    // exposed to accessibility services while locked; dialogs hide themselves.
                    Box(
                        modifier = if (locked) {
                            Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics { }
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        CompositionLocalProvider(LocalAppLocked provides locked) {
                            AppNavigation(
                                settingsRepository = settingsRepository,
                                sharedText = sharedText,
                                sharedImageBytes = sharedImageBytes,
                                sharedImageUri = sharedImageUri,
                                onSharedImageConsumed = {
                                    sharedImageBytes = null
                                    sharedImageUri = null
                                },
                                sharedDocBytes = sharedDocBytes,
                                sharedDocName = sharedDocName,
                                sharedDocMimeType = sharedDocMimeType,
                                onSharedDocConsumed = {
                                    sharedDocBytes = null
                                    sharedDocName = null
                                    sharedDocMimeType = null
                                }
                            )
                        }
                    }

                    if (locked) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.app_locked),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.biometric_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = {
                                checkBiometric()
                            }) {
                                Text(stringResource(R.string.try_again))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            when {
                intent.type == "text/plain" -> {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                intent.type?.startsWith("image/") == true -> {
                    @Suppress("DEPRECATION")
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    uri?.let {
                        val inputStream = contentResolver.openInputStream(it)
                        sharedImageBytes = inputStream?.readBytes()
                        sharedImageUri = it
                        inputStream?.close()
                    }
                }
                intent.type?.startsWith("application/") == true -> {
                    @Suppress("DEPRECATION")
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    uri?.let {
                        val inputStream = contentResolver.openInputStream(it)
                        sharedDocBytes = inputStream?.readBytes()
                        inputStream?.close()
                        sharedDocMimeType = intent.type
                        sharedDocName = getFileNameFromUri(it)
                    }
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index)
                }
            }
        }
        return name ?: "DOC_${System.currentTimeMillis()}"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun initBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticated = true
                    biometricFailed = false
                    isAuthenticating = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricFailed = true
                    isAuthenticating = false
                }

                override fun onAuthenticationFailed() {
                    biometricFailed = true
                    isAuthenticating = false
                }
            })
    }

    private fun checkBiometric() {
        if (!isLockEnabled()) {
            isAuthenticated = true
            return
        }

        if (isAuthenticating) return

        isAuthenticated = false
        biometricFailed = false
        isAuthenticating = true

        val biometricManager = BiometricManager.from(this)
        val hasBiometric = settingsRepository.useBiometric &&
            biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val hasDeviceCredential = settingsRepository.useDeviceCredentials

        val builder = PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle(getString(R.string.biometric_desc))

        when {
            hasBiometric && hasDeviceCredential -> {
                builder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            }
            hasBiometric -> {
                builder.setAllowedAuthenticators(BIOMETRIC_STRONG)
                builder.setNegativeButtonText(getString(R.string.cancel))
            }
            hasDeviceCredential -> {
                builder.setAllowedAuthenticators(DEVICE_CREDENTIAL)
            }
            else -> {
                isAuthenticated = true
                isAuthenticating = false
                return
            }
        }

        promptInfo = builder.build()
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onStart() {
        super.onStart()
        if (isLockEnabled()) {
            // Back from a picker or confirmation the app opened: stay unlocked unless it took too long.
            if (!AppLockGrace.endExternalTask() && !isAuthenticating) isAuthenticated = false
            if (!isAuthenticated) {
                window.decorView.postDelayed({
                    // The device-credential screen restarts this activity; its success callback may land first.
                    if (!isAuthenticated) checkBiometric()
                }, 300)
            }
        } else {
            isAuthenticated = true
        }
    }

    // Lock while the window is still visible: the last frame drawn before going to the
    // background is the lock screen, so returning never flashes the content behind it.
    override fun onPause() {
        super.onPause()
        lockUnlessAuthenticating()
    }

    override fun onStop() {
        super.onStop()
        lockUnlessAuthenticating()
    }

    // The prompt itself (device credential screen) and screens the app opens for a task
    // (pickers, confirmations) pause this activity; those must not relock it.
    private fun lockUnlessAuthenticating() {
        if (isLockEnabled() && !isAuthenticating && !AppLockGrace.isExternalTaskActive) {
            isAuthenticated = false
        }
    }
}
