# Jarvis (Android)

Jarvis, cihazındaki **Termux → proot-distro (Ubuntu) → opencode CLI** zincirini
kullanarak çalışan bir sohbet asistanıdır. Uygulama içi sohbet ekranından yazdığın
mesaj Termux'a gönderilir, Ubuntu içinde `opencode run` çalıştırılır ve yanıt Geri
getirilir. opencode kendi hafızasını (memory.md) Ubuntu içinde tuttuğu için
"derin hafıza" kalıcıdır ve `opencode run --continue` ile konuşma sürer.

APK **GitHub Actions** ile derlenir (local build gerekmez).

## GitHub Actions ile build

1. Bu repoyu GitHub'a push et.
2. **Actions** sekmesinden `Build Jarvis APK` workflow'unu çalıştır (ya da `main`'e push et).
3. Bitince **Artifacts → jarvis-apk** içinden `app-debug.apk`'yi indir ve yükle.

> compileSdk 36 (Android 16) için AGP ≥ 8.11 / Gradle ≥ 8.13 gerekiyor.
> CI sürüm uyuşmazlığı hatası verirse `build.gradle.kts` ve
> `gradle/wrapper/gradle-wrapper.properties` içindeki sürümleri güncelle.

## Telefonda kurulum (bir kez)

Jarvis'in çalışması için telefonunda şunlar gerekiyor:

### 1. Termux + Termux:API
F-Droid'ten `Termux` ve `Termux:API` uygulamalarını kur.
Play Store'daki Termux eski; F-Droid veya GitHub releases kullan.

### 2. Termux dış uygulama izni
Termux içinde:
```bash
echo "allow-external-apps = true" >> ~/.termux/termux.properties
```
Sonra Termux'u tamamen kapat-aç (veya `killall termux`).

### 3. Jarvis'e RUN_COMMAND izni ver
Android'de: **Ayarlar → Uygulamalar → Jarvis → İzinler** ve
"Termux'ta komut çalıştır" (run commands in Termux) iznini etkinleştir.
(Bu izin manifest'te tanımlı; kullanıcının elle vermesi gerekir.)

### 4. proot Ubuntu
Termux içinde:
```bash
pkg update && pkg install proot-distro
proot-distro install ubuntu
proot-distro login ubuntu
```
Ubuntu kabuğundayken opencode'u kur ve modeli ayarla:
```bash
# Ubuntu içinde
curl -fsSL https://bun.sh/install | bash   # veya node kur
npm install -g opencode
```
Model: **`opencode/hy3-free`** (ucretsiz). `~/jarvis` proje dizininde opencode
config'i oluştur (`opencode run` bu dizinde çalışır):
```bash
mkdir -p ~/jarvis
cat > ~/jarvis/opencode.jsonc <<'EOF'
{
  "$schema": "https://opencode.ai/config.json",
  "model": "opencode/hy3-free"
}
EOF
```
Eğer free model kimlik doğrulama isterse `OPENCODE_API_KEY`'i ayarla
(verilen anahtarı `~/.bashrc`'e ekle) ve opencode'a giriş yap:
```bash
export OPENCODE_API_KEY=sk-...
echo 'export OPENCODE_API_KEY=sk-...' >> ~/.bashrc
```
opencode'u `opencode` olarak PATH'te göründüğünden emin ol
(`command -v opencode` ile kontrol et).

> Not: App komutu zaten `-m opencode/hy3-free` içerir (bkz. `OpencodeCommand.MODEL`).
> Farkli model istersen ya bu sabiti ya da yukaridaki `opencode.jsonc` model alanini degistir.

### 5. Çalıştır
Jarvis'i aç, mesaj yaz. İlk mesajda Termux otomatik açılır.
Üstteki **Durum** butonu kurulumu doğrular (Termux, izin, opencode yolu).

## Nasıl çalışır
- `ChatViewModel.send()` → `OpencodeCommand.build(msg)` bir shell komutu üretir:
  `mkdir -p ~/jarvis && proot-distro login ubuntu -- bash -lc 'cd ~/jarvis && opencode run --continue --format json -q -- "<msg>"'`
- Komut `TermuxSession.run()` ile Termux `RUN_COMMAND` intent'ine gönderilir.
- Termux komutu Ubuntu içinde çalıştırır, opencode yanıtı JSON olarak basar.
- `ResultService` sonucu alır, `OutputParser` metni çıkarır, sohbete eklenir.
- `~/jarvis` dizini opencode oturumunu izole tutar; hafıza Ubuntu içinde kalıcıdır.

## Notlar
- Uzun opencode görevleri için 5 dakikalık zaman aşımı var (`TermuxSession.run`).
- Sohbet geçmişi cihazda (`chat.json`) tutulur.
- opencode'un kendi hafıza sistemi (memory.md) "sahibin kim", "yemek siparişi"
  gibi kalıcı bilgileri hatırlar; Jarvis'e bir kez söylemen yeterli.

## opencode sürüm uyarıları
- `opencode` **>= 1.17** sürümünde `run` komutu artık `--format json` ve `-q`
  bayraklarını **kabul etmiyor**. Bu yüzden app varsayılan metin çıktısını
  kullanır; `OutputParser` hem JSON (eski sürümler) hem de ANSI'siz düz metni
  parse eder. Ubuntu'da opencode'u güncel tut.

## Test sistemi (APK gerektirmez)
`test/` dizininde, app'in komut-üretim ve parse mantığını birebir çalıştıran
bir harness var. Gerçek model gerektirmez; sahte bir `opencode` beyni kullanır.

```bash
cd test
python3 harness.py
```

Doğrular:
1. Shell-escape (`shQuote`) — çift tırnak, tek tırnak, `\n`, `$`, backtick,
   komut enjeksiyonu girişimlerine karşı güvenli mi?
2. Üretilen komutun `bash -lc` ile gerçek çalışması (sahte beyin üstünden).
3. Hafıza / `--continue` — ikinci çağrı ilk mesajda öğrenilen adı hatırlıyor.
4. opencode JSONL çıktısının parse edilmesi (eski sürümler için).

