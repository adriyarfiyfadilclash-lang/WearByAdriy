package com.adriy.wearbyadriy.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.adriy.wearbyadriy.BuildConfig
import com.adriy.wearbyadriy.ai.AiConfig
import com.adriy.wearbyadriy.ai.AiProvider
import com.adriy.wearbyadriy.data.Catalog
import com.adriy.wearbyadriy.data.Place
import com.adriy.wearbyadriy.data.ProfileUpdate
import com.adriy.wearbyadriy.data.ThemeMode
import com.adriy.wearbyadriy.ui.Routes
import com.adriy.wearbyadriy.ui.auth.passwordProblem
import com.adriy.wearbyadriy.ui.components.ChoiceChips
import com.adriy.wearbyadriy.ui.components.ColorChips
import com.adriy.wearbyadriy.ui.components.ConfirmDialog
import com.adriy.wearbyadriy.ui.components.ErrorCard
import com.adriy.wearbyadriy.ui.components.Eyebrow
import com.adriy.wearbyadriy.ui.components.LoadingButton
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.ui.components.ScreenScaffold
import com.adriy.wearbyadriy.util.LocationHelper
import com.adriy.wearbyadriy.util.friendlyMessage
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavHostController) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    val theme by vm.prefs.theme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val ai = vm.aiConfig()

    ScreenScaffold("Pengaturan", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(lib.profile?.displayName?.takeIf { it.isNotBlank() } ?: "Tanpa nama", style = MaterialTheme.typography.headlineSmall)
                Text(vm.email().orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Group("Akun & gaya")
            SettingRow(Icons.Outlined.Person, "Profil & preferensi gaya", "Nama, kota cuaca, gaya dan warna favorit") { nav.navigate(Routes.SETTINGS_PROFILE) }
            SettingRow(Icons.Outlined.Key, "API AI",
                if (ai?.ready == true) "${ai.provider.label} · ${ai.model}" else "Belum diatur — saran otomatis tetap bisa dipakai") { nav.navigate(Routes.SETTINGS_AI) }

            Group("Tampilan")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text("Tema", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = theme == m, onClick = { vm.setTheme(m) }, shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.label) }
                    }
                }
            }

            Group("Keamanan")
            SettingRow(Icons.Outlined.Lock, "Ganti password", null) { changePassword = true }
            SettingRow(Icons.AutoMirrored.Outlined.Logout, "Keluar", "Data di perangkat ini dihapus dari tampilan") { confirmLogout = true }
            SettingRow(Icons.Outlined.DeleteForever, "Hapus akun", "Menghapus akun, semua pakaian, foto, dan look secara permanen", danger = true) { confirmDelete = true }

            Group("Tentang")
            SettingRow(Icons.Outlined.Shield, "Privasi", "Foto disimpan di penyimpanan privat dan hanya bisa diakses akunmu. API key AI hanya tersimpan terenkripsi di ponsel ini.", onClick = null)
            Text("WearByAdriy ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        if (busy) AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Spacer(Modifier.width(14.dp)); Text("Memproses…") }
        })
        if (confirmLogout) ConfirmDialog("Keluar dari akun?", "Kamu perlu masuk lagi untuk melihat lemari dan look-mu.", "Keluar", destructive = false,
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                scope.launch { busy = true; vm.signOut(); busy = false }
            })
        if (confirmDelete) DeleteAccountDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    busy = true
                    try { vm.deleteAccount() } catch (e: Exception) { vm.toast(e.friendlyMessage()) }
                    busy = false
                }
            },
        )
        if (changePassword) ChangePasswordDialog(onDismiss = { changePassword = false })
    }
}

@Composable
private fun Group(title: String) {
    Eyebrow(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, danger: Boolean = false, onClick: (() -> Unit)?) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val content: @Composable () -> Unit = {
        ListItem(
            headlineContent = { Text(title, color = color) },
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = { Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = if (onClick != null) { { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) } } else null,
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }
    if (onClick != null) Surface(onClick = onClick, color = MaterialTheme.colorScheme.background) { content() } else content()
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus akun permanen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Semua pakaian, foto, dan look akan dihapus dari server dan tidak bisa dikembalikan. Ketik HAPUS untuk melanjutkan.")
                OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text("HAPUS") })
            }
        },
        confirmButton = {
            TextButton(enabled = typed.trim() == "HAPUS", onClick = onConfirm) { Text("Hapus akun", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val vm = LocalAppViewModel.current
    val scope = rememberCoroutineScope()
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Ganti password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(password, { password = it; error = null }, label = { Text("Password baru") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(confirm, { confirm = it; error = null }, label = { Text("Ulangi password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val p = passwordProblem(password, confirm)
                if (p != null) error = p
                else scope.launch {
                    saving = true
                    try { vm.auth.updatePassword(password); vm.toast("Password berhasil diganti."); onDismiss() }
                    catch (e: Exception) { error = e.friendlyMessage() }
                    saving = false
                }
            }) { Text(if (saving) "Menyimpan…" else "Simpan") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------------------------------------

@Composable
fun ProfileSettingsScreen(nav: NavHostController) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    val p = lib.profile
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(p?.id) { mutableStateOf(p?.displayName.orEmpty()) }
    var city by rememberSaveable(p?.id) { mutableStateOf(p?.city) }
    var lat by rememberSaveable(p?.id) { mutableStateOf(p?.latitude) }
    var lon by rememberSaveable(p?.id) { mutableStateOf(p?.longitude) }
    var styles by rememberSaveable(p?.id) { mutableStateOf(p?.preferredStyles.orEmpty()) }
    var colors by rememberSaveable(p?.id) { mutableStateOf(p?.preferredColors.orEmpty()) }
    var avoided by rememberSaveable(p?.id) { mutableStateOf(p?.avoidedColors.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var cityError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun locate() = scope.launch {
        locating = true
        cityError = null
        val loc = LocationHelper.current(context)
        if (loc == null) cityError = "Lokasi tidak tersedia. Aktifkan layanan lokasi atau cari kota secara manual."
        else {
            lat = loc.latitude; lon = loc.longitude
            city = LocationHelper.cityName(context, loc) ?: "Lokasi saat ini"
        }
        locating = false
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else cityError = "Izin lokasi ditolak. Kamu tetap bisa mencari kota secara manual."
    }

    fun toggle(list: List<String>, v: String) = if (v in list) list - v else list + v

    ScreenScaffold("Profil & preferensi", onBack = { nav.popBackStack() }) { pad ->
        if (p == null) {
            Column(Modifier.padding(pad).padding(16.dp)) {
                if (lib.loading) CircularProgressIndicator(strokeWidth = 2.dp)
                else ErrorCard(lib.error ?: "Profil belum bisa dimuat.") { vm.refresh() }
            }
            return@ScreenScaffold
        }
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            OutlinedTextField(name, { name = it.take(50) }, label = { Text("Nama tampilan") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kota untuk cuaca", style = MaterialTheme.typography.titleMedium)
                Text(if (city != null) "Terpilih: $city" else "Belum ada kota.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    query, { query = it; cityError = null },
                    label = { Text("Cari kota") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(enabled = query.trim().length >= 2 && !searching, onClick = {
                            scope.launch {
                                searching = true
                                try {
                                    results = vm.searchCity(query.trim())
                                    if (results.isEmpty()) cityError = "Kota tidak ditemukan."
                                } catch (e: Exception) { cityError = e.friendlyMessage() }
                                searching = false
                            }
                        }) { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Search, "Cari") }
                    },
                )
                results.forEach { place ->
                    Surface(onClick = { city = place.name; lat = place.latitude; lon = place.longitude; results = emptyList(); query = "" },
                        color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                        Text(place.display, Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                OutlinedButton(enabled = !locating, onClick = {
                    if (LocationHelper.hasPermission(context)) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) {
                    if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.MyLocation, null)
                    Spacer(Modifier.width(8.dp)); Text("Pakai lokasi saat ini")
                }
                if (city != null) TextButton(onClick = { city = null; lat = null; lon = null }) { Text("Hapus kota") }
                cityError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Preferensi ini dipakai sebagai pilihan awal di tab Style.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gaya favorit", style = MaterialTheme.typography.titleMedium)
                ChoiceChips(Catalog.styles, styles.toSet(), { styles = toggle(styles, it) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Warna favorit", style = MaterialTheme.typography.titleMedium)
                ColorChips(colors.toSet(), { c -> colors = toggle(colors, c); avoided = avoided - c })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Warna yang dihindari", style = MaterialTheme.typography.titleMedium)
                ColorChips(avoided.toSet(), { c -> avoided = toggle(avoided, c); colors = colors - c })
            }
            error?.let { ErrorCard(it) }
            LoadingButton("Simpan", saving, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        vm.saveProfile(ProfileUpdate(name.trim().ifBlank { null }, city, lat, lon, styles, colors, avoided))
                        vm.toast("Profil disimpan.")
                        nav.popBackStack()
                    } catch (e: Exception) { error = e.friendlyMessage() }
                    saving = false
                }
            })
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun AiSettingsScreen(nav: NavHostController) {
    val vm = LocalAppViewModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { vm.aiConfig() } ?: return

    var provider by rememberSaveable { mutableStateOf(initial.provider) }
    var stored by remember { mutableStateOf(initial) }        // what is saved for the selected provider
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmDeleteKey by remember { mutableStateOf(false) }

    fun pick(p: AiProvider) {
        provider = p
        val c = vm.aiConfigFor(p) ?: return
        stored = c
        model = c.model
        keyInput = ""
        status = null
    }
    fun effective() = AiConfig(provider, model.trim().ifBlank { provider.defaultModel }, keyInput.trim().ifBlank { stored.apiKey }, enabled)

    ScreenScaffold("API AI", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    "Saran AI memakai akun penyedia AI milikmu sendiri. API key disimpan terenkripsi (Android Keystore) hanya di ponsel ini, " +
                        "tidak dikirim ke server WearByAdriy, dan tidak ikut dicadangkan. Biaya pemakaian mengikuti akun penyedia AI-mu.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Penyedia", style = MaterialTheme.typography.titleMedium)
                AiProvider.entries.forEach { p ->
                    Surface(
                        onClick = { pick(p) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (provider == p) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label, style = MaterialTheme.typography.titleMedium)
                                val has = vm.aiConfigFor(p)?.apiKey?.isNotBlank() == true
                                Text(if (has) "Key tersimpan" else "Belum ada key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (provider == p) Text("Dipilih", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            OutlinedTextField(model, { model = it.trim(); status = null }, label = { Text("Model") }, singleLine = true,
                supportingText = { Text("Bawaan: ${provider.defaultModel}. Ganti jika akunmu memakai model lain.") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                keyInput, { keyInput = it; status = null },
                label = { Text("API key") },
                placeholder = { Text(if (stored.apiKey.isNotBlank()) "Tersimpan (…${stored.apiKey.takeLast(4)}). Isi untuk mengganti." else provider.keyHint) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Tampilkan key") } },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl))) }) { Text("Buat API key di situs ${provider.label}") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aktifkan saran AI", style = MaterialTheme.typography.bodyLarge)
                    Text("Jika mati, tab Style hanya memakai saran otomatis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled, { enabled = it })
            }
            status?.let { (ok, msg) ->
                if (ok) Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), shape = MaterialTheme.shapes.medium) {
                    Text(msg, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                } else ErrorCard(msg)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !testing && effective().apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f).height(50.dp),
                    onClick = {
                        scope.launch {
                            testing = true
                            status = null
                            val cfg = effective()
                            status = try { vm.testAi(cfg); true to "Terhubung. ${cfg.provider.label} (${cfg.model}) menjawab dengan benar." }
                                catch (e: Exception) { false to e.friendlyMessage() }
                            testing = false
                        }
                    },
                ) { if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Tes koneksi") }
                LoadingButton("Simpan", false, modifier = Modifier.weight(1f), onClick = {
                    val cfg = effective()
                    vm.saveAiConfig(cfg)
                    stored = cfg
                    keyInput = ""
                    vm.toast(if (cfg.apiKey.isBlank()) "Disimpan tanpa API key." else "Pengaturan AI disimpan di perangkat ini.")
                })
            }
            if (stored.apiKey.isNotBlank()) {
                TextButton(onClick = { confirmDeleteKey = true }) { Text("Hapus API key ${provider.label}", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (confirmDeleteKey) ConfirmDialog("Hapus API key?", "Key ${provider.label} akan dihapus dari perangkat ini.", "Hapus",
            onDismiss = { confirmDeleteKey = false },
            onConfirm = {
                confirmDeleteKey = false
                vm.deleteAiKey(provider)
                stored = stored.copy(apiKey = "")
                status = null
                vm.toast("API key dihapus.")
            })
    }
}
