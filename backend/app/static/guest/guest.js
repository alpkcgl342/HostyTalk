// HostyTalk web katılım sayfası: QR ile açılır, kullanıcı hesabıyla giriş yapıp konuşmaya katılır.
// Mikrofon sesli asistan gibi çalışır: dokun → konuş → susunca kendiliğinden kapanır ve gönderilir.
(() => {
  "use strict";

  const LANGS = { tr: "Türkçe", en: "English", ar: "العربية", fr: "Français", ru: "Русский" };

  const T = {
    en: { welcome: "Speak in your language — the other person hears it in theirs.",
      invite: "{name} invited you to a conversation", signIn: "Sign in", register: "Create account",
      email: "Email", password: "Password (min. 6 characters)", name: "Your name",
      haveAccount: "Already have an account? Sign in", noAccount: "New here? Create an account",
      signedInAs: "Signed in as {name}", logout: "Sign out", start: "Join conversation",
      startHint: "Allow microphone access when asked.", send: "Send", typeHere: "Or type a message…",
      tapToSpeak: "Tap the microphone and speak.", listening: "Listening… speak now.", hearing: "Hearing you…",
      noSpeech: "I didn't hear anything. Tap to try again.", translating: "Translating…",
      playing: "Playing translation…", sameLang: "Choose your language at the top right.",
      expired: "This conversation code is no longer valid. Ask for a new one.",
      full: "This conversation already has two people.",
      ended: "The conversation has ended. You can find it in your history in the HostyTalk app.",
      reconnecting: "Connection lost, reconnecting…", noMic: "Microphone is not available here. You can still type.",
      summary: "Summary", error: "Something went wrong, please try again.",
      errCredentials: "Wrong email or password.", errTaken: "An account with this email already exists." },
    tr: { welcome: "Kendi dilinizde konuşun, karşınızdaki kendi dilinde duysun.",
      invite: "{name} sizi bir konuşmaya davet etti", signIn: "Giriş yap", register: "Hesap oluştur",
      email: "E-posta", password: "Şifre (en az 6 karakter)", name: "Adınız",
      haveAccount: "Hesabınız var mı? Giriş yapın", noAccount: "Yeni misiniz? Hesap oluşturun",
      signedInAs: "{name} olarak giriş yapıldı", logout: "Çıkış yap", start: "Konuşmaya katıl",
      startHint: "İstendiğinde mikrofon izni verin.", send: "Gönder", typeHere: "Ya da mesaj yazın…",
      tapToSpeak: "Mikrofona dokunun ve konuşun.", listening: "Dinliyorum… konuşabilirsiniz.", hearing: "Sizi duyuyorum…",
      noSpeech: "Bir şey duyamadım. Tekrar denemek için dokunun.", translating: "Çevriliyor…",
      playing: "Çeviri seslendiriliyor…", sameLang: "Sağ üstten kendi dilinizi seçin.",
      expired: "Bu konuşma kodu artık geçerli değil. Yeni bir kod isteyin.",
      full: "Bu konuşmada zaten iki kişi var.",
      ended: "Konuşma sona erdi. HostyTalk uygulamasında geçmişinizde bulabilirsiniz.",
      reconnecting: "Bağlantı koptu, yeniden bağlanılıyor…", noMic: "Burada mikrofon kullanılamıyor. Yine de yazabilirsiniz.",
      summary: "Özet", error: "Bir sorun oluştu, lütfen tekrar deneyin.",
      errCredentials: "E-posta ya da şifre hatalı.", errTaken: "Bu e-posta ile kayıtlı bir hesap zaten var." },
    ar: { welcome: "تحدّث بلغتك، وسيسمعك الطرف الآخر بلغته.",
      invite: "دعاك {name} إلى محادثة", signIn: "تسجيل الدخول", register: "إنشاء حساب",
      email: "البريد الإلكتروني", password: "كلمة المرور (6 أحرف على الأقل)", name: "اسمك",
      haveAccount: "لديك حساب؟ سجّل الدخول", noAccount: "جديد هنا؟ أنشئ حساباً",
      signedInAs: "تم تسجيل الدخول باسم {name}", logout: "تسجيل الخروج", start: "انضم إلى المحادثة",
      startHint: "اسمح بالوصول إلى الميكروفون عند الطلب.", send: "إرسال", typeHere: "أو اكتب رسالة…",
      tapToSpeak: "اضغط على الميكروفون وتحدّث.", listening: "أستمع… تحدّث الآن.", hearing: "أسمعك…",
      noSpeech: "لم أسمع شيئاً. اضغط للمحاولة مرة أخرى.", translating: "جارٍ الترجمة…",
      playing: "تشغيل الترجمة…", sameLang: "اختر لغتك من أعلى الصفحة.",
      expired: "رمز المحادثة لم يعد صالحاً. اطلب رمزاً جديداً.",
      full: "هذه المحادثة تضم شخصين بالفعل.",
      ended: "انتهت المحادثة. يمكنك العثور عليها في سجلك في تطبيق HostyTalk.",
      reconnecting: "انقطع الاتصال، جارٍ إعادة الاتصال…", noMic: "الميكروفون غير متاح هنا. لا يزال بإمكانك الكتابة.",
      summary: "الملخص", error: "حدث خطأ ما، يرجى المحاولة مرة أخرى.",
      errCredentials: "البريد الإلكتروني أو كلمة المرور غير صحيحة.", errTaken: "يوجد حساب مسجّل بهذا البريد الإلكتروني." },
    fr: { welcome: "Parlez dans votre langue, l'autre personne vous entend dans la sienne.",
      invite: "{name} vous invite à une conversation", signIn: "Se connecter", register: "Créer un compte",
      email: "E-mail", password: "Mot de passe (6 caractères min.)", name: "Votre nom",
      haveAccount: "Déjà un compte ? Connectez-vous", noAccount: "Nouveau ? Créez un compte",
      signedInAs: "Connecté en tant que {name}", logout: "Se déconnecter", start: "Rejoindre la conversation",
      startHint: "Autorisez l'accès au micro lorsqu'il est demandé.", send: "Envoyer", typeHere: "Ou écrivez un message…",
      tapToSpeak: "Touchez le micro et parlez.", listening: "À l'écoute… parlez maintenant.", hearing: "Je vous entends…",
      noSpeech: "Je n'ai rien entendu. Touchez pour réessayer.", translating: "Traduction…",
      playing: "Lecture de la traduction…", sameLang: "Choisissez votre langue en haut à droite.",
      expired: "Ce code de conversation n'est plus valable. Demandez-en un nouveau.",
      full: "Cette conversation compte déjà deux personnes.",
      ended: "La conversation est terminée. Vous la retrouverez dans votre historique dans l'application HostyTalk.",
      reconnecting: "Connexion perdue, reconnexion…", noMic: "Le micro n'est pas disponible ici. Vous pouvez toujours écrire.",
      summary: "Résumé", error: "Un problème est survenu, veuillez réessayer.",
      errCredentials: "E-mail ou mot de passe incorrect.", errTaken: "Un compte existe déjà avec cet e-mail." },
    ru: { welcome: "Говорите на своём языке — собеседник услышит вас на своём.",
      invite: "{name} приглашает вас в разговор", signIn: "Войти", register: "Создать аккаунт",
      email: "Эл. почта", password: "Пароль (не менее 6 символов)", name: "Ваше имя",
      haveAccount: "Уже есть аккаунт? Войдите", noAccount: "Впервые здесь? Создайте аккаунт",
      signedInAs: "Вы вошли как {name}", logout: "Выйти", start: "Присоединиться к разговору",
      startHint: "Разрешите доступ к микрофону, когда появится запрос.", send: "Отправить", typeHere: "Или напишите сообщение…",
      tapToSpeak: "Нажмите на микрофон и говорите.", listening: "Слушаю… говорите.", hearing: "Слышу вас…",
      noSpeech: "Ничего не слышно. Нажмите, чтобы попробовать снова.", translating: "Перевожу…",
      playing: "Воспроизвожу перевод…", sameLang: "Выберите свой язык вверху справа.",
      expired: "Этот код разговора больше не действует. Попросите новый.",
      full: "В этом разговоре уже два человека.",
      ended: "Разговор завершён. Он сохранён в вашей истории в приложении HostyTalk.",
      reconnecting: "Связь потеряна, переподключение…", noMic: "Микрофон здесь недоступен. Вы можете писать.",
      summary: "Резюме", error: "Что-то пошло не так, попробуйте ещё раз.",
      errCredentials: "Неверная эл. почта или пароль.", errTaken: "Аккаунт с этой эл. почтой уже существует." },
  };

  const $ = (id) => document.getElementById(id);
  const code = (location.pathname.split("/").filter(Boolean).pop() || "").toUpperCase();

  let lang = detectLang();
  let token = safeGet("hostytalk.token");
  let me = null;          // giriş yapan kullanıcı
  let invite = null;      // { host_name, host_lang }
  let session = null;     // katıldıktan sonra sunucunun döndürdüğü oturum
  let registerMode = false;
  let ws = null;
  let ended = false;
  let busy = false;
  let notice = "";

  function detectLang() {
    const saved = safeGet("hostytalk.lang");
    if (saved && LANGS[saved]) return saved;
    for (const l of navigator.languages || [navigator.language || "en"]) {
      const c = l.toLowerCase().split("-")[0];
      if (LANGS[c]) return c;
    }
    return "en";
  }
  // Tarayıcı depolaması kapalıysa (gizli sekme vb.) sayfa yine çalışsın.
  function safeGet(k) { try { return localStorage.getItem(k); } catch { return null; } }
  function safeSet(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* yoksay */ } }
  const t = (key, vars = {}) =>
    ((T[lang] && T[lang][key]) || T.en[key]).replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? "");

  function applyLang() {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    document.querySelectorAll("[data-i18n]").forEach((el) => (el.textContent = t(el.dataset.i18n)));
    $("textInput").placeholder = t("typeHere");
    $("email").placeholder = t("email");
    $("password").placeholder = t("password");
    $("displayName").placeholder = t("name");
    $("lang").value = lang;
    renderAuth();
    if (invite) $("inviteText").textContent = t("invite", { name: invite.host_name });
    if (me) $("signedInAs").textContent = t("signedInAs", { name: me.display_name });
    refreshStatus();
  }

  function setupLangSelect() {
    const sel = $("lang");
    for (const [c, name] of Object.entries(LANGS)) sel.add(new Option(name, c));
    sel.addEventListener("change", () => {
      lang = sel.value;
      safeSet("hostytalk.lang", lang);
      applyLang();
      send({ type: "set_lang", lang });
    });
  }

  // --- hesap -----------------------------------------------------------------

  async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (token) headers.Authorization = `Bearer ${token}`;
    const r = await fetch(path, { ...options, headers });
    let body = null;
    try { body = await r.json(); } catch { /* boş yanıt */ }
    if (!r.ok) throw Object.assign(new Error((body && body.detail) || `http_${r.status}`), { status: r.status });
    return body;
  }

  function renderAuth() {
    $("authTitle").textContent = registerMode ? t("register") : t("signIn");
    $("authSubmit").textContent = registerMode ? t("register") : t("signIn");
    $("authToggle").textContent = registerMode ? t("haveAccount") : t("noAccount");
    $("displayName").classList.toggle("hidden", !registerMode);
    $("displayName").required = registerMode;
    $("password").autocomplete = registerMode ? "new-password" : "current-password";
  }

  function showSignedIn() {
    $("authForm").classList.add("hidden");
    $("signedInAs").textContent = t("signedInAs", { name: me.display_name });
    $("welcome").classList.remove("hidden");
  }

  function showAuth() {
    $("welcome").classList.add("hidden");
    $("authForm").classList.remove("hidden");
    renderAuth();
  }

  async function submitAuth(e) {
    e.preventDefault();
    const err = $("authError");
    err.classList.add("hidden");
    const body = { email: $("email").value.trim(), password: $("password").value };
    if (registerMode) body.display_name = $("displayName").value.trim();
    try {
      const r = await api(registerMode ? "/api/auth/register" : "/api/auth/login",
        { method: "POST", body: JSON.stringify(body) });
      token = r.token;
      me = r.user;
      safeSet("hostytalk.token", token);
      $("password").value = "";
      showSignedIn();
    } catch (ex) {
      err.textContent = ex.message === "invalid_credentials" ? t("errCredentials")
        : ex.message === "email_taken" ? t("errTaken") : t("error");
      err.classList.remove("hidden");
    }
  }

  function logout() {
    token = null;
    me = null;
    safeSet("hostytalk.token", null);
    showAuth();
  }

  // --- bağlantı -------------------------------------------------------------

  async function join() {
    try {
      session = await api("/api/sessions/join", { method: "POST", body: JSON.stringify({ code, lang }) });
    } catch (ex) {
      if (ex.status === 401) return logout();
      return showClosed(ex.message === "session_full" ? t("full") : t("expired"));
    }
    $("invite").classList.add("hidden");
    $("welcome").classList.add("hidden");
    $("controls").classList.remove("hidden");
    connect();
    refreshStatus();
  }

  function connect() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const q = new URLSearchParams({ role: "guest", token });
    ws = new WebSocket(`${proto}://${location.host}/ws/${encodeURIComponent(session.id)}?${q}`);
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
        session = { ...session, ...m };
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
      case "summary": showSummary(m.texts); break;
      case "error":
        busy = false; notice = t("error"); refreshStatus(); break;
      case "ended":
        ended = true; stopTurn(false); showClosed(t("ended"), true); break;
    }
  }

  // --- arayüz ----------------------------------------------------------------

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) { e.textContent = text; e.dir = "auto"; }
    return e;
  }

  function addBubble(m) {
    const mine = m.from === "guest";
    const li = el("li", `bubble ${mine ? "mine" : "theirs"}`);
    // Herkes kendi dilindeki metni büyük görür.
    li.append(el("div", "main", mine ? m.original : m.translated), el("div", "sub", mine ? m.translated : m.original));
    $("transcript").append(li);
    li.scrollIntoView({ behavior: "smooth", block: "end" });
  }

  function showSummary(texts) {
    const box = $("summary");
    box.replaceChildren(el("h3", null, t("summary")), el("p", null, texts[lang] || Object.values(texts).pop() || ""));
    box.classList.remove("hidden");
  }

  function showClosed(text, keepTranscript = false) {
    for (const id of ["invite", "authForm", "welcome", "controls"]) $(id).classList.add("hidden");
    $("closedText").textContent = text;
    $("closed").classList.remove("hidden");
    if (!keepTranscript) $("transcript").replaceChildren();
  }

  function setStatus(s) { $("status").textContent = s; }

  function refreshStatus() {
    if (ended || !session) return;
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
    else if (session.host_lang && session.host_lang === lang) s = t("sameLang");
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

    try {
      invite = await api(`/api/sessions/code/${encodeURIComponent(code)}`);
    } catch {
      return showClosed(t("expired"));
    }
    $("inviteText").textContent = t("invite", { name: invite.host_name });

    if (token) {
      try { me = await api("/api/me"); } catch { token = null; safeSet("hostytalk.token", null); }
    }
    me ? showSignedIn() : showAuth();

    $("authForm").addEventListener("submit", submitAuth);
    $("authToggle").addEventListener("click", () => { registerMode = !registerMode; renderAuth(); });
    $("logout").addEventListener("click", logout);

    $("start").addEventListener("click", () => {
      // iOS: ses çalma ve ses motoru kullanıcı dokunuşuyla açılmalı.
      ensureAudioContext();
      player.src = URL.createObjectURL(new Blob([encodeWav(new Float32Array(1600))], { type: "audio/wav" }));
      player.play().catch(() => {});
      join();
    });

    // Açıkken dokunmak: konuşmayı hemen bitirip gönderir.
    $("mic").addEventListener("click", () => (turn ? stopTurn(true) : startTurn()));
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
