package com.lcaf.tyguarnotes.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.data.sync.SyncScheduler
import com.lcaf.tyguarnotes.ui.components.WarningDialog
import com.lcaf.tyguarnotes.ui.util.AppLockGrace

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity
    val webClientId = stringResource(R.string.google_web_client_id)

    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDriveConsentResult(activity, result.resultCode, result.data)
    }

    LaunchedEffect(uiState.driveConsentRequest) {
        uiState.driveConsentRequest?.let {
            viewModel.onDriveConsentLaunched()
            AppLockGrace.beginExternalTask()
            driveConsentLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkExistingSession()
    }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            SyncScheduler.schedule(activity)
            onLoginSuccess()
        }
    }

    if (uiState.errorMessage != null) {
        WarningDialog(
            message = uiState.errorMessage!!,
            onDismiss = { viewModel.dismissError() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            val vaultStep = uiState.vaultStep
            if (vaultStep != null) {
                VaultForm(
                    step = vaultStep,
                    isLoading = uiState.isLoading,
                    onCreate = viewModel::createVault,
                    onUnlock = viewModel::unlockVault,
                    onCancel = viewModel::cancelVault
                )
            } else {
                ProgressButton(
                    text = stringResource(R.string.sign_in_with_google),
                    isLoading = uiState.isLoading,
                    onClick = { viewModel.signIn(activity, webClientId) }
                )
            }
        }
    }
}

@Composable
private fun VaultForm(
    step: VaultStep,
    isLoading: Boolean,
    onCreate: (password: String, confirmation: String) -> Unit,
    onUnlock: (password: String) -> Unit,
    onCancel: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val creating = step == VaultStep.CREATE
    val submit = { if (creating) onCreate(password, confirmation) else onUnlock(password) }

    Text(
        text = stringResource(if (creating) R.string.vault_create_title else R.string.vault_unlock_title),
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(if (creating) R.string.vault_create_description else R.string.vault_unlock_description),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
    VaultPasswordField(
        value = password,
        onValueChange = { password = it },
        label = stringResource(R.string.vault_password),
        imeAction = if (creating) ImeAction.Next else ImeAction.Done,
        onDone = submit
    )
    if (creating) {
        Spacer(modifier = Modifier.height(12.dp))
        VaultPasswordField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = stringResource(R.string.vault_password_confirm),
            imeAction = ImeAction.Done,
            onDone = submit
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    ProgressButton(
        text = stringResource(if (creating) R.string.vault_create else R.string.vault_unlock),
        isLoading = isLoading,
        onClick = submit
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onCancel, enabled = !isLoading) {
        Text(stringResource(R.string.cancel))
    }
}

@Composable
private fun VaultPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

@Composable
private fun ProgressButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text)
        }
    }
}
