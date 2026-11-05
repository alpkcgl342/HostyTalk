# HostyTalk — Kurulum ve Çalıştırma

Ürün tanımı için [HostyTalk.md](HostyTalk.md). Bu belge kodun nasıl çalıştırılacağını anlatır.

```
HostyTalk/
├── backend/   Python (FastAPI) sunucu + yapay zekâ köprüsü + turist web sayfası
└── android/   Esnaf uygulaması (Kotlin + Jetpack Compose) — Android Studio ile açılır
```

## Mimari

```
Esnaf Android  ──WebSocket──►  Python sunucu  ◄──WebSocket──  Turist tarayıcısı (QR)
  (mikrofon, QR,               │  oturum, QR, giriş, kota
   canlı döküm)                │  ses → Whisper (konuşmadan metne + dil algılama)
                               │      → ChatGPT (turizme özel çeviri)
                               │      → OpenAI TTS (metinden sese)
                               ▼
                         OpenAI API (tek API anahtarı)
```

- Her iki taraf da eller serbest konuşur: cihaz sessizlik algılayınca cümleyi gönderir.
- Esnafın ve turistin telefonları yan yana olduğu için bir telefon diğer tarafın sesini de duyabilir; sunucu algılanan dile bakıp bu yankıları eler.
- Turist başka bir dilde konuşmaya başlarsa dili otomatik değişir.
- Desteklenen diller: **Türkçe, İngilizce, Arapça, Fransızca, Rusça** (uygulama arayüzü, turist sayfası, çeviri ve hazır cümleler). Yeni dil eklemek için `backend/app/languages.py`, `android/.../data/Languages.kt` ve `values-xx/strings.xml` yeterli.

## 1. Backend (Python)

Python 3.11+ gerekir.

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
```

`.env` içinde en az şunları doldurun:

| Değişken | Açıklama |
|---|---|
| `OPENAI_API_KEY` | ChatGPT (OpenAI) API anahtarı |
| `JWT_SECRET` | Uzun, rastgele bir değer |
| `PUBLIC_BASE_URL` | Turistin QR ile açacağı adres (aşağıya bakın) |

Çalıştırma:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Testler (OpenAI taklit edilir, anahtar gerekmez):

```bash
python -m pytest
```

### HTTPS uyarısı (önemli)

Tarayıcılar mikrofonu yalnızca **HTTPS** sayfalarda açar. `http://192.168.x.x` adresinde turist sadece yazarak konuşabilir. Gerçek denemede sunucuyu HTTPS arkasına koyun; geliştirme için bir tünel en kolayı:

```bash
cloudflared tunnel --url http://localhost:8000
```

Verilen `https://...trycloudflare.com` adresini `.env` içindeki `PUBLIC_BASE_URL` alanına ve uygulamadaki **Sunucu adresi** ayarına yazın.

## 2. Android uygulaması

1. Android Studio → **Open** → `HostyTalk/android` klasörünü seçin, Gradle senkronunu bekleyin.
2. Emülatörde sunucu otomatik olarak `http://10.0.2.2:8000` adresindedir. Gerçek telefonda uygulamanın giriş ekranındaki **⚙ Sunucu adresi** bölümünden bilgisayarın IP'sini ya da HTTPS tünel adresini girin (ya da `android/local.properties` içine `API_BASE_URL=...` ekleyin).
3. Çalıştırın, işletme hesabı oluşturun, **Yeni konuşma** deyin, QR'ı başka bir telefonla okutun.

Ekranlar:
- **Giriş / Kayıt** — e-posta, şifre, işletme adı, işletme türü
- **Ana sayfa** — aylık dakika kullanımı, "konuştuğum dil", işletme türü, uygulama dili, yeni konuşma
- **Konuşma (Tezgâh modu)** — QR ve geçerlilik sayacı, iki dilde canlı döküm, sesli asistan tarzı mikrofon (dokun → konuş → susunca gönderilir), hazır cümleler, fiyat gösterme ve pazarlık, özetle bitirme ve paylaşma
- **Hazır cümlelerim** — işletme türüne göre hazır set (otel, restoran, mağaza/çarşı, taksi, rehber, araç kiralama, diğer) + esnafın kendi eklediği cümleler
- **Geçmiş konuşmalar** — her konuşmanın dökümü, teklifleri, anlaşılan fiyatı ve özeti; sonradan özet oluşturma, paylaşma (WhatsApp vb.), silme

**Pazarlık modu:** Esnaf "Fiyat / teklif → Teklif ver" ile teklif verir. Turist kendi para biriminde görür, **Kabul et** ya da **Karşı teklif** der. Esnaf turistin teklifini kabul eder, reddeder ya da yeni teklif verir. Kabul edilen fiyat özete ve geçmişe yazılır.

**Özet paylaşma:** Konuşma bitince turistin sayfasında **Özeti kaydet** bağlantısı çıkar (`/s/<anahtar>`). Bu sayfada anlaşılan fiyat, iki dilde özet ve döküm görünür; turist paylaşabilir ya da PDF olarak kaydedebilir. Hiç konuşulmadan bitirilen konuşmalar kaydedilmez.

Uygulama dili cihaz dilini izler; uygulama içinden de değiştirilebilir (Arapça seçildiğinde arayüz sağdan sola döner).

## API özeti

| Uç nokta | Açıklama |
|---|---|
| `POST /api/auth/register`, `/api/auth/login` | Esnaf hesabı, JWT döner |
| `GET /api/me` | Hesap + aylık kullanım |
| `PATCH /api/me` | İşletme türünü değiştir |
| `GET/POST /api/phrases`, `DELETE /api/phrases/{id}` | Hazır cümleler (tür setleri + esnafın kendi cümleleri) |
| `GET /api/conversations`, `GET/DELETE /api/conversations/{id}` | Konuşma geçmişi |
| `POST /api/conversations/{id}/summary` | Sonradan özet oluştur |
| `GET /s/{key}` | Turistin özet sayfası (hesapsız) |
| `POST /api/sessions` | Yeni konuşma, QR adresi (30 dk geçerli) |
| `GET /g/{id}?k=...` | Turist sayfası (hesapsız) |
| `WS /ws/{id}?role=merchant&token=...` / `?role=guest&k=...` | Canlı konuşma |

WebSocket'e ikili (binary) mesaj olarak 16 kHz mono WAV cümle gönderilir; JSON mesajlar: `text`, `price`, `offer`, `offer_reply`, `set_lang`, `end`. Sunucu `state`, `processing`, `message` (dinleyene `audio_b64` ile), `price`, `offer`, `summary`, `error`, `ended` olaylarını yollar.

## Henüz yapılmayanlar

- Ödeme / abonelik (yalnızca deneme kotası takip ediliyor)
- Esnaf hafızası (menü/ürün listesi), çoklu çalışan — v2
- Oturumlar sunucu belleğinde tutulur; birden fazla sunucuya ölçeklemek için Redis gibi bir paylaşımlı depo gerekir
- Belgede 6. dil olarak geçen Almanca bu sürümde yok (isteğiniz üzerine 5 dil)
