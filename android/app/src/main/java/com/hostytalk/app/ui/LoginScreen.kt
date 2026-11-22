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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hostytalk.app.R

@Composable
fun Logo(modifier: Modifier = Modifier) {
    // Marka notu: "Hosty" öne çıkar, "Talk" daha küçük.
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)) { append("Hosty") }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp)) { append("Talk") }
        },
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun LoginScreen(vm: AuthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var serverDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AppLanguagePicker() }
            Spacer(Modifier.height(24.dp))
            Logo()
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (register) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.display_name)) },
                    supportingText = { Text(stringResource(R.string.display_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.submit(register, email, password, name) },
                enabled = !state.loading && email.isNotBlank() && password.length >= 6 && (!register || name.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.loading) CircularProgressIndicator(Modifier.height(20.dp))
                else Text(stringResource(if (register) R.string.register else R.string.login))
            }
            TextButton(onClick = { register = !register }) {
                Text(stringResource(if (register) R.string.have_account else R.string.no_account))
            }
            TextButton(onClick = { serverDialog = true }) {
                Text("⚙ ${stringResource(R.string.server_url)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (serverDialog) ServerUrlDialog(serverUrl, vm::setServerUrl) { serverDialog = false }
}
