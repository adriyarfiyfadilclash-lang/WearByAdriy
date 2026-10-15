# WearByAdriy

Aplikasi Android (Bahasa Indonesia) untuk mencatat isi lemari, menyusun outfit, dan meminta saran gaya — dengan akun sungguhan, database cloud, dan foto privat.

- **Home** — cuaca kota pilihan, outfit hari ini dari isi lemari, look terbaru.
- **Other Looks** — semua look tersimpan: favorit, riwayat dipakai, ganti item, ubah nama, hapus. Bisa menyusun look manual.
- **Wardrobe** — tambah pakaian dengan foto (galeri/kamera), kategori, warna, gaya, acara, catatan, status tersedia/favorit.
- **Style** — buat 3 outfit dari lemarimu, dengan **AI** (OpenAI, Google Gemini, atau Anthropic Claude memakai API key milik pengguna) atau **otomatis** (aturan warna/gaya/cuaca, tanpa AI). Hasil AI divalidasi: item yang tidak ada di lemari dibuang, dan jika AI gagal aplikasi menampilkan pesan gagal — tidak pernah hasil palsu.
- **Pengaturan** (ikon profil di Home) — profil & preferensi, kota cuaca, API AI, tema, ganti password, keluar, hapus akun.

## Arsitektur

| Bagian | Teknologi |
|---|---|
| App | Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coroutines |
| Auth | Supabase Auth (email + password, PKCE, tautan `wearbyadriy://login-callback`) |
| Database | Supabase Postgres + Row Level Security per `user_id` |
| Foto | Supabase Storage bucket privat `wardrobe`, path `<user_id>/<uuid>.jpg`, diakses lewat signed URL 1 jam |
| Hapus akun | Edge Function `delete-account` (service role hanya di server) |
| AI | Dipanggil langsung dari ponsel dengan key pengguna; key disimpan di `EncryptedSharedPreferences` (Android Keystore), per akun, tidak dicadangkan |
| Cuaca | Open-Meteo (tanpa key) |

Keamanan sesi: saat keluar, state aplikasi, cache signed URL, dan cache gambar di memori dihapus; gambar tidak pernah di-cache ke disk. Layar privat tidak dirender sama sekali selama tidak ada sesi.

## Skema database

`supabase/migrations/20261016000001_init.sql`:

- `profiles` (1 baris per user, dibuat otomatis oleh trigger saat daftar)
- `wardrobe_items` (pakaian; `image_path` wajib di folder milik user)
- `looks` + `look_items` (relasi look–item; trigger memastikan look dan item milik user yang sama)
- `ai_generations` (log metadata saja: penyedia, model, status — tanpa prompt, tanpa key)
- Bucket `wardrobe` privat, maks 5 MB, hanya JPEG/PNG/WebP, policy per folder user.

Migrasi tidak menghapus tabel/data apa pun.

## Setup

### 1. Supabase
1. Buat project Supabase.
2. Jalankan migrasi: `supabase link --project-ref <ref>` lalu `supabase db push` (atau tempel isi file SQL di SQL Editor).
3. Deploy fungsi: `supabase functions deploy delete-account`.
4. **Authentication → URL Configuration → Redirect URLs**: tambahkan `wearbyadriy://login-callback**`.
5. (Disarankan) Authentication → Providers → Email: aktifkan *Confirm email*; atur panjang password minimal 8.
6. Catat **Project URL** dan **anon / publishable key** (Settings → API). **Jangan** pakai service_role key di aplikasi.

### 2. Build lokal
```bash
cp local.properties.example local.properties   # isi sdk.dir, SUPABASE_URL, SUPABASE_ANON_KEY
./gradlew testDebugUnitTest assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Build lewat GitHub Actions
Repo → Settings → Secrets and variables → Actions → tambahkan `SUPABASE_URL` dan `SUPABASE_ANON_KEY`.
Setiap push ke `main` menjalankan unit test, build APK debug, memverifikasi isinya, lalu mengunggahnya ke pre-release **`debug-latest`** (bagian Releases). Jika build gagal, ringkasan error disimpan di branch `ci-logs`.

Tanpa secret, APK tetap terbuat tetapi hanya menampilkan layar "belum terhubung ke server".

### 4. Instal di ponsel
Unduh `WearByAdriy-debug.apk` dari Releases → buka di Android → izinkan "Instal dari sumber ini". Butuh Android 8.0+.

## Rilis (APK release bertanda tangan)
1. Buat keystore **di luar repo**: `keytool -genkey -v -keystore wearbyadriy.jks -keyalg RSA -keysize 4096 -validity 10000 -alias wearbyadriy`.
2. Set env `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, lalu `./gradlew assembleRelease`.
3. Naikkan `versionCode`/`versionName` di `app/build.gradle.kts` setiap rilis.
4. Jangan pernah commit keystore atau passwordnya.

## Keterbatasan yang diketahui
- Virtual try-on dan pembuatan gambar AI **belum** diimplementasikan, sehingga tidak ditampilkan di aplikasi.
- AI dipanggil dari ponsel dengan key milik pengguna; daftar pakaian (nama, kategori, warna, tag — tanpa foto) dikirim ke penyedia AI yang dipilih.
- Aplikasi butuh internet untuk membaca dan menyimpan data (tidak ada mode offline dengan sinkronisasi).
- Lokasi dipakai sekali (perkiraan kasar) hanya untuk memilih kota cuaca.
