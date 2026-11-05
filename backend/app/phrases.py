"""İşletme türüne göre hazır cümleler (5 dilde).

Esnaf kendi dilindeki listeyi görür; tek dokunuşla cümle turistin diline çevrilip seslendirilir.
Esnafın kendi eklediği cümleler veritabanında tutulur ve bu listenin önünde gösterilir.
"""

BUSINESS_TYPES = ["hotel", "restaurant", "shop", "taxi", "guide", "car_rental", "other"]

_COMMON = {
    "tr": ["Hoş geldiniz!", "Nasıl yardımcı olabilirim?", "Bir dakika lütfen.", "Teşekkür ederiz, yine bekleriz!"],
    "en": ["Welcome!", "How can I help you?", "One moment, please.", "Thank you, come again!"],
    "ar": ["أهلاً وسهلاً!", "كيف يمكنني مساعدتك؟", "لحظة من فضلك.", "شكراً لك، نتمنى رؤيتك مجدداً!"],
    "fr": ["Bienvenue !", "Comment puis-je vous aider ?", "Un instant, s'il vous plaît.", "Merci, à bientôt !"],
    "ru": ["Добро пожаловать!", "Чем могу помочь?", "Одну минуту, пожалуйста.", "Спасибо, приходите ещё!"],
}

_BY_TYPE = {
    "hotel": {
        "tr": ["Rezervasyonunuz hangi isimle?", "Pasaportunuzu alabilir miyim?", "Kahvaltı 07:00–10:30 arası.",
               "Odanız 3. katta, asansör sağda.", "Wi-Fi şifresi kartın arkasında.", "Çıkış saati 12:00."],
        "en": ["What name is the reservation under?", "May I have your passport, please?",
               "Breakfast is from 7:00 to 10:30.", "Your room is on the 3rd floor, the elevator is on the right.",
               "The Wi-Fi password is on the back of the card.", "Check-out is at 12:00."],
        "ar": ["باسم من الحجز؟", "هل يمكنني أخذ جواز سفرك؟", "الإفطار من 7:00 إلى 10:30.",
               "غرفتك في الطابق الثالث، والمصعد على اليمين.", "كلمة مرور الواي فاي على ظهر البطاقة.",
               "موعد المغادرة الساعة 12:00."],
        "fr": ["À quel nom est la réservation ?", "Puis-je avoir votre passeport ?",
               "Le petit-déjeuner est servi de 7h00 à 10h30.",
               "Votre chambre est au 3e étage, l'ascenseur est à droite.",
               "Le mot de passe Wi-Fi est au dos de la carte.", "Le départ est à 12h00."],
        "ru": ["На чьё имя бронирование?", "Можно ваш паспорт?", "Завтрак с 7:00 до 10:30.",
               "Ваш номер на 3-м этаже, лифт справа.", "Пароль от Wi-Fi на обороте карточки.", "Выезд в 12:00."],
    },
    "restaurant": {
        "tr": ["Kaç kişisiniz?", "Buyurun, menümüz.", "Bugünün önerisi bu.", "Alerjiniz var mı?",
               "Bu yemek acılı.", "Hesabı getireyim mi?"],
        "en": ["How many people?", "Here is our menu.", "This is today's special.", "Do you have any allergies?",
               "This dish is spicy.", "Shall I bring the bill?"],
        "ar": ["كم عددكم؟", "تفضل، هذه قائمة الطعام.", "هذا طبق اليوم المميز.", "هل لديك أي حساسية؟",
               "هذا الطبق حار.", "هل أحضر لك الحساب؟"],
        "fr": ["Vous êtes combien ?", "Voici notre menu.", "Voici le plat du jour.", "Avez-vous des allergies ?",
               "Ce plat est épicé.", "Je vous apporte l'addition ?"],
        "ru": ["Сколько вас?", "Вот наше меню.", "Это блюдо дня.", "У вас есть аллергия?",
               "Это блюдо острое.", "Принести счёт?"],
    },
    "shop": {
        "tr": ["Bu el yapımı.", "Fiyatı bu, ama indirim yapabilirim.", "Kartla ödeme yapabilirsiniz.",
               "Kargo dahil, ülkenize gönderiyoruz.", "Başka renkleri de var.",
               "Fatura kesiyoruz, vergi iadesi alabilirsiniz."],
        "en": ["This is handmade.", "This is the price, but I can give you a discount.", "You can pay by card.",
               "Shipping is included, we deliver to your country.", "It also comes in other colors.",
               "We issue an invoice, you can get a tax refund."],
        "ar": ["هذا مصنوع يدوياً.", "هذا هو السعر، لكن يمكنني أن أعطيك خصماً.", "يمكنك الدفع بالبطاقة.",
               "الشحن مشمول، نرسل إلى بلدك.", "متوفر بألوان أخرى أيضاً.",
               "نصدر فاتورة، ويمكنك استرداد الضريبة."],
        "fr": ["C'est fait à la main.", "Voici le prix, mais je peux vous faire une remise.",
               "Vous pouvez payer par carte.", "La livraison est incluse, nous envoyons dans votre pays.",
               "Il existe aussi en d'autres couleurs.", "Nous faisons une facture, vous pouvez obtenir la détaxe."],
        "ru": ["Это ручная работа.", "Вот цена, но я могу сделать скидку.", "Можно оплатить картой.",
               "Доставка включена, отправляем в вашу страну.", "Есть и другие цвета.",
               "Мы выдаём чек, вы можете вернуть налог."],
    },
    "taxi": {
        "tr": ["Nereye gitmek istiyorsunuz?", "Taksimetreyle gidiyoruz.", "Yaklaşık 20 dakika sürer.",
               "Emniyet kemerinizi takın lütfen.", "Burada inebilir misiniz?", "Bagajınızı ben alayım."],
        "en": ["Where would you like to go?", "We go by the meter.", "It takes about 20 minutes.",
               "Please fasten your seat belt.", "Can you get out here?", "Let me take your luggage."],
        "ar": ["إلى أين تريد الذهاب؟", "نسير حسب العداد.", "تستغرق الرحلة حوالي 20 دقيقة.",
               "يرجى ربط حزام الأمان.", "هل يمكنك النزول هنا؟", "دعني أحمل أمتعتك."],
        "fr": ["Où souhaitez-vous aller ?", "Nous roulons au compteur.", "Cela prend environ 20 minutes.",
               "Attachez votre ceinture, s'il vous plaît.", "Pouvez-vous descendre ici ?", "Je prends vos bagages."],
        "ru": ["Куда вы хотите поехать?", "Едем по счётчику.", "Дорога займёт около 20 минут.",
               "Пристегните, пожалуйста, ремень.", "Можете выйти здесь?", "Давайте я возьму ваш багаж."],
    },
    "guide": {
        "tr": ["Lütfen grubu takip edin.", "Burada 15 dakika serbest zaman var.",
               "Saat 14:00'te otobüste buluşuyoruz.", "Burada fotoğraf çekmek yasak.",
               "Camiye girerken ayakkabılar çıkarılır.", "Sorusu olan var mı?"],
        "en": ["Please follow the group.", "You have 15 minutes of free time here.", "We meet at the bus at 14:00.",
               "Taking photos is not allowed here.", "Shoes are taken off when entering the mosque.",
               "Does anyone have a question?"],
        "ar": ["يرجى اتباع المجموعة.", "لديكم 15 دقيقة من الوقت الحر هنا.", "نلتقي عند الحافلة الساعة 14:00.",
               "التصوير ممنوع هنا.", "تُخلع الأحذية عند دخول المسجد.", "هل لدى أحد سؤال؟"],
        "fr": ["Veuillez suivre le groupe.", "Vous avez 15 minutes de temps libre ici.",
               "Rendez-vous au bus à 14h00.", "Les photos sont interdites ici.",
               "On enlève ses chaussures pour entrer dans la mosquée.", "Quelqu'un a-t-il une question ?"],
        "ru": ["Пожалуйста, следуйте за группой.", "Здесь у вас 15 минут свободного времени.",
               "Встречаемся у автобуса в 14:00.", "Здесь фотографировать запрещено.",
               "При входе в мечеть снимают обувь.", "У кого-нибудь есть вопросы?"],
    },
    "car_rental": {
        "tr": ["Ehliyetinizi görebilir miyim?", "Depo dolu teslim ediliyor, dolu iade edin.",
               "Sigorta fiyata dahil.", "Depozito için kredi kartı gerekiyor.",
               "Aracı saat 10:00'a kadar iade etmelisiniz.", "Bir sorun olursa bu numarayı arayın."],
        "en": ["May I see your driving licence?", "The tank is full, please return it full.",
               "Insurance is included in the price.", "A credit card is required for the deposit.",
               "You must return the car by 10:00.", "If there is any problem, call this number."],
        "ar": ["هل يمكنني رؤية رخصة القيادة؟", "الخزان ممتلئ، يرجى إعادته ممتلئاً.", "التأمين مشمول في السعر.",
               "مطلوب بطاقة ائتمان للتأمين المالي.", "يجب إعادة السيارة قبل الساعة 10:00.",
               "إذا حدثت أي مشكلة، اتصل بهذا الرقم."],
        "fr": ["Puis-je voir votre permis de conduire ?", "Le réservoir est plein, merci de le rendre plein.",
               "L'assurance est incluse dans le prix.", "Une carte de crédit est nécessaire pour la caution.",
               "Vous devez rendre la voiture avant 10h00.", "En cas de problème, appelez ce numéro."],
        "ru": ["Можно ваше водительское удостоверение?", "Бак полный, верните, пожалуйста, с полным.",
               "Страховка включена в цену.", "Для залога нужна кредитная карта.",
               "Машину нужно вернуть до 10:00.", "Если возникнут проблемы, звоните по этому номеру."],
    },
    "other": {
        "tr": ["Fiyatı bu.", "Kartla ödeme yapabilirsiniz.", "Nakit de kabul ediyoruz.", "Fotoğraf çekebilirsiniz."],
        "en": ["This is the price.", "You can pay by card.", "We also accept cash.", "You can take photos."],
        "ar": ["هذا هو السعر.", "يمكنك الدفع بالبطاقة.", "نقبل الدفع نقداً أيضاً.", "يمكنك التقاط الصور."],
        "fr": ["Voici le prix.", "Vous pouvez payer par carte.", "Nous acceptons aussi les espèces.",
               "Vous pouvez prendre des photos."],
        "ru": ["Вот цена.", "Можно оплатить картой.", "Наличные тоже принимаем.", "Можно фотографировать."],
    },
}


def defaults(business_type: str, lang: str) -> list[str]:
    by_lang = _BY_TYPE.get(business_type, _BY_TYPE["other"])
    return _COMMON.get(lang, _COMMON["en"]) + by_lang.get(lang, by_lang["en"])
