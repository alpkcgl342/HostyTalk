package com.hostytalk.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hostytalk.app.HostyTalkApp
import com.hostytalk.app.R
import com.hostytalk.app.data.Conversation
import com.hostytalk.app.data.Languages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
    val loading: Boolean = false,
    val list: List<Conversation> = emptyList(),
    val detail: Conversation? = null,
    val summarizing: Boolean = false,
    val error: String? = null,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val api = (application as HostyTalkApp).api
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun loadList() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { api.conversations() }
            .onSuccess { l -> _state.update { it.copy(loading = false, list = l, error = null) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.code()) } }
    }

    fun loadDetail(id: Int) = viewModelScope.launch {
        if (_state.value.detail?.id != id) _state.update { it.copy(detail = null) }
        _state.update { it.copy(loading = true) }
        runCatching { api.conversation(id) }
            .onSuccess { c -> _state.update { it.copy(loading = false, detail = c, error = null) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.code()) } }
    }

    fun summarize(id: Int) = viewModelScope.launch {
        _state.update { it.copy(summarizing = true) }
        runCatching { api.summarize(id) }
            .onSuccess { c -> _state.update { it.copy(summarizing = false, detail = c, error = null) } }
            .onFailure { e -> _state.update { it.copy(summarizing = false, error = e.code()) } }
    }

    fun delete(id: Int, onDone: () -> Unit) = viewModelScope.launch {
        runCatching { api.deleteConversation(id) }
            .onSuccess {
                _state.update { s -> s.copy(detail = null, list = s.list.filterNot { it.id == id }) }
                onDone()
            }
            .onFailure { e -> _state.update { it.copy(error = e.code()) } }
    }
}

private fun dateText(context: Context, epochSeconds: Long): String =
    DateUtils.formatDateTime(
        context, epochSeconds * 1000,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

/** Özeti ve paylaşım bağlantısını telefonun paylaş menüsüyle gönderir (WhatsApp, e-posta...). */
fun shareSummary(context: Context, title: String, summary: Map<String, String>?, merchantLang: String, url: String?) {
    val text = buildString {
        appendLine(title)
        summary?.entries?.sortedBy { it.key != merchantLang }?.forEach { (code, s) ->
            appendLine()
            appendLine("${Languages.of(code).flag} $s")
        }
        if (url != null) {
            appendLine()
            append(url)
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, vm: HistoryViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadList() }
    BackHandler(onBack = onBack)

    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.history), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { item { Text(errorText(it), color = MaterialTheme.colorScheme.error) } }
            if (state.loading && state.list.isEmpty()) {
                item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } }
            }
            if (!state.loading && state.list.isEmpty() && state.error == null) {
                item { Text(stringResource(R.string.history_empty)) }
            }
            items(state.list, key = { it.id }) { c ->
                val guest = Languages.of(c.guestLang)
                Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${guest.flag} ${guest.nativeName}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(dateText(context, c.startedAt), style = MaterialTheme.typography.bodySmall)
                        }
                        c.agreed?.let { Text("🤝 ${money(it.amount, it.currency)}", fontWeight = FontWeight.Bold) }
                        Text(
                            c.summary?.get(c.merchantLang)
                                ?: stringResource(R.string.messages_count_fmt, c.messageCount ?: 0),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(id: Int, businessName: String, onBack: () -> Unit, vm: HistoryViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(id) { vm.loadDetail(id) }
    BackHandler(onBack = onBack)

    val c = state.detail?.takeIf { it.id == id }
    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.conversation_detail), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { item { Text(errorText(it), color = MaterialTheme.colorScheme.error) } }
            if (c == null) {
                item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } }
                return@LazyColumn
            }
            item {
                val guest = Languages.of(c.guestLang)
                val me = Languages.of(c.merchantLang)
                Text("${me.flag} ${me.nativeName} ⇄ ${guest.flag} ${guest.nativeName}", fontWeight = FontWeight.SemiBold)
                Text(dateText(context, c.startedAt), style = MaterialTheme.typography.bodySmall)
            }
            c.agreed?.let { item { OfferCard(it, mine = it.by == "merchant", onReply = null) } }
            if (c.summary != null) {
                item { SummaryCard(c.summary, c.merchantLang) }
            } else {
                item {
                    OutlinedButton(onClick = { vm.summarize(id) }, enabled = !state.summarizing) {
                        Text(stringResource(if (state.summarizing) R.string.summarizing else R.string.create_summary))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        shareSummary(context, businessName, c.summary, c.merchantLang, c.shareUrl)
                    }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            val offers = c.offers.filter { it != c.agreed }
            if (offers.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.bargaining)) }
                items(offers, key = { "o${it.id}" }) { OfferCard(it, mine = it.by == "merchant", onReply = null) }
            }
            val messages = c.messages.orEmpty()
            if (messages.isNotEmpty()) {
                item { HorizontalDivider(); SectionTitle(stringResource(R.string.transcript)) }
                items(messages, key = { "m${it.id}" }) { m ->
                    Bubble(
                        ChatMessage(m.id, m.from == "guest", m.original, m.translated, m.sourceLang, m.targetLang)
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(id, onBack) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
