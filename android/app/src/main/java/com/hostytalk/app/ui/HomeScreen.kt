package com.hostytalk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hostytalk.app.R
import com.hostytalk.app.data.NewSession

@Composable
fun HomeScreen(
    onStarted: (NewSession) -> Unit,
    onLogout: () -> Unit,
    onPhrases: () -> Unit,
    onHistory: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val merchantLang by vm.merchantLang.collectAsStateWithLifecycle()
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    var serverDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Logo(Modifier.weight(1f))
                TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
            }

            state.merchant?.let { m ->
                Text(stringResource(R.string.welcome_fmt, m.businessName), style = MaterialTheme.typography.titleLarge)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.usage_fmt, m.usedMinutes.toString(), m.quotaMinutes))
                        LinearProgressIndicator(
                            progress = { (m.usedMinutes / m.quotaMinutes).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.i_speak_hint), style = MaterialTheme.typography.bodyMedium)
            LanguagePicker(stringResource(R.string.i_speak), merchantLang, vm::setMerchantLang)

            Button(
                onClick = { vm.start(onStarted) },
                enabled = !state.starting,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                if (state.starting) CircularProgressIndicator(Modifier.height(24.dp))
                else Text(stringResource(R.string.new_conversation), fontSize = 20.sp)
            }
            Text(stringResource(R.string.new_conversation_hint), style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("🕘 ${stringResource(R.string.history)}")
                }
                OutlinedButton(onClick = onPhrases, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("💬 ${stringResource(R.string.my_phrases)}")
                }
            }
            state.merchant?.let { m -> BusinessTypePicker(m.businessType, vm::setBusinessType) }

            state.error?.let {
                Text(errorText(it), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.retry)) }
            }

            Spacer(Modifier.height(16.dp))
            AppLanguagePicker()
            TextButton(onClick = { serverDialog = true }) { Text("⚙ ${stringResource(R.string.server_url)}") }
        }
    }

    if (serverDialog) ServerUrlDialog(serverUrl, vm::setServerUrl) { serverDialog = false }
}
