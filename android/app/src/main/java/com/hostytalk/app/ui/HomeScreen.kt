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
import com.hostytalk.app.data.LiveSession

@Composable
fun HomeScreen(
    onOpen: (LiveSession) -> Unit,
    onLogout: () -> Unit,
    onHistory: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val myLang by vm.myLang.collectAsStateWithLifecycle()
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    var serverDialog by rememberSaveable { mutableStateOf(false) }
    var joinDialog by rememberSaveable { mutableStateOf(false) }
    var passwordDialog by rememberSaveable { mutableStateOf(false) }

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

            state.user?.let { u ->
                Text(stringResource(R.string.welcome_fmt, u.displayName), style = MaterialTheme.typography.titleLarge)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.usage_fmt, u.usedMinutes.toString(), u.quotaMinutes))
                        LinearProgressIndicator(
                            progress = { (u.usedMinutes / u.quotaMinutes).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.i_speak_hint), style = MaterialTheme.typography.bodyMedium)
            LanguagePicker(stringResource(R.string.i_speak), myLang, vm::setMyLang)

            Button(
                onClick = { vm.start(onOpen) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.height(24.dp))
                else Text(stringResource(R.string.new_conversation), fontSize = 20.sp)
            }
            OutlinedButton(
                onClick = { vm.clearError(); joinDialog = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) { Text(stringResource(R.string.join_conversation), fontSize = 18.sp) }
            Text(stringResource(R.string.new_conversation_hint), style = MaterialTheme.typography.bodySmall)

            state.error?.let {
                Text(errorText(it), color = MaterialTheme.colorScheme.error)
                if (it == "network") TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.retry)) }
            }

            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("🕘 ${stringResource(R.string.history)}")
            }

            Spacer(Modifier.height(8.dp))
            AppLanguagePicker()
            TextButton(onClick = { passwordDialog = true }) { Text("🔒 ${stringResource(R.string.change_password)}") }
            TextButton(onClick = { serverDialog = true }) { Text("⚙ ${stringResource(R.string.server_url)}") }
        }
    }

    if (passwordDialog) ChangePasswordDialog(onSubmit = { pw, onDone -> vm.changePassword(pw, onDone) }) { passwordDialog = false }

    if (serverDialog) ServerUrlDialog(serverUrl, vm::setServerUrl) { serverDialog = false }
    if (joinDialog) JoinDialog(onJoin = { vm.join(it, onOpen) }) { joinDialog = false }
}
