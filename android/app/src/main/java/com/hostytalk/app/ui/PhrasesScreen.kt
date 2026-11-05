package com.hostytalk.app.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hostytalk.app.HostyTalkApp
import com.hostytalk.app.R
import com.hostytalk.app.data.BusinessTypes
import com.hostytalk.app.data.CustomPhrase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhrasesState(
    val loading: Boolean = true,
    val businessType: String = "other",
    val lang: String = "tr",
    val custom: List<CustomPhrase> = emptyList(),
    val defaults: List<String> = emptyList(),
    val error: String? = null,
)

class PhrasesViewModel(application: Application) : AndroidViewModel(application) {
    private val api = (application as HostyTalkApp).api
    private val prefs = (application as HostyTalkApp).prefs

    private val _state = MutableStateFlow(PhrasesState())
    val state: StateFlow<PhrasesState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val lang = prefs.merchantLang.first()
        _state.update { it.copy(loading = true, lang = lang) }
        runCatching { api.phrases(lang) }
            .onSuccess { p ->
                _state.update {
                    it.copy(loading = false, businessType = p.businessType, custom = p.custom, defaults = p.defaults, error = null)
                }
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.code()) } }
    }

    fun add(text: String) = viewModelScope.launch {
        runCatching { api.addPhrase(text.trim(), _state.value.lang) }
            .onSuccess { p -> _state.update { it.copy(custom = it.custom + p, error = null) } }
            .onFailure { e -> _state.update { it.copy(error = e.code()) } }
    }

    fun delete(id: Int) = viewModelScope.launch {
        runCatching { api.deletePhrase(id) }
            .onSuccess { _state.update { s -> s.copy(custom = s.custom.filterNot { it.id == id }) } }
            .onFailure { e -> _state.update { it.copy(error = e.code()) } }
    }
}

@Composable
fun PhrasesScreen(onBack: () -> Unit, vm: PhrasesViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)

    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.my_phrases), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.phrases_hint), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(300) },
                        label = { Text(stringResource(R.string.new_phrase)) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { vm.add(draft); draft = "" }, enabled = draft.isNotBlank()) {
                        Text(stringResource(R.string.add))
                    }
                }
            }
            state.error?.let { item { Text(errorText(it), color = MaterialTheme.colorScheme.error) } }

            item { SectionTitle(stringResource(R.string.my_custom_phrases)) }
            if (state.custom.isEmpty() && !state.loading) {
                item { Text(stringResource(R.string.no_custom_phrases), style = MaterialTheme.typography.bodySmall) }
            }
            items(state.custom, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.text, Modifier.weight(1f))
                        TextButton(onClick = { vm.delete(p.id) }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    "${BusinessTypes.emoji(state.businessType)} " +
                        stringResource(R.string.default_phrases_fmt, businessTypeLabel(state.businessType))
                )
            }
            items(state.defaults) { text ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) { Text(text, Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}
