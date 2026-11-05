package com.hostytalk.app.ui

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
import com.hostytalk.app.data.BusinessTypes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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
        "session_not_found", "invalid_token" -> R.string.err_session
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

@Composable
fun businessTypeLabel(code: String): String = stringResource(
    when (code) {
        "hotel" -> R.string.bt_hotel
        "restaurant" -> R.string.bt_restaurant
        "shop" -> R.string.bt_shop
        "taxi" -> R.string.bt_taxi
        "guide" -> R.string.bt_guide
        "car_rental" -> R.string.bt_car_rental
        else -> R.string.bt_other
    }
)

/** İşletme türü seçici; hazır cümle seti buna göre gelir. */
@Composable
fun BusinessTypePicker(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) {
            Text("${stringResource(R.string.business_type)}: ${BusinessTypes.emoji(current)} ${businessTypeLabel(current)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BusinessTypes.all.forEach { (code, emoji) ->
                DropdownMenuItem(
                    text = { Text("$emoji  ${businessTypeLabel(code)}") },
                    onClick = { open = false; onPick(code) },
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
