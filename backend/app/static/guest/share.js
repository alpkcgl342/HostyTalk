// HostyTalk konuşma özeti: turistin sonradan açıp saklayabildiği / paylaşabildiği sayfa.
(() => {
  "use strict";

  const LANGS = { tr: "Türkçe", en: "English", ar: "العربية", fr: "Français", ru: "Русский" };
  const T = {
    en: { summary: "What we agreed on", deal: "Agreed price", transcript: "Conversation",
      share: "Share", save: "Save as PDF", notFound: "This summary could not be found.", you: "You", host: "Host",
      copied: "Link copied." },
    tr: { summary: "Neyde anlaştık", deal: "Anlaşılan fiyat", transcript: "Konuşma",
      share: "Paylaş", save: "PDF olarak kaydet", notFound: "Bu özet bulunamadı.", you: "Siz", host: "İşletme",
      copied: "Bağlantı kopyalandı." },
    ar: { summary: "ما اتفقنا عليه", deal: "السعر المتفق عليه", transcript: "المحادثة",
      share: "مشاركة", save: "حفظ كملف PDF", notFound: "تعذّر العثور على هذا الملخص.", you: "أنت", host: "المضيف",
      copied: "تم نسخ الرابط." },
    fr: { summary: "Ce que nous avons convenu", deal: "Prix convenu", transcript: "Conversation",
      share: "Partager", save: "Enregistrer en PDF", notFound: "Ce résumé est introuvable.", you: "Vous", host: "Hôte",
      copied: "Lien copié." },
    ru: { summary: "О чём мы договорились", deal: "Согласованная цена", transcript: "Разговор",
      share: "Поделиться", save: "Сохранить в PDF", notFound: "Резюме не найдено.", you: "Вы", host: "Хозяин",
      copied: "Ссылка скопирована." },
  };
  const SYMBOL = { TRY: "₺", EUR: "€", USD: "$", GBP: "£" };

  const $ = (id) => document.getElementById(id);
  const key = location.pathname.split("/").filter(Boolean).pop();
  let data = null;
  let lang = "en";
  const t = (k) => (T[lang] && T[lang][k]) || T.en[k];

  function money(v, c) {
    return `${Number(v).toLocaleString(lang, { maximumFractionDigits: 2 })} ${SYMBOL[c] || c}`;
  }

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) { e.textContent = text; e.dir = "auto"; }
    return e;
  }

  function render() {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    document.querySelectorAll("[data-i18n]").forEach((n) => (n.textContent = t(n.dataset.i18n)));
    $("lang").value = lang;
    if (!data) return;

    $("business").textContent = data.business_name;
    $("date").textContent = new Date(data.started_at * 1000).toLocaleString(lang, { dateStyle: "long", timeStyle: "short" });

    const accepted = data.offers.filter((o) => o.status === "accepted").pop();
    const deal = $("deal");
    deal.replaceChildren();
    if (accepted) {
      deal.append(el("div", "hint", t("deal")), el("div", "amount", money(accepted.amount, accepted.currency)));
      const others = el("div", "others");
      for (const [c, v] of Object.entries(accepted.conversions)) {
        if (c !== accepted.currency) others.append(el("span", null, `≈ ${money(v, c)}`));
      }
      deal.append(others);
    }
    deal.classList.toggle("hidden", !accepted);

    const box = $("summary");
    box.replaceChildren();
    if (data.summary) {
      box.append(el("h3", null, t("summary")));
      // Önce turistin dili, sonra esnafın dili.
      const ordered = Object.entries(data.summary).sort((x, y) => (y[0] === lang) - (x[0] === lang));
      ordered.forEach(([code, text], i) => {
        const p = el("p", i ? "sub" : null, text);
        p.lang = code;
        box.append(p);
      });
    }
    box.classList.toggle("hidden", !data.summary);

    const list = $("transcript");
    list.replaceChildren();
    for (const m of data.messages) {
      const mine = m.from === "guest";
      const li = el("li", `bubble ${mine ? "mine" : "theirs"}`);
      li.append(el("div", "who", mine ? t("you") : t("host")));
      li.append(el("div", "main", mine ? m.original : m.translated));
      li.append(el("div", "sub", mine ? m.translated : m.original));
      list.append(li);
    }
    $("transcriptTitle").classList.toggle("hidden", !data.messages.length);
  }

  async function init() {
    const sel = $("lang");
    for (const [code, name] of Object.entries(LANGS)) sel.add(new Option(name, code));
    sel.addEventListener("change", () => { lang = sel.value; render(); });

    try {
      const r = await fetch(`/api/share/${encodeURIComponent(key)}`);
      if (r.ok) data = await r.json();
    } catch { /* aşağıda "bulunamadı" gösterilir */ }

    lang = (data && LANGS[data.guest_lang]) ? data.guest_lang : "en";
    if (!data) {
      $("notFound").classList.remove("hidden");
      document.querySelector(".actions").classList.add("hidden");
    }
    render();

    $("shareBtn").addEventListener("click", async () => {
      const shareData = { title: `HostyTalk · ${data.business_name}`, url: location.href };
      if (navigator.share) {
        try { await navigator.share(shareData); } catch { /* kullanıcı vazgeçti */ }
      } else {
        try { await navigator.clipboard.writeText(location.href); alert(t("copied")); } catch { /* yoksay */ }
      }
    });
    $("printBtn").addEventListener("click", () => window.print());
  }

  init();
})();
