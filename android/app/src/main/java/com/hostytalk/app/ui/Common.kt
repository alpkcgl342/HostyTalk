package com.hostytalk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.hostytalk.app.R
import com.hostytalk.app.data.Languages

@Composable
fun errorText(code: String): String = stringResource(
    when (code) {
        "network" -> R.string.err_network
        "invalid_credentials" -> R.string.err_invalid_credentials
        "email_taken" -> R.string.err_email_taken
        "quota_exceeded" -> R.string.err_quota
        "ai_failed", "summary_failed" -> R.string.err_ai
        "invalid_token" -> R.string.err_session
        "session_not_found" -> R.string.err_code_invalid
        "session_full" -> R.string.err_session_full
        "http_422" -> R.string.err_validation
        else -> R.string.err_generic
    }
)

/** Dil seçici: [current] seçili dil, seçim [onPick] ile bildirilir. */
@Composable
fun LanguagePicker(label: String, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val lang = Languages.of(current)
    Column {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${lang.flag} ${lang.nativeName}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Languages.all.forEach { l ->
                DropdownMenuItem(
                    text = { Text("${l.flag}  ${l.nativeName}") },
                    onClick = { open = false; onPick(l.code) },
                )
            }
        }
    }
}

/** Alt ekranlar için geri düğmeli basit üst çubuk. */
@Composable
fun SimpleTopBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                // Arapça'da (sağdan sola) ok yönü de dönsün.
                val arrow = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "→" else "←"
                Text(arrow, fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun AppLanguagePicker() {
    LanguagePicker(
        label = stringResource(R.string.app_language),
        current = Languages.appLanguage() ?: java.util.Locale.getDefault().language,
        onPick = Languages::setAppLanguage,
    )
}

@Composable
fun ServerUrlDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_url)) },
        text = {
            Column {
                Text(stringResource(R.string.server_url_hint))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Giriş yapılıyken yeni şifre belirleme penceresi (eski şifre sorulmaz). */
@Composable
fun ChangePasswordDialog(
    onSubmit: (password: String, onDone: (error: String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val mismatch = repeat.isNotEmpty() && repeat != password

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Text(stringResource(R.string.password_changed))
                } else {
                    Text(stringResource(R.string.change_password_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.new_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.repeat_password)) },
                        singleLine = true,
                        isError = mismatch,
                        supportingText = { if (mismatch) Text(stringResource(R.string.passwords_dont_match)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (done) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            } else {
                TextButton(
                    onClick = {
                        saving = true
                        error = null
                        onSubmit(password) { e ->
                            saving = false
                            if (e == null) done = true else error = e
                        }
                    },
                    enabled = !saving && password.length >= 6 && password == repeat,
                ) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {
            if (!done) TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Karşı tarafın verdiği 6 haneli kodla konuşmaya katılma penceresi. */
@Composable
fun JoinDialog(onJoin: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_conversation)) },
        text = {
            Column {
                Text(stringResource(R.string.join_dialog_hint))
                OutlinedTextField(
                    value = code,
                    // Boşluk ve küçük harf girilse de kod tanınsın.
                    onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.uppercase().take(6) },
                    singleLine = true,
                    placeholder = { Text("ABC 123") },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code); onDismiss() }, enabled = code.length == 6) {
                Text(stringResource(R.string.join))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
