# HostyTalk — Kurulum ve Çalıştırma

> Farklı diller konuşan iki kişi için gerçek zamanlı sesli tercüman.
> Okulda, işte, yolda, komşuyla, müşteriyle: herkes kendi dilinde konuşur, karşısındaki kendi dilinde duyar.

Bu belge kodun nasıl çalıştırılacağını anlatır. İlk ürün fikri (turizm/esnaf odaklı) [HostyTalk.md](HostyTalk.md) içinde duruyor; uygulama artık genel kullanıma yönelik.

```
HostyTalk/
├── backend/   Python (FastAPI) sunucu + yapay zekâ köprüsü + web katılım sayfası
└── android/   Android uygulaması (Kotlin + Jetpack Compose) — Android Studio ile açılır
```

## Nasıl çalışır

1. Bir kişi uygulamada **Konuşma başlat** der. Ekranda 6 haneli bir kod (ör. `T8K 3KD`) ve QR kod çıkar.
2. Karşısındaki kişi uygulamada **Konuşmaya katıl** deyip kodu girer. Uygulaması yoksa (ör. iPhone) QR'ı kamerayla okutup tarayıcıdan katılır.
3. **İki tarafın da HostyTalk hesabı vardır.** Konuşma iki kişiliktir; her kişinin konuşma süresi kendi aylık kotasından düşer.
4. Mikrofona dokunup konuşursunuz; susunca cümle gönderilir, çevrilir ve karşı tarafın telefonunda seslendirilir. Sessiz ortamlarda yazarak da gönderilebilir.
5. Konuşmayı iki taraftan biri bitirir; isterse iki dilde kısa bir özet oluşur. Konuşma iki kişinin de **Geçmiş konuşmalar** ekranına düşer.

Hazır/otomatik mesaj yoktur; ekranda yalnızca iki kişinin gerçekten söyledikleri görünür.

## Mimari

```
Android (başlatan)  ──WebSocket──►  Python sunucu  ◄──WebSocket──  Android ya da tarayıcı (katılan)
                                    │  hesaplar, katılım kodu, kota, geçmiş
                                    │  ses → Whisper (konuşmadan metne + dil algılama)
                                    │      → ChatGPT (doğal, tonu koruyan çeviri)
                                    │      → OpenAI TTS (metinden sese)
                                    ▼
                              OpenAI API (tek API anahtarı)
```

- İki kişi yan yana konuşurken bir telefon diğerinin sesini de duyabilir; sunucu algılanan dile bakıp bu yankıları eler.
- Biri başka bir dilde konuşmaya başlarsa o kişinin dili otomatik değişir.
- Desteklenen diller: **Türkçe, İngilizce, Arapça, Fransızca, Rusça**. Yeni dil eklemek için `backend/app/languages.py`, `android/.../data/Languages.kt` ve `values-xx/strings.xml` yeterli.

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
| `PUBLIC_BASE_URL` | QR kodun açacağı adres (aşağıya bakın) |

Çalıştırma:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Testler (OpenAI taklit edilir, anahtar gerekmez):

```bash
python -m pytest
```

İlk sürümün (esnaf/turist) veritabanı açılışta otomatik olarak yeni yapıya taşınır: hesaplar ve kullanım korunur, eski konuşma kayıtları silinir.

### HTTPS uyarısı (önemli)

Tarayıcılar mikrofonu yalnızca **HTTPS** sayfalarda açar. QR ile tarayıcıdan katılan kişinin mikrofonu `http://192.168.x.x` adresinde çalışmaz (yazarak katılabilir). Geliştirme için bir tünel en kolayı:

```bash
cloudflared tunnel --url http://localhost:8000
```

Verilen `https://...trycloudflare.com` adresini `.env` içindeki `PUBLIC_BASE_URL` alanına yazıp sunucuyu yeniden başlatın.

## 2. Android uygulaması

1. Android Studio → **Open** → `HostyTalk/android` klasörünü seçin, Gradle senkronunu bekleyin.
2. Emülatörde sunucu `http://10.0.2.2:8000` adresindedir. Bu adres engelliyse `adb reverse tcp:8000 tcp:8000` çalıştırıp uygulamada sunucu adresini `http://127.0.0.1:8000` yapın. Gerçek telefonda giriş ekranındaki **⚙ Sunucu adresi** bölümüne bilgisayarın IP'sini ya da HTTPS tünel adresini girin.
3. Çalıştırın, hesap oluşturun; ikinci bir cihazda (ya da tarayıcıda) başka bir hesapla katılın.

Ekranlar:
- **Giriş / Kayıt** — e-posta, şifre, ad
- **Ana sayfa** — aylık dakika kullanımı, konuştuğum dil, uygulama dili, **Konuşma başlat**, **Konuşmaya katıl**
- **Konuşma** — başlatanda katılım kodu + QR; iki dilde canlı döküm, sesli asistan tarzı mikrofon (dokun → konuş → susunca gönderilir), yazarak mesaj, özetle bitirme ve paylaşma
- **Geçmiş konuşmalar** — karşı tarafın adı, döküm ve özet; sonradan özet oluşturma, paylaşma (WhatsApp vb.), silme (yalnızca sizin geçmişinizden)

Uygulama dili cihaz dilini izler; uygulama içinden de değiştirilebilir (Arapça seçildiğinde arayüz sağdan sola döner).

## API özeti

| Uç nokta | Açıklama |
|---|---|
| `POST /api/auth/register`, `/api/auth/login` | Hesap, JWT döner |
| `GET /api/me`, `PATCH /api/me` | Hesap + aylık kullanım, ad değiştirme |
| `POST /api/sessions` | Konuşma başlat: 6 haneli kod + QR adresi (30 dk geçerli) |
| `POST /api/sessions/join` | Kodla katıl (giriş gerekli; konuşma iki kişilik) |
| `GET /api/sessions/code/{code}` | Davet eden kişinin adı (katılım sayfası için) |
| `GET /j/{code}` | Web katılım sayfası (QR bunu açar) |
| `GET /api/conversations`, `GET/DELETE /api/conversations/{id}` | Konuşma geçmişi (isteyenin gözünden) |
| `POST /api/conversations/{id}/summary` | Sonradan özet oluştur |
| `WS /ws/{id}?role=host|guest&token=...` | Canlı konuşma |

WebSocket'e ikili (binary) mesaj olarak 16 kHz mono WAV cümle gönderilir; JSON mesajlar: `text`, `set_lang`, `end`. Sunucu `state`, `processing`, `message` (dinleyene `audio_b64` ile), `summary`, `error`, `ended` olaylarını yollar.

## Henüz yapılmayanlar

- Ödeme / abonelik (yalnızca deneme kotası takip ediliyor)
- İkiden fazla kişilik grup konuşması
- Oturumlar sunucu belleğinde tutulur; birden fazla sunucuya ölçeklemek için Redis gibi bir paylaşımlı depo gerekir
