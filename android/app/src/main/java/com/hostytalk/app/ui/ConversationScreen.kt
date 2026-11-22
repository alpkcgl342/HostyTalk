package com.hostytalk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hostytalk.app.R
import com.hostytalk.app.data.Connection
import com.hostytalk.app.data.Languages
import com.hostytalk.app.data.LiveSession
import kotlinx.coroutines.delay

@Composable
fun ConversationScreen(session: LiveSession, onClose: () -> Unit) {
    val vm: ConversationViewModel = viewModel(key = session.id)
    LaunchedEffect(session.id) { vm.start(session) }

    val state by vm.state.collectAsStateWithLifecycle()
    val micOn by vm.micOn.collectAsStateWithLifecycle()
    val hearing by vm.hearing.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    var showCode by rememberSaveable { mutableStateOf(session.isHost) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    // Konuşma boyunca ekran kapanmasın.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Karşı taraf bağlanınca kod kartı küçülsün, kopunca (başlatan için) geri gelsin.
    LaunchedEffect(state.otherConnected) { showCode = state.isHost && !state.otherConnected }

    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.tapMic()
    }
    fun tapMic() {
        if (micOn || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) vm.tapMic()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler(enabled = !state.ended) { confirmEnd = true }

    Scaffold(
        topBar = {
            TopBar(state, showCode, onToggleCode = { showCode = !showCode }, onEnd = { confirmEnd = true })
        },
        bottomBar = {
            if (!state.ended) Controls(
                state = state,
                micOn = micOn,
                hearing = hearing,
                level = level,
                playing = playing,
                onMic = ::tapMic,
                onSend = vm::sendText,
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size, state.summary) {
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0) listState.animateScrollToItem(count - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showCode && state.isHost && !state.ended) item { JoinCodeCard(state) }
            // İki taraf aynı dili seçtiyse çevrilecek bir şey yok: kullanıcıyı uyar, dilini değiştirebilsin.
            if (state.otherConnected && state.myLang == state.otherLang && !state.ended) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.same_lang_warning))
                            LanguagePicker(stringResource(R.string.i_speak), state.myLang, vm::setMyLang)
                        }
                    }
                }
            }
            state.error?.let { code ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(errorText(code), Modifier.weight(1f))
                            TextButton(onClick = vm::dismissError) { Text("✕") }
                        }
                    }
                }
            }
            items(state.messages, key = { it.id }) { Bubble(it, state.otherName) }
            state.summary?.let { item { SummaryCard(it, state.myLang) } }
            if (state.ended) {
                item {
                    val title = stringResource(R.string.summary_share_title, state.otherName)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(if (state.endedByOther) R.string.ended_by_other else R.string.conversation_ended),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        if (state.summary != null) {
                            OutlinedButton(
                                onClick = { shareSummary(context, title, state.summary, state.myLang) },
                                modifier = Modifier.padding(top = 12.dp),
                            ) { Text(stringResource(R.string.share_summary)) }
                        }
                        Button(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.back_home))
                        }
                    }
                }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.end_title)) },
            text = { Text(stringResource(R.string.end_message)) },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; vm.end(withSummary = true) }) {
                    Text(stringResource(R.string.end_with_summary))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false; vm.end(withSummary = false) }) {
                    Text(stringResource(R.string.end_without_summary))
                }
            },
        )
    }
}

@Composable
private fun TopBar(state: ConversationState, showCode: Boolean, onToggleCode: () -> Unit, onEnd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val other = Languages.of(state.otherLang)
                Text(
                    text = when {
                        state.connection == Connection.Reconnecting -> stringResource(R.string.connection_lost)
                        state.otherConnected -> stringResource(R.string.other_connected_fmt, state.otherName)
                        state.otherName.isNotEmpty() -> stringResource(R.string.other_away_fmt, state.otherName)
                        else -> stringResource(R.string.waiting_other)
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                val me = Languages.of(state.myLang)
                Text("${me.flag} ${me.nativeName} ⇄ ${other.flag} ${other.nativeName}", style = MaterialTheme.typography.bodySmall)
            }
            if (!state.ended) {
                if (state.isHost) {
                    TextButton(onClick = onToggleCode) {
                        Text(
                            stringResource(if (showCode) R.string.hide_code else R.string.show_code),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                OutlinedButton(onClick = onEnd, enabled = !state.ending) {
                    Text(stringResource(R.string.end_conversation), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** Başlatan kişinin ekranı: karşı taraf bu kodu uygulamaya girer ya da QR'ı okutur. */
@Composable
private fun JoinCodeCard(state: ConversationState) {
    val remaining by produceState(initialValue = 0L, state.joinExpiresAt) {
        while (true) {
            value = (state.joinExpiresAt - System.currentTimeMillis() / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.join_code_title), style = MaterialTheme.typography.titleMedium)
            Text(
                state.code.chunked(3).joinToString(" "),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.join_code_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (state.joinUrl.isNotEmpty()) QrCode(state.joinUrl, Modifier.size(200.dp))
            Text(stringResource(R.string.qr_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text(
                if (remaining > 0) stringResource(R.string.qr_expires_fmt, remaining / 60, remaining % 60)
                else stringResource(R.string.qr_expired),
                style = MaterialTheme.typography.bodySmall,
                color = if (remaining > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun Bubble(m: ChatMessage, otherName: String) {
    // Herkes kendi dilindeki metni büyük görür.
    val main = if (m.mine) m.original else m.translated
    val sub = if (m.mine) m.translated else m.original
    val subLang = Languages.of(if (m.mine) m.targetLang else m.sourceLang)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (m.mine) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp)
        ) {
            Text(
                if (m.mine) stringResource(R.string.you) else otherName.ifEmpty { stringResource(R.string.other_person) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(main, fontSize = 18.sp)
            Text("${subLang.flag} $sub", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun SummaryCard(texts: Map<String, String>, myLang: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleMedium)
            // Önce kendi dilim, sonra karşı tarafın dili.
            texts.entries.sortedBy { it.key != myLang }.forEachIndexed { i, (code, text) ->
                if (i > 0) HorizontalDivider()
                Text("${Languages.of(code).flag} $text")
            }
        }
    }
}

@Composable
private fun Controls(
    state: ConversationState,
    micOn: Boolean,
    hearing: Boolean,
    level: Float,
    playing: Boolean,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    fun send() {
        onSend(draft)
        draft = ""
    }
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val waiting = state.processing != null || playing || state.ending
            Text(
                when {
                    state.ending -> stringResource(R.string.ending)
                    hearing -> stringResource(R.string.hearing)
                    micOn -> stringResource(R.string.mic_on)
                    state.processing != null -> stringResource(R.string.translating)
                    playing -> stringResource(R.string.playing)
                    state.noSpeech -> stringResource(R.string.no_speech)
                    !state.otherConnected -> stringResource(R.string.waiting_other)
                    else -> stringResource(R.string.mic_off)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            MicButton(
                micOn = micOn,
                hearing = hearing,
                level = level,
                enabled = micOn || !waiting,
                onClick = onMic,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            // Sessiz ortamlar (sınıf, toplantı) için yazarak da gönderilebilir.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = ::send, enabled = draft.isNotBlank() && !state.ending) {
                    Text(stringResource(R.string.send))
                }
            }
        }
    }
}

/** Sesli asistan tarzı mikrofon: açıkken sesin şiddetine göre etrafında halka büyür. */
@Composable
private fun MicButton(
    micOn: Boolean,
    hearing: Boolean,
    level: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring by animateFloatAsState(if (micOn) 0.15f + level * 0.85f else 0f, label = "ring")
    val color = when {
        !micOn -> MaterialTheme.colorScheme.primary
        hearing -> MaterialTheme.colorScheme.secondary
        else -> Color(0xFFC0392B)
    }
    Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(76.dp + 36.dp * ring)
                .background(color.copy(alpha = 0.25f), CircleShape)
        )
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(76.dp),
        ) { Text(if (micOn) "■" else "🎤", fontSize = 30.sp, color = Color.White) }
    }
}
