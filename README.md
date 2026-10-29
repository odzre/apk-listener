# NotiPay Listener (Android)

Aplikasi Android untuk membaca notifikasi pembayaran dari aplikasi yang dipilih dan mengirimkannya sebagai callback ke website NotiPay.

Repository website: https://github.com/odzre/web-listener

## Fitur

- Checklist aplikasi yang dipantau (hanya notifikasi dari paket terpilih yang dikirim).
- URL callback dan API key merchant.
- Callback melalui WorkManager dengan retry otomatis saat jaringan bermasalah.
- Foreground service agar listener tetap berjalan saat aplikasi ditutup.
- Auto-start setelah reboot bila listener diaktifkan.
- Console log notifikasi lokal dari aplikasi yang dicentang, lengkap dengan status callback, salin, dan hapus log.
- Status akses notifikasi, status callback terakhir, dan tombol tes koneksi.
- Permintaan pengecualian optimasi baterai untuk vendor Android yang agresif.
- APK debug siap pasang di `release/notipay-listener-debug.apk`.

## Persyaratan

- JDK 17
- Android SDK 35
- Android Studio (disarankan) atau Gradle 8.9 (wrapper sudah disertakan)
- Android 8.0+ (minSdk 26)

## Build

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
```

Hasil build:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Perubahan versi **1.1.0**: console log notifikasi, status callback per log, salin/hapus log, dan perbaikan scroll daftar aplikasi.

APK yang sudah tersedia di repository:

```text
release/notipay-listener-debug.apk
```

Untuk release build, tambahkan signing config sendiri di `app/build.gradle.kts`.

## Cara Pakai

1. Install APK.
2. Buka aplikasi → **Buka** pada kartu **Akses notifikasi** → aktifkan **NotiPay Listener**.
3. Login ke website NotiPay → **Pengaturan**.
4. Salin **API key** dan **URL callback** ke aplikasi.
5. Centang aplikasi pembayaran yang ingin dipantau (DANA, GoPay, Touch 'n Go, dan lainnya).
6. Tekan **Tes koneksi callback**.
7. Aktifkan **Listener background**, lalu tekan **Simpan & jalankan**.
8. Nonaktifkan optimasi baterai jika callback tidak masuk pada perangkat tertentu.

Callback dikirim ke:

```text
POST https://domain-anda.com/api/v1/callback
```

Payload utama:

```json
{
  "apikey": "np_live_xxxxxxxxx",
  "package": "id.dana",
  "title": "DANA",
  "text": "RM116.00 diterima untuk -",
  "device_id": "android-01",
  "timestamp": "2026-10-29T02:16:40.000Z"
}
```

## Struktur Penting

```text
app/src/main/java/com/notipay/listener/
├── MainActivity.kt
├── NotificationListener.kt
├── KeeperService.kt
├── CallbackSender.kt
├── CallbackWorker.kt
├── BootReceiver.kt
├── AppListAdapter.kt
├── Prefs.kt
├── LogStore.kt
├── LogAdapter.kt
└── InstalledApp.kt
```

## Catatan Keamanan & Kompatibilitas

- API key disimpan di SharedPreferences perangkat. Gunakan perangkat yang tepercaya.
- `QUERY_ALL_PACKAGES` dipakai untuk menampilkan seluruh aplikasi. Publikasi ke Play Store memerlukan justifikasi kebijakan.
- Beberapa OEM (Xiaomi, Oppo, Vivo, Samsung) membatasi service latar belakang. Pengguna perlu mengizinkan autostart/background activity.
- Notifikasi pembayaran dapat mengandung data sensitif. Aplikasi hanya mengirim notifikasi dari paket yang dipilih.
- APK di `release/` adalah debug build untuk pengujian, bukan build production bertanda tangan resmi.
