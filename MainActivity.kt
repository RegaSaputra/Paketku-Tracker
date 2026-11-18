package id.paketku.tracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val PREF = "paketku_data"
private const val PARCELS = "parcels"
private const val API_KEY = "binderbyte_key"
private const val SB_TOKEN = "supabase_access_token"
private const val SB_USER = "supabase_user_id"
private const val SB_EMAIL = "supabase_email"
private val jsonType = "application/json; charset=utf-8".toMediaType()
private val http = OkHttpClient()

data class ParcelItem(
    val id: String, val awb: String, val courier: String, val courierName: String,
    val label: String, val status: String = "Belum dilacak", val lastUpdate: String = "",
    val history: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaketKuApp(this) }
        val req = PeriodicWorkRequestBuilder<TrackingWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("paketku_auto_track", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaketKuApp(context: Context) {
    val prefs = remember { context.getSharedPreferences(PREF, Context.MODE_PRIVATE) }
    var parcels by remember { mutableStateOf(loadParcels(prefs)) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun persist(list: List<ParcelItem>) { parcels = list; saveParcels(prefs, list) }
    val visible = parcels.filter { it.awb.contains(query, true) || it.label.contains(query, true) || it.courierName.contains(query, true) }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF155EEF), secondary = androidx.compose.ui.graphics.Color(0xFF12B76A))) {
        Scaffold(topBar = {
            TopAppBar(title = { Column { Text("PaketKu Tracker", fontWeight = FontWeight.Bold); Text("Semua resi dalam satu tempat", style = MaterialTheme.typography.labelSmall) } }, actions = {
                TextButton(onClick = { showAccount = true }) { Text(if (prefs.getString(SB_TOKEN, null).isNullOrBlank()) "Login" else "Akun") }
                TextButton(onClick = { showSettings = true }) { Text("API") }
            })
        }, floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Total", parcels.size.toString(), Modifier.weight(1f))
                    StatCard("Dalam proses", parcels.count { !it.status.contains("DELIVERED", true) && it.status != "Belum dilacak" }.toString(), Modifier.weight(1f))
                    StatCard("Terkirim", parcels.count { it.status.contains("DELIVERED", true) || it.status.contains("diterima", true) }.toString(), Modifier.weight(1f))
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cari nama atau nomor resi") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && parcels.isNotEmpty(), onClick = {
                        scope.launch {
                            busy = true
                            var updated = parcels
                            for (p in parcels) {
                                val result = withContext(Dispatchers.IO) { track(context, p) }
                                if (result != null) updated = updated.map { if (it.id == p.id) result else it }
                            }
                            persist(updated); busy = false; message = "Pembaruan pelacakan selesai"
                        }
                    }, modifier = Modifier.weight(1f)) { Text(if (busy) "Melacak…" else "↻ Lacak semua") }
                    OutlinedButton(onClick = { showSettings = true }) { Text("Pengaturan") }
                    OutlinedButton(enabled = !busy && !prefs.getString(SB_TOKEN, null).isNullOrBlank(), onClick = {
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { syncCloud(context, parcels) }
                            if (result != null) persist(result)
                            busy = false
                            message = if (result == null) "Sinkronisasi gagal. Periksa login dan tabel Supabase." else "Sinkronisasi cloud berhasil."
                        }
                    }) { Text("Sync") }
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                if (visible.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Belum ada paket", fontWeight = FontWeight.Bold); Text("Tekan tombol + untuk menambahkan nomor resi pertama.") } }
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visible, key = { it.id }) { p ->
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.label.ifBlank { p.courierName }, fontWeight = FontWeight.Bold); Text(p.status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                                Text("${p.courierName} • ${p.awb}", style = MaterialTheme.typography.bodySmall)
                                if (p.lastUpdate.isNotBlank()) Text(p.lastUpdate, style = MaterialTheme.typography.bodySmall)
                                if (p.history.isNotBlank()) Text(p.history.take(220), style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { scope.launch { busy = true; val r = withContext(Dispatchers.IO) { track(context, p) }; if (r != null) persist(parcels.map { if (it.id == p.id) r else it }); busy = false; message = if (r == null) "Gagal melacak. Periksa API key, kurir, atau koneksi." else "Resi diperbarui." } }) { Text("Lacak") }
                                    TextButton(onClick = { persist(parcels.filterNot { it.id == p.id }) }) { Text("Hapus") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddParcelDialog(onDismiss = { showAdd = false }, onSave = { awb, courier, label ->
        val c = courier.ifBlank { detectCourier(awb) }
        val names = courierNames()
        persist(parcels + ParcelItem(System.currentTimeMillis().toString(), awb.trim(), c, names[c] ?: c.uppercase(), label.trim()))
        showAdd = false
    })
    if (showSettings) ApiSettingsDialog(context, onDismiss = { showSettings = false })
    if (showAccount) AccountDialog(context, parcels, onDismiss = { showAccount = false }, onSynced = { list -> persist(list) })
}

@Composable private fun AccountDialog(context: Context, parcels: List<ParcelItem>, onDismiss: () -> Unit, onSynced: (List<ParcelItem>) -> Unit) {
    val prefs = remember { context.getSharedPreferences(PREF, Context.MODE_PRIVATE) }
    var email by remember { mutableStateOf(prefs.getString(SB_EMAIL, "") ?: "") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val signedIn = !prefs.getString(SB_TOKEN, null).isNullOrBlank()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (signedIn) "Akun PaketKu" else "Login / Daftar") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (signedIn) {
                Text("Masuk sebagai ${prefs.getString(SB_EMAIL, "")}")
                Text("Data lokal dapat disinkronkan ke cloud setelah tabel Supabase disiapkan.", style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password (min. 6 karakter)") }, singleLine = true)
            }
            if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }, confirmButton = {
        if (signedIn) {
            TextButton(onClick = { scope.launch { busy = true; val r = withContext(Dispatchers.IO) { syncCloud(context, parcels) }; if (r != null) onSynced(r); busy = false; info = if (r == null) "Sync gagal. Pastikan SQL tabel sudah dijalankan." else "Cloud sync berhasil." } }, enabled = !busy) { Text(if (busy) "Memproses…" else "Sinkronkan") }
            TextButton(onClick = { prefs.edit().remove(SB_TOKEN).remove(SB_USER).remove(SB_EMAIL).apply(); onDismiss() }) { Text("Keluar") }
        } else {
            TextButton(enabled = !busy && email.contains("@") && password.length >= 6, onClick = {
                scope.launch { busy = true; info = "Membuat akun…"; val result = withContext(Dispatchers.IO) { supabaseAuth("signup", email.trim(), password) }; busy = false
                    if (result != null && result.optString("access_token").isNotBlank()) { storeSession(prefs, result, email.trim()); val synced = withContext(Dispatchers.IO) { syncCloud(context, parcels) }; if (synced != null) onSynced(synced); info = "Akun berhasil dibuat dan login." }
                    else info = "Pendaftaran belum berhasil. Jika konfirmasi email aktif, cek email lalu login." }
            }) { Text("Daftar") }
            TextButton(enabled = !busy && email.contains("@") && password.length >= 6, onClick = {
                scope.launch { busy = true; info = "Login…"; val result = withContext(Dispatchers.IO) { supabaseAuth("token?grant_type=password", email.trim(), password) }; busy = false
                    if (result != null && result.optString("access_token").isNotBlank()) { storeSession(prefs, result, email.trim()); val synced = withContext(Dispatchers.IO) { syncCloud(context, parcels) }; if (synced != null) onSynced(synced); info = "Login berhasil." } else info = "Login gagal. Periksa email/password atau konfirmasi email." }
            }) { Text("Login") }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
}

private fun storeSession(prefs: android.content.SharedPreferences, result: JSONObject, email: String) {
    prefs.edit().putString(SB_TOKEN, result.optString("access_token")).putString(SB_USER, result.optJSONObject("user")?.optString("id") ?: "").putString(SB_EMAIL, email).apply()
}

private fun supabaseAuth(path: String, email: String, password: String): JSONObject? = try {
    val url = "${BuildConfig.SUPABASE_URL}/auth/v1/$path"
    val body = JSONObject().put("email", email).put("password", password).toString().toRequestBody(jsonType)
    val req = Request.Builder().url(url).addHeader("apikey", BuildConfig.SUPABASE_KEY).addHeader("Content-Type", "application/json").post(body).build()
    http.newCall(req).execute().use { response -> val raw = response.body?.string().orEmpty(); if (response.isSuccessful) JSONObject(raw) else null }
} catch (_: Exception) { null }

private fun syncCloud(context: Context, local: List<ParcelItem>): List<ParcelItem>? {
    return try {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val token = prefs.getString(SB_TOKEN, null) ?: return null
    val userId = prefs.getString(SB_USER, null).orEmpty()
    if (userId.isBlank()) return null
    val base = "${BuildConfig.SUPABASE_URL}/rest/v1/parcels"
    val payload = JSONArray()
    local.forEach { p -> payload.put(JSONObject().put("id", p.id).put("user_id", userId).put("awb", p.awb).put("courier", p.courier).put("courier_name", p.courierName).put("label", p.label).put("status", p.status).put("last_update", p.lastUpdate).put("history", p.history)) }
    if (payload.length() > 0) {
        val push = Request.Builder().url(base).addHeader("apikey", BuildConfig.SUPABASE_KEY).addHeader("Authorization", "Bearer $token").addHeader("Prefer", "resolution=merge-duplicates,return=minimal").post(payload.toString().toRequestBody(jsonType)).build()
        http.newCall(push).execute().use { if (!it.isSuccessful) return null }
    }
    val get = Request.Builder().url("$base?select=*&user_id=eq.$userId").addHeader("apikey", BuildConfig.SUPABASE_KEY).addHeader("Authorization", "Bearer $token").get().build()
    http.newCall(get).execute().use { response ->
        if (!response.isSuccessful) return null
        val arr = JSONArray(response.body?.string().orEmpty())
        val cloud = (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); ParcelItem(o.getString("id"), o.getString("awb"), o.getString("courier"), o.getString("courier_name"), o.optString("label"), o.optString("status", "Belum dilacak"), o.optString("last_update"), o.optString("history")) }
        (cloud.associateBy { it.id } + local.associateBy { it.id }).values.toList().also { saveParcels(prefs, it) }
    }
} catch (_: Exception) { null }
}

@Composable private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(title, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun AddParcelDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var awb by remember { mutableStateOf("") }; var courier by remember { mutableStateOf("jnt") }; var label by remember { mutableStateOf("") }
    val options = courierNames().toList()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tambah resi") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(awb, { awb = it }, label = { Text("Nomor resi") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            OutlinedTextField(label, { label = it }, label = { Text("Nama paket (opsional)") }, singleLine = true)
            Text("Ekspedisi", style = MaterialTheme.typography.labelMedium)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(options.firstOrNull { it.first == courier }?.second ?: "Pilih ekspedisi", {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { courier = code; expanded = false }) } }
            }
            Text("Pilih 'Deteksi otomatis' bila kode resi dikenali.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = awb.isNotBlank(), onClick = { onSave(awb, if (courier == "auto") detectCourier(awb) else courier, label) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable private fun ApiSettingsDialog(context: Context, onDismiss: () -> Unit) {
    val prefs = remember { context.getSharedPreferences(PREF, Context.MODE_PRIVATE) }
    var key by remember { mutableStateOf(prefs.getString(API_KEY, "") ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pengaturan pelacakan") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Penyedia: BinderByte • API cek resi multi-kurir", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(key, { key = it }, label = { Text("BinderByte API Key") }, singleLine = true)
            Text("Buat akun dan ambil API key dari binderbyte.com. API key disimpan lokal di HP ini. Pelacakan dikenai kuota/biaya sesuai paket penyedia.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { prefs.edit().putString(API_KEY, "").apply(); key = "" }) { Text("Hapus API key") }
        }
    }, confirmButton = { TextButton(onClick = { prefs.edit().putString(API_KEY, key.trim()).apply(); onDismiss() }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
}

private fun courierNames() = listOf(
    "auto" to "Deteksi otomatis", "jne" to "JNE", "jnt" to "J&T Express", "sicepat" to "SiCepat", "pos" to "POS Indonesia", "anteraja" to "AnterAja", "spx" to "Shopee Express", "tiki" to "TIKI", "ninja" to "Ninja Xpress", "lion" to "Lion Parcel", "ide" to "ID Express", "wahana" to "Wahana", "jnt_cargo" to "J&T Cargo", "kurir_tokopedia" to "Kurir Tokopedia", "sap" to "SAP Express", "lex" to "LEX"
).toMap()

private fun detectCourier(awb: String): String {
    val s = awb.uppercase()
    return when {
        s.startsWith("JP") || s.startsWith("JX") -> "jnt"
        s.startsWith("SPX") -> "spx"
        s.startsWith("JNE") -> "jne"
        s.startsWith("SICEPAT") -> "sicepat"
        s.startsWith("ANT") -> "anteraja"
        s.startsWith("LEX") -> "lex"
        s.startsWith("NINJA") -> "ninja"
        else -> "jnt"
    }
}

private fun track(context: Context, p: ParcelItem): ParcelItem? {
    val key = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(API_KEY, "") ?: ""
    if (key.isBlank()) return null
    return try {
        val url = "https://api.binderbyte.com/v1/track?api_key=${URLEncoder.encode(key, "UTF-8")}&courier=${URLEncoder.encode(p.courier, "UTF-8")}&awb=${URLEncoder.encode(p.awb, "UTF-8")}"
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string() ?: return null)
            if (root.optInt("status") != 200) return null
            val data = root.optJSONObject("data") ?: return null
            val summary = data.optJSONObject("summary") ?: JSONObject()
            val history = data.optJSONArray("history") ?: JSONArray()
            val lines = mutableListOf<String>()
            for (i in 0 until minOf(history.length(), 8)) {
                val h = history.optJSONObject(i) ?: continue
                lines.add("${h.optString("date")} • ${h.optString("desc")} ${h.optString("location")}".trim())
            }
            p.copy(status = summary.optString("status", "Dalam proses"), lastUpdate = summary.optString("date", ""), history = lines.joinToString("\n"))
        }
    } catch (_: Exception) { null }
}

private fun loadParcels(prefs: android.content.SharedPreferences): List<ParcelItem> {
    return try {
        val arr = JSONArray(prefs.getString(PARCELS, "[]"))
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); ParcelItem(o.getString("id"), o.getString("awb"), o.getString("courier"), o.getString("courierName"), o.optString("label"), o.optString("status", "Belum dilacak"), o.optString("lastUpdate"), o.optString("history")) }
    } catch (_: Exception) { emptyList() }
}
private fun saveParcels(prefs: android.content.SharedPreferences, list: List<ParcelItem>) {
    val arr = JSONArray(); list.forEach { p -> arr.put(JSONObject().put("id", p.id).put("awb", p.awb).put("courier", p.courier).put("courierName", p.courierName).put("label", p.label).put("status", p.status).put("lastUpdate", p.lastUpdate).put("history", p.history)) }
    prefs.edit().putString(PARCELS, arr.toString()).apply()
}

class TrackingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val list = loadParcels(prefs)
        if (prefs.getString(API_KEY, "").isNullOrBlank() || list.isEmpty()) return@withContext Result.success()
        val updated = list.map { track(applicationContext, it) ?: it }
        saveParcels(prefs, updated)
        Result.success()
    }
}
