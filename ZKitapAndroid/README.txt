Z-Kitap Görüntü Yakalama - Android Studio projesi

Özellikler:
- MediaProjection ile kullanıcı izniyle ekran yakalama
- Diğer uygulamaların üzerinde sürüklenebilir yeşil + simgesi
- Ana alan seçimi ve isteğe bağlı beyazlatılacak alan seçimi
- Beyaz boşluk otomatik kırpma
- Pictures/ZKitapYakalama klasörüne 001.jpg, 002.jpg ... kaydı

APK oluşturma:
1. Bilgisayara Android Studio (güncel kararlı sürüm) kurun.
2. Bu ZIP dosyasını çıkarın.
3. Android Studio > Open ile ZKitapAndroid klasörünü açın.
4. Gradle senkronizasyonunun bitmesini bekleyin.
5. Build > Build Bundle(s) / APK(s) > Build APK(s) seçin.
6. APK genellikle app/build/outputs/apk/debug/app-debug.apk yolunda oluşur.
7. APK'yı telefona aktarın ve kurulum izni verin.

İlk prototip notları:
- Android'in ekran yakalama izin penceresini onaylamak gerekir.
- Xiaomi cihazlarda Ayarlar > Uygulamalar > Özel izinler bölümünden diğer uygulamaların üzerinde gösterme izni verin.
- Bazı korumalı uygulamalar ekran yakalamayı engelleyebilir.
- Bu kaynak proje bu çalışma ortamında Android SDK/Gradle bulunmadığı için derlenmiş APK olarak test edilemedi. Gerçek cihazda deneme ve gerekirse düzeltme gerektirir.
