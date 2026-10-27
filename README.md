# IPTV Oynatıcı (Android TV / TV box / telefon)

Xtream Codes uyumlu IPTV aboneliğiniz için basit bir oynatıcı:

- Sunucu adresi + kullanıcı adı + şifre ile giriş (bilgiler cihazda saklanır)
- Kanalları sunucudan otomatik çeker, kategorilere ayırır
- Kanal arama
- Favori ekleme (yıldıza basın) ve "Favoriler" sekmesi
- Media3 / ExoPlayer ile donanımsal kod çözme, akıcı oynatma için ayarlı tampon
  ve yayın kesilince otomatik yeniden bağlanma
- Hem uzaktan kumanda (D-pad) hem dokunmatik ile kullanılabilir

> Not: Uygulama yalnızca bir oynatıcıdır. Herhangi bir kanal veya içerik
> barındırmaz; yalnızca sizin girdiğiniz kendi aboneliğinize bağlanır.

---

## APK nasıl alınır? (Bilgisayara hiçbir şey kurmadan)

Derleme işini ücretsiz GitHub bulutu yapar. Adımlar:

1. **Ücretsiz bir GitHub hesabı açın:** https://github.com/signup
2. Sağ üstten **New repository** → bir isim verin (örn. `iptv-oynatici`) →
   **Create repository**.
3. Açılan sayfada **uploading an existing file** bağlantısına tıklayın.
4. Bu klasördeki **tüm dosya ve klasörleri** (gizli `.github` klasörü dahil)
   sürükleyip bırakın, sonra **Commit changes**.
   - En kolayı: size verilen `iptv-player.zip` dosyasını açıp içindekilerin
     tamamını yükleyin.
5. Üstteki **Actions** sekmesine geçin. "APK Derle" işi kendiliğinden başlar
   (ilk sefer onay istenirse **I understand my workflows, enable them**'e basın).
   Yeşil tik gelene kadar ~3–5 dakika bekleyin.
6. APK'yı iki yerden indirebilirsiniz:
   - **Actions** → ilgili çalışma → en altta **Artifacts → IPTV-Oynatici-APK**, veya
   - Sağdaki **Releases → latest → IPTV-Oynatici.apk** (TV'nin tarayıcısından
     da açılabilen kalıcı link).

Kod değiştikçe her yüklemede APK otomatik yeniden derlenir.

---

## TV box / Android TV'ye kurma

1. APK dosyasını cihaza ulaştırın (en kolayı: cihazın tarayıcısında Releases
   linkini açıp indirmek; ya da bir USB bellek, ya da "Send Files to TV" gibi
   bir uygulama).
2. **Ayarlar → Uygulamalar → Güvenlik / Bilinmeyen kaynaklar**'dan, dosyayı
   açacağınız uygulamaya (tarayıcı veya dosya yöneticisi) izin verin.
3. APK'ya tıklayıp **Kur** deyin.
4. Uygulama, ana ekranda "IPTV Oynatıcı" olarak görünür.

İlk açılışta sunucu adresinizi (`http://adres:port`), kullanıcı adı ve
şifrenizi girin. Giriş bir kez yapılır; sonraki açılışlarda doğrudan kanal
listesi gelir. Çıkış yapmak için sağ üstteki düğmeyi kullanın
(favorileriniz korunur).

---

## Teknik notlar

- `minSdk 21` (Android 5.0) — eski TV box'lar dahil geniş uyumluluk
- Akış adresi varsayılan olarak `.ts` üretilir (en uyumlu). Sunucunuz yalnızca
  `.m3u8` veriyorsa `XtreamClient.liveUrl(...)` çağrısındaki `ext` değerini
  `"m3u8"` yapmanız yeterli.
- Yayın `http` (şifresiz) olabildiği için `usesCleartextTraffic` açıktır.
- İmzasız/debug imzalı APK'dır; kişisel kullanım ve yan yükleme (sideload) için
  uygundur. Google Play'e yüklemek isterseniz release imzalama eklenmelidir.
