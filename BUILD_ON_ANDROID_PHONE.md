# Build APK hanya menggunakan HP Android

Cara paling mudah adalah memakai GitHub Actions (build berjalan di cloud, bukan di HP).

1. Ekstrak `PaketKuTracker-Android-Source.zip`.
2. Buat repository GitHub baru, misalnya `PaketKuTracker`, dan set sebagai **Private**.
3. Upload seluruh ISI folder `PaketKuTracker` ke root repository (jangan membuat folder induk `PaketKuTracker` di GitHub). Pastikan folder tersembunyi `.github/workflows/` ikut ter-upload, termasuk `android.yml`. Jika unggah lewat browser HP tidak menampilkan folder tersembunyi, aktifkan tampilan file tersembunyi atau gunakan GitHub web upload dengan memastikan `.github/workflows/android.yml` berada di root repository.
4. Buka tab **Actions** pada repository. Jika diminta, aktifkan Actions.
5. Pilih workflow **Build PaketKu Tracker APK** → **Run workflow** → **Run workflow**.
6. Tunggu sampai workflow selesai dengan tanda hijau.
7. Buka hasil run, cari **Artifacts**, lalu unduh `PaketKuTracker-debug-apk`.
8. Ekstrak ZIP artifact di HP, ketuk `app-debug.apk`, lalu izinkan instalasi dari sumber tersebut jika Android meminta.

Catatan:
- Jangan unggah file `local.properties`, password, service-role key, atau secret Supabase.
- Publishable key Supabase memang untuk aplikasi klien; RLS harus tetap aktif.
- APK debug untuk uji coba, bukan rilis Play Store.
- Pelacakan BinderByte memerlukan API key terpisah. Masukkan melalui menu API di aplikasi setelah APK terpasang.
- Cloud sync pada source ini manual/setelah login; sinkronisasi latar belakang penuh, penghapusan cloud, refresh token otomatis, dan konflik edit belum dibuat.
- Source belum diuji build di perangkat ini. GitHub Actions akan menunjukkan error build jika ada.
