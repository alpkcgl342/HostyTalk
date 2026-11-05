package com.hostytalk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hostytalk.app.R
import com.hostytalk.app.data.Connection
import com.hostytalk.app.data.Languages
import com.hostytalk.app.data.NewSession
import com.hostytalk.app.data.Offer
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Currency

@Composable
fun ConversationScreen(session: NewSession, onClose: () -> Unit) {
    val vm: ConversationViewModel = viewModel(key = session.id)
    LaunchedEffect(session.id) { vm.start(session) }

    val state by vm.state.collectAsStateWithLifecycle()
    val micOn by vm.micOn.collectAsStateWithLifecycle()
    val hearing by vm.hearing.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    var showQr by rememberSaveable { mutableStateOf(true) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    // Konuşma boyunca ekran kapanmasın.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Misafir bağlanınca QR küçülsün, kopunca geri gelsin.
    LaunchedEffect(state.guestConnected) { showQr = !state.guestConnected }

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
        topBar = { TopBar(state, showQr, onToggleQr = { showQr = !showQr }, onEnd = { confirmEnd = true }) },
        bottomBar = {
            if (!state.ended) Controls(
                state = state,
                micOn = micOn,
                hearing = hearing,
                level = level,
                playing = playing,
                onMic = ::tapMic,
                onPhrase = vm::sayPhrase,
                onPrice = vm::showPrice,
                onOffer = vm::makeOffer,
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size, state.summary, state.offers) {
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0) listState.animateScrollToItem(count - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showQr && !state.ended) item { QrCard(state) }
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
            state.price?.let { item { PriceCard(it) } }
            items(state.messages, key = { it.id }) { Bubble(it) }
            // Pazarlık kartları: turistin açık teklifi kabul/ret düğmeleriyle gelir.
            items(state.offers, key = { "offer${it.id}" }) { o ->
                OfferCard(o, mine = o.by == "merchant", onReply = if (state.ended) null else { id, ok -> vm.replyOffer(id, ok) })
            }
            state.summary?.let { item { SummaryCard(it, state.merchantLang) } }
            if (state.ended) {
                item {
                    val context = LocalContext.current
                    val businessName = stringResource(R.string.app_name)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.conversation_ended), style = MaterialTheme.typography.titleMedium)
                        if (state.shareUrl != null) {
                            OutlinedButton(
                                onClick = {
                                    shareSummary(context, businessName, state.summary, state.merchantLang, state.shareUrl)
                                },
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
private fun TopBar(state: ConversationState, showQr: Boolean, onToggleQr: () -> Unit, onEnd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val guest = Languages.of(state.guestLang)
                Text(
                    text = when {
                        state.connection == Connection.Reconnecting -> stringResource(R.string.connection_lost)
                        state.guestConnected -> stringResource(R.string.guest_connected_fmt, "${guest.flag} ${guest.nativeName}")
                        else -> stringResource(R.string.guest_waiting)
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                val me = Languages.of(state.merchantLang)
                Text("${me.flag} ${me.nativeName} ⇄ ${guest.flag} ${guest.nativeName}", style = MaterialTheme.typography.bodySmall)
            }
            if (!state.ended) {
                TextButton(onClick = onToggleQr) {
                    Text(stringResource(if (showQr) R.string.hide_qr else R.string.show_qr), color = MaterialTheme.colorScheme.onPrimary)
                }
                OutlinedButton(onClick = onEnd, enabled = !state.ending) {
                    Text(stringResource(R.string.end_conversation), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun QrCard(state: ConversationState) {
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
            Text(stringResource(R.string.scan_qr), style = MaterialTheme.typography.titleMedium)
            if (state.joinUrl.isNotEmpty()) QrCode(state.joinUrl, Modifier.size(240.dp))
            Text(stringResource(R.string.qr_hint), style = MaterialTheme.typography.bodySmall)
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
fun Bubble(m: ChatMessage) {
    // Esnaf her zaman kendi dilindeki metni büyük görür.
    val main = if (m.fromGuest) m.translated else m.original
    val sub = if (m.fromGuest) m.original else m.translated
    val subLang = Languages.of(if (m.fromGuest) m.sourceLang else m.targetLang)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromGuest) Alignment.CenterStart else Alignment.CenterEnd) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (m.fromGuest) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp)
        ) {
            Text(
                stringResource(if (m.fromGuest) R.string.guest else R.string.you),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(main, fontSize = 18.sp)
            Text("${subLang.flag} $sub", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

fun money(amount: Double, code: String): String = runCatching {
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code); maximumFractionDigits = 2 }.format(amount)
}.getOrDefault("$amount $code")

@Composable
private fun PriceCard(p: Price) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.price), style = MaterialTheme.typography.labelMedium)
            Text(money(p.amount, p.currency), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                p.conversions.filterKeys { it != p.currency }.entries.joinToString("   ") { "≈ ${money(it.value, it.key)}" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Pazarlık teklifi. [onReply] verilirse ve teklif karşı taraftansa Kabul/Ret düğmeleri çıkar. */
@Composable
fun OfferCard(o: Offer, mine: Boolean, onReply: ((Int, Boolean) -> Unit)?) {
    val (statusText, color) = when (o.status) {
        "accepted" -> stringResource(R.string.offer_accepted) to Color(0xFF2E8B57)
        "rejected" -> stringResource(R.string.offer_rejected) to MaterialTheme.colorScheme.error
        "countered" -> stringResource(R.string.offer_countered) to MaterialTheme.colorScheme.outline
        else -> (if (mine) stringResource(R.string.offer_waiting) else "") to MaterialTheme.colorScheme.secondary
    }
    Card(
        Modifier.fillMaxWidth().alpha(if (o.status == "countered" || o.status == "rejected") 0.6f else 1f),
        border = BorderStroke(2.dp, color),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (mine) R.string.offer_mine else R.string.offer_guest),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(money(o.amount, o.currency), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                o.conversions.filterKeys { it != o.currency }.entries.joinToString("   ") { "≈ ${money(it.value, it.key)}" },
                style = MaterialTheme.typography.bodySmall,
            )
            if (statusText.isNotEmpty()) Text(statusText, color = color, fontWeight = FontWeight.SemiBold)
            if (onReply != null && !mine && o.status == "open") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(
                        onClick = { onReply(o.id, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E8B57)),
                    ) { Text(stringResource(R.string.accept)) }
                    OutlinedButton(onClick = { onReply(o.id, false) }) { Text(stringResource(R.string.reject)) }
                }
                Text(stringResource(R.string.counter_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SummaryCard(texts: Map<String, String>, merchantLang: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleMedium)
            // Önce esnafın dili, sonra turistin dili.
            texts.entries.sortedBy { it.key != merchantLang }.forEachIndexed { i, (code, text) ->
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
    onPhrase: (String) -> Unit,
    onPrice: (Double, String) -> Unit,
    onOffer: (Double, String) -> Unit,
) {
    var priceOpen by rememberSaveable { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = { priceOpen = !priceOpen },
                        label = { Text("🤝 ${stringResource(R.string.price_and_offer)}") },
                    )
                }
                items(state.phrases) { phrase ->
                    AssistChip(onClick = { onPhrase(phrase) }, label = { Text(phrase) })
                }
            }
            if (priceOpen) {
                PriceInput(
                    onShow = { a, c -> onPrice(a, c); priceOpen = false },
                    onOffer = { a, c -> onOffer(a, c); priceOpen = false },
                )
            }

            val waiting = state.processingFrom != null || playing || state.ending
            Text(
                when {
                    state.ending -> stringResource(R.string.ending)
                    hearing -> stringResource(R.string.hearing)
                    micOn -> stringResource(R.string.mic_on)
                    state.processingFrom != null -> stringResource(R.string.translating)
                    playing -> stringResource(R.string.playing)
                    state.noSpeech -> stringResource(R.string.no_speech)
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

@Composable
private fun PriceInput(onShow: (Double, String) -> Unit, onOffer: (Double, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("TRY") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("TRY" to "₺", "EUR" to "€", "USD" to "$", "GBP" to "£").forEach { (code, symbol) ->
                FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text("$symbol $code") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } },
                label = { Text(stringResource(R.string.price_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        // "Göster" yalnızca bilgi verir; "Teklif ver" pazarlık başlatır, turist kabul edebilir ya da karşı teklif verir.
        val value = amount.replace(',', '.').toDoubleOrNull()
        val valid = value != null && value > 0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { value?.let { onShow(it, currency) } }, enabled = valid) {
                Text(stringResource(R.string.show_price))
            }
            Button(onClick = { value?.let { onOffer(it, currency) } }, enabled = valid) {
                Text(stringResource(R.string.make_offer))
            }
        }
    }
}
