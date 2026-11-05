// HostyTalk turist sayfası: hesapsız, QR ile katılım.
// Mikrofon sesli asistan gibi çalışır: dokun → konuş → susunca kendiliğinden kapanır ve gönderilir.
(() => {
  "use strict";

  const LANGS = { tr: "Türkçe", en: "English", ar: "العربية", fr: "Français", ru: "Русский" };

  const T = {
    en: { welcome: "Speak in your language, the host will hear it in theirs.", start: "Start conversation",
      startHint: "Allow microphone access when asked.", send: "Send", typeHere: "Or type a message…",
      tapToSpeak: "Tap the microphone and speak.", listening: "Listening… speak now.", hearing: "Hearing you…",
      noSpeech: "I didn't hear anything. Tap to try again.", translating: "Translating…",
      playing: "Playing translation…", sameLang: "Choose your language at the top right.",
      expired: "This conversation link has expired. Please ask for a new QR code.",
      ended: "The conversation has ended. Thank you!", reconnecting: "Connection lost, reconnecting…",
      noMic: "Microphone is not available here. You can still type.", summary: "What we agreed on",
      price: "Price", error: "Something went wrong, please try again." },
    tr: { welcome: "Kendi dilinizde konuşun, karşı taraf kendi dilinde duysun.", start: "Konuşmayı başlat",
      startHint: "İstendiğinde mikrofon izni verin.", send: "Gönder", typeHere: "Ya da mesaj yazın…",
      tapToSpeak: "Mikrofona dokunun ve konuşun.", listening: "Dinliyorum… konuşabilirsiniz.", hearing: "Sizi duyuyorum…",
      noSpeech: "Bir şey duyamadım. Tekrar denemek için dokunun.", translating: "Çevriliyor…",
      playing: "Çeviri seslendiriliyor…", sameLang: "Sağ üstten kendi dilinizi seçin.",
      expired: "Bu konuşma bağlantısının süresi doldu. Lütfen yeni bir QR kod isteyin.",
      ended: "Konuşma sona erdi. Teşekkürler!", reconnecting: "Bağlantı koptu, yeniden bağlanılıyor…",
      noMic: "Burada mikrofon kullanılamıyor. Yine de yazabilirsiniz.", summary: "Neyde anlaştık",
      price: "Fiyat", error: "Bir sorun oluştu, lütfen tekrar deneyin." },
    ar: { welcome: "تحدّث بلغتك، وسيسمعك المضيف بلغته.", start: "ابدأ المحادثة",
      startHint: "اسمح بالوصول إلى الميكروفون عند الطلب.", send: "إرسال", typeHere: "أو اكتب رسالة…",
      tapToSpeak: "اضغط على الميكروفون وتحدّث.", listening: "أستمع… تحدّث الآن.", hearing: "أسمعك…",
      noSpeech: "لم أسمع شيئاً. اضغط للمحاولة مرة أخرى.", translating: "جارٍ الترجمة…",
      playing: "تشغيل الترجمة…", sameLang: "اختر لغتك من أعلى الصفحة.",
      expired: "انتهت صلاحية رابط المحادثة. يرجى طلب رمز QR جديد.",
      ended: "انتهت المحادثة. شكرًا لك!", reconnecting: "انقطع الاتصال، جارٍ إعادة الاتصال…",
      noMic: "الميكروفون غير متاح هنا. لا يزال بإمكانك الكتابة.", summary: "ما اتفقنا عليه",
      price: "السعر", error: "حدث خطأ ما، يرجى المحاولة مرة أخرى." },
    fr: { welcome: "Parlez dans votre langue, votre hôte vous entendra dans la sienne.", start: "Commencer la conversation",
      startHint: "Autorisez l'accès au micro lorsqu'il est demandé.", send: "Envoyer", typeHere: "Ou écrivez un message…",
      tapToSpeak: "Touchez le micro et parlez.", listening: "À l'écoute… parlez maintenant.", hearing: "Je vous entends…",
      noSpeech: "Je n'ai rien entendu. Touchez pour réessayer.", translating: "Traduction…",
      playing: "Lecture de la traduction…", sameLang: "Choisissez votre langue en haut à droite.",
      expired: "Ce lien de conversation a expiré. Demandez un nouveau QR code.",
      ended: "La conversation est terminée. Merci !", reconnecting: "Connexion perdue, reconnexion…",
      noMic: "Le micro n'est pas disponible ici. Vous pouvez toujours écrire.", summary: "Ce que nous avons convenu",
      price: "Prix", error: "Un problème est survenu, veuillez réessayer." },
    ru: { welcome: "Говорите на своём языке — хозяин услышит вас на своём.", start: "Начать разговор",
      startHint: "Разрешите доступ к микрофону, когда появится запрос.", send: "Отправить", typeHere: "Или напишите сообщение…",
      tapToSpeak: "Нажмите на микрофон и говорите.", listening: "Слушаю… говорите.", hearing: "Слышу вас…",
      noSpeech: "Ничего не слышно. Нажмите, чтобы попробовать снова.", translating: "Перевожу…",
      playing: "Воспроизвожу перевод…", sameLang: "Выберите свой язык вверху справа.",
      expired: "Срок действия ссылки истёк. Попросите новый QR-код.",
      ended: "Разговор завершён. Спасибо!", reconnecting: "Связь потеряна, переподключение…",
      noMic: "Микрофон здесь недоступен. Вы можете писать.", summary: "О чём мы договорились",
      price: "Цена", error: "Что-то пошло не так, попробуйте ещё раз." },
  };

  // Pazarlık ve özet kaydı metinleri
  const T_OFFERS = {
    en: { hostOffer: "Host's offer", myOffer: "Your offer", accept: "Accept", counter: "Counter-offer",
      yourOffer: "Your counter-offer", sendOffer: "Send", waitingReply: "Waiting for the host's answer…",
      st_accepted: "Agreed ✓", st_rejected: "Declined", st_countered: "Countered", saveSummary: "Save summary" },
    tr: { hostOffer: "İşletmenin teklifi", myOffer: "Sizin teklifiniz", accept: "Kabul et", counter: "Karşı teklif",
      yourOffer: "Karşı teklifiniz", sendOffer: "Gönder", waitingReply: "İşletmenin yanıtı bekleniyor…",
      st_accepted: "Anlaşıldı ✓", st_rejected: "Reddedildi", st_countered: "Karşı teklif verildi", saveSummary: "Özeti kaydet" },
    ar: { hostOffer: "عرض المضيف", myOffer: "عرضك", accept: "قبول", counter: "عرض مضاد",
      yourOffer: "عرضك المضاد", sendOffer: "إرسال", waitingReply: "في انتظار رد المضيف…",
      st_accepted: "تم الاتفاق ✓", st_rejected: "مرفوض", st_countered: "تم تقديم عرض مضاد", saveSummary: "حفظ الملخص" },
    fr: { hostOffer: "Offre de l'hôte", myOffer: "Votre offre", accept: "Accepter", counter: "Contre-offre",
      yourOffer: "Votre contre-offre", sendOffer: "Envoyer", waitingReply: "En attente de la réponse de l'hôte…",
      st_accepted: "Accord conclu ✓", st_rejected: "Refusée", st_countered: "Contre-offre faite", saveSummary: "Enregistrer le résumé" },
    ru: { hostOffer: "Предложение хозяина", myOffer: "Ваше предложение", accept: "Принять", counter: "Встречное предложение",
      yourOffer: "Ваше встречное предложение", sendOffer: "Отправить", waitingReply: "Ждём ответа хозяина…",
      st_accepted: "Договорились ✓", st_rejected: "Отклонено", st_countered: "Сделано встречное", saveSummary: "Сохранить резюме" },
  };
  for (const [l, strings] of Object.entries(T_OFFERS)) Object.assign(T[l], strings);

  const CURRENCY_SYMBOL = { TRY: "₺", EUR: "€", USD: "$", GBP: "£" };

  const $ = (id) => document.getElementById(id);
  const sessionId = location.pathname.split("/").filter(Boolean).pop();
  const joinKey = new URLSearchParams(location.search).get("k") || "";

  let lang = detectLang();
  let merchantLang = null;
  let ws = null;
  let ended = false;
  let busy = false;
  let notice = ""; // geçici durum mesajı (ör. "bir şey duyamadım")

  function detectLang() {
    const saved = safeGet("hostytalk.lang");
    if (saved && LANGS[saved]) return saved;
    for (const l of navigator.languages || [navigator.language || "en"]) {
      const code = l.toLowerCase().split("-")[0];
      if (LANGS[code]) return code;
    }
    return "en";
  }
  function safeGet(k) { try { return localStorage.getItem(k); } catch { return null; } }
  function safeSet(k, v) { try { localStorage.setItem(k, v); } catch { /* yoksay */ } }
  const t = (key) => (T[lang] && T[lang][key]) || T.en[key];

  function applyLang() {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    document.querySelectorAll("[data-i18n]").forEach((el) => (el.textContent = t(el.dataset.i18n)));
    $("textInput").placeholder = t("typeHere");
    $("lang").value = lang;
    renderOffers();
    refreshStatus();
  }

  function setupLangSelect() {
    const sel = $("lang");
    for (const [code, name] of Object.entries(LANGS)) sel.add(new Option(name, code));
    sel.addEventListener("change", () => {
      lang = sel.value;
      safeSet("hostytalk.lang", lang);
      applyLang();
      send({ type: "set_lang", lang });
    });
  }

  // --- bağlantı -------------------------------------------------------------

  async function checkSession() {
    const r = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/public?k=${encodeURIComponent(joinKey)}`);
    if (!r.ok) return null;
    return r.json();
  }

  function connect() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const q = new URLSearchParams({ role: "guest", k: joinKey, lang });
    ws = new WebSocket(`${proto}://${location.host}/ws/${encodeURIComponent(sessionId)}?${q}`);
    ws.binaryType = "arraybuffer";
    ws.onmessage = (e) => handle(JSON.parse(e.data));
    ws.onclose = (e) => {
      if (ended) return;
      if (e.code >= 4400 && e.code < 4500) return showClosed(t("expired"));
      setStatus(t("reconnecting"));
      setTimeout(connect, 2000);
    };
    ws.onopen = () => refreshStatus();
  }

  function send(obj) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return false;
    ws.send(obj instanceof ArrayBuffer ? obj : JSON.stringify(obj));
    return true;
  }

  function handle(m) {
    switch (m.type) {
      case "state":
        merchantLang = m.merchant_lang;
        if (m.guest_lang && m.guest_lang !== lang && LANGS[m.guest_lang]) {
          lang = m.guest_lang; // sunucu konuşmadan farklı bir dil algıladı
          applyLang();
        }
        refreshStatus();
        break;
      case "processing":
        busy = true; refreshStatus(); break;
      case "idle":
        busy = false; refreshStatus(); break;
      case "message":
        busy = false;
        addBubble(m);
        if (m.audio_b64) play(m.audio_b64); else refreshStatus();
        break;
      case "price": showPrice(m); break;
      case "offer": upsertOffer(m); break;
      case "summary": showSummary(m.texts); break;
      case "error":
        busy = false; notice = t("error"); refreshStatus(); break;
      case "ended":
        ended = true; stopTurn(false); showClosed(t("ended"), true); showSaveLink(m.share_url); break;
    }
  }

  // --- arayüz ----------------------------------------------------------------

  function addBubble(m) {
    const mine = m.from === "guest";
    const li = document.createElement("li");
    li.className = `bubble ${mine ? "mine" : "theirs"}`;
    const main = document.createElement("div");
    main.className = "main";
    // Turist her zaman kendi dilindeki metni büyük görür.
    main.textContent = mine ? m.original : m.translated;
    main.dir = "auto";
    const sub = document.createElement("div");
    sub.className = "sub";
    sub.textContent = mine ? m.translated : m.original;
    sub.dir = "auto";
    li.append(main, sub);
    $("transcript").append(li);
    li.scrollIntoView({ behavior: "smooth", block: "end" });
  }

  function money(v, c) {
    return `${Number(v).toLocaleString(lang, { maximumFractionDigits: 2 })} ${CURRENCY_SYMBOL[c] || c}`;
  }

  function showPrice(m) {
    const box = $("price");
    box.replaceChildren();
    const title = document.createElement("div");
    title.className = "hint";
    title.textContent = t("price");
    const amount = document.createElement("div");
    amount.className = "amount";
    amount.textContent = money(m.amount, m.currency);
    const others = document.createElement("div");
    others.className = "others";
    for (const [c, v] of Object.entries(m.conversions)) {
      if (c === m.currency) continue;
      const span = document.createElement("span");
      span.textContent = `≈ ${money(v, c)}`;
      others.append(span);
    }
    box.append(title, amount, others);
    box.classList.remove("hidden");
  }

  function showSummary(texts) {
    const box = $("summary");
    box.replaceChildren();
    const h = document.createElement("h3");
    h.textContent = t("summary");
    const p = document.createElement("p");
    p.dir = "auto";
    p.textContent = texts[lang] || Object.values(texts).pop() || "";
    box.append(h, p);
    box.classList.remove("hidden");
  }

  // --- pazarlık ---------------------------------------------------------------

  const offers = new Map(); // id -> teklif

  function upsertOffer(o) {
    offers.set(o.id, o);
    renderOffers();
    const card = document.querySelector(`[data-offer="${o.id}"]`);
    if (card && o.status === "open") card.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function renderOffers() {
    const box = $("offers");
    if (!box) return;
    box.replaceChildren();
    let openFromHost = null;
    for (const o of offers.values()) {
      const mine = o.by === "guest";
      const card = el("div", `card offer ${o.status}`);
      card.dataset.offer = o.id;
      card.append(el("div", "hint", mine ? t("myOffer") : t("hostOffer")));
      card.append(el("div", "amount", money(o.amount, o.currency)));
      const others = el("div", "others");
      // Turist önce kendi para birimini görsün diye TL'yi sona koy.
      const entries = Object.entries(o.conversions).filter(([c]) => c !== o.currency)
        .sort(([a], [b]) => (a === "TRY") - (b === "TRY"));
      for (const [c, v] of entries) others.append(el("span", null, `≈ ${money(v, c)}`));
      card.append(others);

      if (o.status === "open" && !mine) {
        openFromHost = o;
        const buttons = el("div", "buttons");
        const accept = el("button", "accept", t("accept"));
        accept.type = "button";
        accept.onclick = () => send({ type: "offer_reply", offer_id: o.id, accept: true });
        const counter = el("button", null, t("counter"));
        counter.type = "button";
        counter.onclick = () => openCounter(o);
        buttons.append(accept, counter);
        card.append(buttons);
      } else if (o.status === "open") {
        card.append(el("div", "badge", t("waitingReply")));
      } else {
        card.append(el("div", "badge", t(`st_${o.status}`)));
      }
      box.append(card);
    }
    if (!openFromHost) $("counterForm").classList.add("hidden");
  }

  function openCounter(o) {
    const form = $("counterForm");
    // Turistin kendi para birimi: teklif TL ise euro önerelim.
    $("counterCurrency").value = o.currency === "TRY" ? "EUR" : o.currency;
    $("counterAmount").value = "";
    form.classList.remove("hidden");
    form.scrollIntoView({ behavior: "smooth", block: "center" });
    $("counterAmount").focus();
  }

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function showSaveLink(url) {
    if (!url) return;
    const a = el("a", "primary saveLink", t("saveSummary"));
    a.href = url;
    a.target = "_blank";
    a.rel = "noopener";
    $("closed").append(a);
  }

  function showClosed(text, keepTranscript = false) {
    $("welcome").classList.add("hidden");
    $("controls").classList.add("hidden");
    $("closedText").textContent = text;
    $("closed").classList.remove("hidden");
    if (!keepTranscript) $("transcript").replaceChildren();
  }

  function setStatus(s) { $("status").textContent = s; }

  function refreshStatus() {
    if (ended) return;
    const mic = $("mic");
    mic.classList.toggle("on", !!turn);
    mic.classList.toggle("hearing", inSpeech);
    mic.disabled = !micAvailable || (!turn && (busy || playing));
    if (!turn) mic.style.setProperty("--level", 0);

    let s;
    if (turn) s = inSpeech ? t("hearing") : t("listening");
    else if (busy) s = t("translating");
    else if (playing) s = t("playing");
    else if (!micAvailable) s = t("noMic");
    else if (notice) s = notice;
    else if (merchantLang && merchantLang === lang) s = t("sameLang");
    else s = t("tapToSpeak");
    setStatus(s);
  }

  // --- ses çalma -----------------------------------------------------------

  const player = $("player");
  const queue = [];
  let playing = false;

  function play(b64) {
    queue.push(`data:audio/mpeg;base64,${b64}`);
    if (!playing) next();
  }
  function next() {
    const src = queue.shift();
    if (!src) { playing = false; refreshStatus(); return; }
    playing = true;
    refreshStatus();
    player.src = src;
    player.play().catch(() => next());
  }
  player.addEventListener("ended", next);
  player.addEventListener("error", next);

  // --- mikrofon: tek konuşmalık tur -----------------------------------------
  // Dokununca açılır; konuşma bitince (kısa sessizlik) kendiliğinden kapanır ve gönderilir.
  // Hiç konuşulmazsa bir süre sonra boşuna açık kalmaz.

  const TARGET_RATE = 16000;
  const FRAME_MS = 30;
  const SILENCE_END_MS = 1000;
  const MIN_SPEECH_MS = 300;
  const MAX_SPEECH_MS = 30000;
  const NO_SPEECH_TIMEOUT_MS = 8000;

  let micAvailable = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  let audioCtx = null;
  let turn = null; // { stream, source, processor, sink, startedAt }
  let noiseFloor = 0.004, inSpeech = false, speechMs = 0, silenceMs = 0, loudFrames = 0;
  let chunks = [], preRoll = [], pending = new Float32Array(0);

  function ensureAudioContext() {
    // iOS Safari: ses motoru ancak dokunma anında (await'ten önce) başlatılırsa çalışır.
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!audioCtx) audioCtx = new AC();
    return audioCtx.resume();
  }

  async function startTurn() {
    if (turn || busy || playing || !micAvailable) return;
    notice = "";
    const resumed = ensureAudioContext();
    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
    } catch {
      micAvailable = false;
      return refreshStatus();
    }
    await resumed;
    const source = audioCtx.createMediaStreamSource(stream);
    const processor = audioCtx.createScriptProcessor(4096, 1, 1);
    const sink = audioCtx.createGain();
    sink.gain.value = 0; // işlemci ancak çıkışa bağlıysa çalışır; hoparlöre ses vermesin
    source.connect(processor);
    processor.connect(sink);
    sink.connect(audioCtx.destination);
    resetVad();
    turn = { stream, source, processor, sink, startedAt: Date.now() };
    processor.onaudioprocess = (e) => onAudio(e.inputBuffer.getChannelData(0), audioCtx.sampleRate);
    refreshStatus();
  }

  /** Turu kapatır; [submit] ise yakalanan konuşmayı gönderir. */
  function stopTurn(submit) {
    if (!turn) return;
    const voiced = speechMs - silenceMs;
    const pcm = inSpeech ? concat(chunks) : null;
    turn.processor.onaudioprocess = null;
    turn.source.disconnect();
    turn.processor.disconnect();
    turn.sink.disconnect();
    turn.stream.getTracks().forEach((tr) => tr.stop()); // telefonun mikrofon simgesi de kapansın
    turn = null;
    resetVad();
    if (submit && pcm && voiced >= MIN_SPEECH_MS && send(encodeWav(pcm))) {
      busy = true; // sunucudan "processing" gelmeden de çeviri bekleniyor görünsün
    }
    refreshStatus();
  }

  function resetVad() {
    inSpeech = false; speechMs = 0; silenceMs = 0; loudFrames = 0;
    chunks = []; preRoll = []; pending = new Float32Array(0);
  }

  function downsample(input, rate) {
    if (rate === TARGET_RATE) return Float32Array.from(input);
    const ratio = rate / TARGET_RATE;
    const out = new Float32Array(Math.floor(input.length / ratio));
    for (let i = 0; i < out.length; i++) {
      const start = Math.floor(i * ratio), end = Math.min(input.length, Math.floor((i + 1) * ratio));
      let sum = 0;
      for (let j = start; j < end; j++) sum += input[j];
      out[i] = sum / Math.max(1, end - start);
    }
    return out;
  }

  function onAudio(input, rate) {
    if (!turn) return;
    const data = downsample(input, rate);
    const merged = new Float32Array(pending.length + data.length);
    merged.set(pending); merged.set(data, pending.length);
    const frameLen = (TARGET_RATE * FRAME_MS) / 1000;
    let off = 0;
    for (; off + frameLen <= merged.length && turn; off += frameLen) vadFrame(merged.subarray(off, off + frameLen));
    pending = merged.slice(off);
  }

  function vadFrame(frame) {
    let sum = 0;
    for (let i = 0; i < frame.length; i++) sum += frame[i] * frame[i];
    const rms = Math.sqrt(sum / frame.length);
    const threshold = Math.max(0.01, noiseFloor * 3);
    const loud = rms > threshold;
    $("mic").style.setProperty("--level", Math.min(1, rms / 0.15).toFixed(2));

    if (!inSpeech) {
      noiseFloor = noiseFloor * 0.95 + rms * 0.05;
      preRoll.push(Float32Array.from(frame));
      if (preRoll.length > 10) preRoll.shift(); // ~300 ms ön kayıt, kelime başı kesilmesin
      loudFrames = loud ? loudFrames + 1 : 0;
      if (loudFrames >= 3) {
        inSpeech = true; chunks = preRoll; preRoll = []; speechMs = chunks.length * FRAME_MS; silenceMs = 0;
        refreshStatus();
      } else if (Date.now() - turn.startedAt > NO_SPEECH_TIMEOUT_MS) {
        notice = t("noSpeech");
        stopTurn(false);
      }
      return;
    }
    chunks.push(Float32Array.from(frame));
    speechMs += FRAME_MS;
    silenceMs = loud ? 0 : silenceMs + FRAME_MS;
    if (silenceMs >= SILENCE_END_MS || speechMs >= MAX_SPEECH_MS) stopTurn(true);
  }

  function concat(list) {
    const out = new Float32Array(list.reduce((n, a) => n + a.length, 0));
    let o = 0;
    for (const a of list) { out.set(a, o); o += a.length; }
    return out;
  }

  function encodeWav(samples) {
    const buf = new ArrayBuffer(44 + samples.length * 2);
    const v = new DataView(buf);
    const w = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    w(0, "RIFF"); v.setUint32(4, 36 + samples.length * 2, true); w(8, "WAVE");
    w(12, "fmt "); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
    v.setUint32(24, TARGET_RATE, true); v.setUint32(28, TARGET_RATE * 2, true);
    v.setUint16(32, 2, true); v.setUint16(34, 16, true);
    w(36, "data"); v.setUint32(40, samples.length * 2, true);
    for (let i = 0; i < samples.length; i++) {
      const s = Math.max(-1, Math.min(1, samples[i]));
      v.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
    }
    return buf;
  }

  // --- başlatma -------------------------------------------------------------

  async function init() {
    setupLangSelect();
    applyLang();
    const info = await checkSession().catch(() => null);
    if (!info) return showClosed(t("expired"));
    $("business").textContent = info.business_name;
    merchantLang = info.merchant_lang;

    $("start").addEventListener("click", () => {
      // iOS: ses çalma ve ses motoru kullanıcı dokunuşuyla açılmalı.
      ensureAudioContext();
      player.src = URL.createObjectURL(new Blob([encodeWav(new Float32Array(1600))], { type: "audio/wav" }));
      player.play().catch(() => {});
      $("welcome").classList.add("hidden");
      $("controls").classList.remove("hidden");
      connect();
      refreshStatus();
    });

    // Açıkken dokunmak: konuşmayı hemen bitirip gönderir.
    $("mic").addEventListener("click", () => (turn ? stopTurn(true) : startTurn()));
    $("counterForm").addEventListener("submit", (e) => {
      e.preventDefault();
      const amount = parseFloat($("counterAmount").value.replace(",", "."));
      if (!(amount > 0)) return;
      send({ type: "offer", amount, currency: $("counterCurrency").value });
      $("counterForm").classList.add("hidden");
    });
    $("textForm").addEventListener("submit", (e) => {
      e.preventDefault();
      const text = $("textInput").value.trim();
      if (!text) return;
      send({ type: "text", text });
      $("textInput").value = "";
    });
  }

  init();
})();
