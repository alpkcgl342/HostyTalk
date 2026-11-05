# HostyTalk

> Yapay zeka destekli, turizm odaklı gerçek zamanlı sesli tercüman.
> Esnaf konuşur, turist kendi dilinde duyar. Turistin hiçbir şey indirmesine gerek yok.

---

## 1. Proje Özeti

HostyTalk, farklı diller konuşan iki kişinin sesli olarak, gerçek zamanlı ve doğal biçimde anlaşmasını sağlayan bir Android uygulamasıdır. Bir taraf konuşurken uygulama konuşmayı karşı tarafın diline çevirip seslendirir; tersi de aynı şekilde çalışır.

Uygulama genel amaçlı bir çeviri aracı değil, **turizm sektörüne özel** bir üründür. Ana müşteri, turistlerle iletişim kuran **esnaf ve işletmelerdir**.

## 2. Problem

- Türkiye'deki esnaf, otel, restoran, rehber ve taksiciler her gün farklı dillerde turistlerle iletişim kurmak zorunda.
- Mevcut çözümler yetersiz:
  - **Google Translate konuşma modu:** Sırayla konuşmayı ve butona basmayı gerektirdiği için hantal.
  - **Samsung Galaxy AI / Google Pixel Live Translate:** Yalnızca belirli telefon modellerinde, belirli dillerde ve bölgelerde çalışıyor.
  - Hiçbiri işletmeye özel bilgileri (menü, ürün, fiyat) bilmiyor ve konuşma sonrasında kayıt ya da özet sunmuyor.

## 3. Çözüm

Esnafın telefonunda ya da tabletinde çalışan bir Android uygulaması ve turistin **QR kod okutarak tarayıcıdan** katıldığı, hesap gerektirmeyen bir web sayfası.

## 4. Hedef Kitle

| Taraf | Kim | Rolü |
|---|---|---|
| **Ödeyen müşteri (B2B)** | Otel, restoran, çarşı esnafı, tur rehberi, taksici, araç kiralama | Uygulamayı kurar, konuşmayı başlatır, abonelik öder |
| **Misafir kullanıcı** | Türkiye'ye gelen yabancı turistler | QR okutur, tarayıcıdan katılır, ücretsizdir |

**Kapsam dışı:** Sağlık ve hukuk gibi yüksek sorumluluk gerektiren alanlar bilinçli olarak hedeflenmemektedir.

## 5. Samsung ve Google'dan Farkımız

1. **Marka bağımsız:** Her Android cihazda çalışır; turistin telefonu iPhone dahil ne olursa olsun katılabilir.
2. **İndirme gerektirmez:** Turist QR okutup tarayıcıdan bağlanır.
3. **Esnaf hafızası:** İşletmenin menüsünü, ürünlerini ve özel isimlerini öğrenir, hep doğru çevirir.
4. **Turizme özel araçlar:** Pazarlık modu, iki para birimli fiyat gösterimi, hazır kalıp cümleler.
5. **Konuşma özeti:** Görüşme sonunda iki dilde "neyde anlaştık" notu üretir.
6. **Duygu ve kültür aktarımı:** Tonu korur, gerektiğinde kısa kültürel ipuçları verir.

## 6. Temel Akış: "Tezgâh Modu"

1. Esnaf uygulamayı açar ve **yeni konuşma** başlatır. Ekranda bir QR kod belirir.
2. Turist QR'ı okutur. Tarayıcıda dili otomatik algılanır.
3. Esnaf Türkçe konuşur, turist kendi dilinde duyar ve okur. Tersi de aynı şekilde çalışır.
4. Konuşma bitince isteğe bağlı olarak iki dilde özet oluşturulur.
   - Örnek: *"2 adet halı, toplam 450 €, kargo dahil."*

## 7. Özellikler

### MVP (İlk sürüm)
- [ ] Esnaf hesabı ve giriş
- [ ] Konuşma başlatma ve QR kod üretme
- [ ] Turist web katılım sayfası (hesapsız)
- [ ] Gerçek zamanlı sesten sese çeviri (iki yönlü)
- [ ] Ekranda canlı metin dökümü (iki dilde)
- [ ] Otomatik dil algılama
- [ ] Hazır kalıp cümleler (tek dokunuşla seslendirme)
- [ ] İki para birimli fiyat gösterimi (TL ↔ €, $, £)
- [ ] 6 dil: Türkçe, İngilizce, Almanca, Rusça, Arapça, Fransızca

### v2
- [ ] Esnaf hafızası (menü, ürün ve fiyat listesi yükleme)
- [ ] Konuşma özeti (iki dilde)
- [ ] Pazarlık modu
- [ ] Gürültü filtresi ve bas-konuş modu
- [ ] Birden fazla çalışan hesabı
- [ ] İşletme logolu, basılabilir QR standı

### v3
- [ ] Rehber modu (bir konuşmacı, çok dilli dinleyici grubu)
- [ ] Ses klonlama (esnafın kendi sesiyle çeviri)
- [ ] Kültürel ipuçları ve ton aktarımı
- [ ] Ek diller
- [ ] Çevrimdışı temel mod

## 8. Kötüye Kullanım Önlemleri

**Sorun:** Esnaf ücretli hesap yerine ücretsiz turist tarafını kullanabilir mi?

**Çözüm:**
- Turist tarafında **hesap yoktur**; turist yalnızca esnafın başlattığı oturuma misafir olarak katılır.
- Konuşmayı **sadece esnaf hesabı başlatabilir**. Maliyet her zaman başlatan hesaptan düşer.
- QR kodlar **oturuma özeldir** ve kısa sürede geçersiz olur (ör. 30 dakika).
- İleride bağımsız bir turist modu eklenirse:
  - Ücretsiz katman günlük dakika ile sınırlı olur.
  - Esnafa özel özellikler turist tarafında yer almaz.
  - Şüpheli kullanım desenleri (aynı konum, uzun süre, çok sayıda dil) tespit edilip işletme hesabına geçiş önerilir.
- Fiyatlandırma, hile yapmayı zahmetli, ödemeyi kolay kılacak şekilde belirlenir.

## 9. Gelir Modeli

| Paket | Hedef | İçerik |
|---|---|---|
| **Deneme** | Yeni esnaf | İlk ay ücretsiz, sınırlı dakika |
| **Esnaf** | Küçük işletme | Aylık abonelik, aylık dakika kotası |
| **İşletme** | Otel, rehber, zincir | Daha yüksek kota, çoklu çalışan, özel hafıza |
| **Turist** | Misafir | Her zaman ücretsiz |

> Not: Çeviri API'leri dakika başına ücretlendirildiği için abonelik fiyatları API maliyetine göre hesaplanmalıdır. Bu hesap MVP sonrasında gerçek kullanım verisiyle netleştirilecek.

## 10. Teknik Mimari

```
┌────────────────────┐        ┌──────────────────────┐        ┌────────────────────┐
│  Esnaf Android     │◄──────►│  Backend Sunucu      │◄──────►│  Turist Web Sayfası │
│  (Kotlin+Compose)  │ WebRTC │  - Oturum / QR       │ WebRTC │  (Tarayıcı, PWA)    │
│                    │  / WS  │  - Kimlik doğrulama  │  / WS  │                    │
└────────────────────┘        │  - Kota takibi       │        └────────────────────┘
                              │  - Çeviri API köprüsü│
                              └──────────┬───────────┘
                                         │
                              ┌──────────▼───────────┐
                              │ Gerçek zamanlı sesli  │
                              │ çeviri API'si         │
                              └──────────────────────┘
```

### Bileşenler
- **Android uygulaması:** Kotlin, Jetpack Compose
- **Turist sayfası:** Hafif bir web uygulaması (mikrofon erişimi, ses çalma, canlı metin)
- **Backend:** Oturum yönetimi, QR üretimi, kimlik doğrulama, kota ve ödeme takibi
- **Gerçek zamanlı iletişim:** WebRTC ve/veya WebSocket
- **Çeviri motoru (değerlendirilecek seçenekler):**
  - OpenAI `gpt-realtime-translate` (Realtime API, akışlı sesten sese çeviri)
  - Palabra.ai (düşük gecikme, ses klonlama)
  - DeepL Voice-to-Voice API
  - Alternatif: Whisper (konuşmadan metne) + LLM (çeviri) + TTS (metinden sese) zinciri

> Seçim yapılırken gecikme, dil desteği (özellikle Türkçe çıkış), dakika maliyeti ve ses kalitesi karşılaştırılmalıdır.

## 11. Yol Haritası

| Aşama | Süre (tahmini) | Hedef |
|---|---|---|
| **0. Hazırlık** | 1 hafta | İsim ve marka kontrolü, API seçimi ve maliyet testi |
| **1. MVP** | 4–6 hafta | Tezgâh modu, QR ile katılım, 6 dil |
| **2. Pilot** | 2 hafta | Gerçek bir işletmede (otel, restoran ya da çarşı esnafı) saha testi |
| **3. v2** | 4–6 hafta | Esnaf hafızası, özetler, pazarlık modu, abonelik |
| **4. Yayın** | — | Google Play yayını, ilk müşteriler |
| **5. v3** | — | Rehber modu, ses klonlama, yeni diller |

## 12. Yapılacaklar (Kısa Vade)

- [ ] "HostyTalk" ismini Google Play'de kontrol et
- [ ] hostytalk.com / hostytalk.app alan adlarını kontrol et
- [ ] TÜRKPATENT ve EUIPO'da marka araştırması yap
- [ ] Çeviri API'lerini Türkçe ↔ İngilizce/Almanca/Rusça için test et (gecikme + kalite + maliyet)
- [ ] Ekran tasarımlarını hazırla (esnaf uygulaması + turist katılım sayfası)
- [ ] Pilot test için bir işletme bul
- [ ] Slogan ve logo çalışması

## 13. Marka Notları

- **İsim:** HostyTalk (bitişik, iki büyük harf)
- **Anlam:** "Host" (ev sahibi) + "Talk" (konuşma). Esnafı turistin ev sahibi olarak konumlar.
- **Logo önerisi:** "Hosty" kısmı öne çıkarılır, "Talk" daha küçük yazılır.
- **Kültürel not:** Quebec Fransızcasında "hostie" kaba bir ifadedir. Hedef kitle açısından büyük risk oluşturmaz, ancak bilinmesinde fayda var.
