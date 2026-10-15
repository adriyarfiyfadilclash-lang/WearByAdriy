package com.adriy.wearbyadriy.ui.wardrobe

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.adriy.wearbyadriy.data.Catalog
import com.adriy.wearbyadriy.data.WardrobeItem
import com.adriy.wearbyadriy.data.WardrobeItemInput
import com.adriy.wearbyadriy.ui.Routes
import com.adriy.wearbyadriy.ui.components.ChoiceChips
import com.adriy.wearbyadriy.ui.components.ColorChips
import com.adriy.wearbyadriy.ui.components.ColorDot
import com.adriy.wearbyadriy.ui.components.ConfirmDialog
import com.adriy.wearbyadriy.ui.components.EmptyState
import com.adriy.wearbyadriy.ui.components.ErrorCard
import com.adriy.wearbyadriy.ui.components.Eyebrow
import com.adriy.wearbyadriy.ui.components.ItemImage
import com.adriy.wearbyadriy.ui.components.LoadingButton
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.ui.components.Pill
import com.adriy.wearbyadriy.ui.components.ScreenScaffold
import com.adriy.wearbyadriy.ui.goTab
import com.adriy.wearbyadriy.util.friendlyMessage
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun ItemDetailScreen(nav: NavHostController, id: String) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    val item = lib.item(id)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = item?.name ?: "Pakaian",
        onBack = { nav.popBackStack() },
        actions = {
            if (item != null) {
                IconButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try { vm.toggleItemFavorite(item) } catch (e: Exception) { vm.toast(e.friendlyMessage()) }
                        busy = false
                    }
                }) {
                    Icon(if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (item.isFavorite) "Hapus dari favorit" else "Jadikan favorit")
                }
                IconButton(onClick = { nav.navigate(Routes.itemEdit(item.id)) }) { Icon(Icons.Outlined.Edit, "Ubah") }
            }
        },
    ) { pad ->
        if (item == null) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (!lib.loaded) CircularProgressIndicator(Modifier.align(Alignment.Center), strokeWidth = 2.dp)
                else EmptyState(Icons.Outlined.SearchOff, "Pakaian tidak ditemukan", "Mungkin sudah dihapus.", actionLabel = "Kembali", onAction = { nav.popBackStack() })
            }
            return@ScreenScaffold
        }
        val usedIn = lib.looks.filter { item.id in it.itemIds }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ItemImage(item, Modifier.fillMaxWidth().aspectRatio(0.9f), 22.dp)
            Column {
                Eyebrow(Catalog.categoryLabel(item.category))
                Text(item.name, style = MaterialTheme.typography.headlineSmall)
            }
            if (item.colors.isNotEmpty()) InfoRow("Warna") {
                item.colors.forEach { c -> Row(verticalAlignment = Alignment.CenterVertically) { ColorDot(c); Spacer(Modifier.width(4.dp)); Text(c, style = MaterialTheme.typography.bodyMedium) } }
            }
            if (item.styles.isNotEmpty()) InfoRow("Gaya") { item.styles.forEach { Pill(it) } }
            if (item.occasions.isNotEmpty()) InfoRow("Acara") { item.occasions.forEach { Pill(it) } }
            if (!item.notes.isNullOrBlank()) InfoRow("Catatan") { Text(item.notes, style = MaterialTheme.typography.bodyMedium) }

            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                ListItem(
                    headlineContent = { Text("Tersedia untuk dipakai") },
                    supportingContent = { Text("Matikan saat sedang dicuci, dipinjam, atau rusak. Stylist tidak akan memakainya.") },
                    trailingContent = {
                        Switch(checked = item.isAvailable, enabled = !busy, onCheckedChange = {
                            scope.launch {
                                busy = true
                                try { vm.toggleItemAvailable(item) } catch (e: Exception) { vm.toast(e.friendlyMessage()) }
                                busy = false
                            }
                        })
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }

            OutlinedButton(onClick = { vm.pendingPin.value = item.id; nav.goTab(Routes.STYLE) }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = item.isAvailable) {
                Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Buat outfit dengan item ini")
            }

            if (usedIn.isNotEmpty()) {
                Text("Dipakai di ${usedIn.size} look", style = MaterialTheme.typography.titleMedium)
                usedIn.forEach { look ->
                    TextButton(onClick = { nav.navigate(Routes.look(look.id)) }) { Text(look.name) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("Hapus pakaian", color = MaterialTheme.colorScheme.error)
            }
        }
        if (confirmDelete) ConfirmDialog(
            title = "Hapus ${item.name}?",
            message = if (usedIn.isEmpty()) "Pakaian dan fotonya akan dihapus permanen dari akunmu."
                else "Pakaian dan fotonya akan dihapus permanen, dan dilepas dari ${usedIn.size} look.",
            confirmLabel = "Hapus",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    busy = true
                    try {
                        vm.deleteItem(item)
                        vm.toast("Pakaian dihapus.")
                        nav.popBackStack()
                    } catch (e: Exception) { vm.toast(e.friendlyMessage()) }
                    busy = false
                }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InfoRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun clearCameraCache(context: Context) {
    File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
}

@Composable
fun ItemEditorScreen(nav: NavHostController, id: String?) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    val existing: WardrobeItem? = id?.let { lib.item(it) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (id != null && existing == null) {
        ScreenScaffold("Ubah pakaian", onBack = { nav.popBackStack() }) { pad ->
            EmptyState(Icons.Outlined.SearchOff, "Pakaian tidak ditemukan", "Mungkin sudah dihapus.", Modifier.padding(pad))
        }
        return
    }

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(existing?.category) }
    var colors by rememberSaveable { mutableStateOf(existing?.colors.orEmpty()) }
    var styles by rememberSaveable { mutableStateOf(existing?.styles.orEmpty()) }
    var occasions by rememberSaveable { mutableStateOf(existing?.occasions.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var available by rememberSaveable { mutableStateOf(existing?.isAvailable ?: true) }
    var favorite by rememberSaveable { mutableStateOf(existing?.isFavorite ?: false) }
    var newPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var removePhoto by rememberSaveable { mutableStateOf(false) }
    var pendingCamera by rememberSaveable { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { newPhoto = uri; removePhoto = false }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pendingCamera != null) { newPhoto = pendingCamera; removePhoto = false }
    }

    fun toggle(list: List<String>, v: String) = if (v in list) list - v else list + v

    ScreenScaffold(if (existing == null) "Tambah pakaian" else "Ubah pakaian", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ----- photo -----
            Box(Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(22.dp))) {
                when {
                    newPhoto != null -> AsyncImage(newPhoto, "Foto baru", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    existing?.imagePath != null && !removePhoto -> ItemImage(existing, Modifier.fillMaxSize(), 22.dp)
                    else -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Belum ada foto", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galeri") }
                OutlinedButton(
                    onClick = {
                        runCatching { newCameraUri(context) }
                            .onSuccess { pendingCamera = it; camera.launch(it) }
                            .onFailure { error = "Kamera tidak bisa dibuka." }
                    },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Kamera") }
            }
            if (newPhoto != null || (existing?.imagePath != null && !removePhoto)) {
                TextButton(onClick = { newPhoto = null; removePhoto = existing?.imagePath != null }) { Text("Hapus foto") }
            }

            OutlinedTextField(name, { name = it.take(80); error = null }, label = { Text("Nama pakaian") },
                placeholder = { Text("mis. Kemeja linen putih") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Section("Kategori") {
                ChoiceChips(Catalog.categories.map { it.label }, setOfNotNull(category?.let { Catalog.categoryLabel(it) }), { label ->
                    category = Catalog.categories.first { it.label == label }.key; error = null
                })
            }
            Section("Warna", "Warna pertama yang dipilih dianggap warna utama.") {
                ColorChips(colors.toSet(), { colors = toggle(colors, it) })
            }
            Section("Gaya") { ChoiceChips(Catalog.styles, styles.toSet(), { styles = toggle(styles, it) }) }
            Section("Cocok untuk acara") { ChoiceChips(Catalog.occasions, occasions.toSet(), { occasions = toggle(occasions, it) }) }

            OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Catatan (opsional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tersedia untuk dipakai", Modifier.weight(1f))
                Switch(available, { available = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Favorit", Modifier.weight(1f))
                Switch(favorite, { favorite = it })
            }

            error?.let { ErrorCard(it) }
            LoadingButton(if (existing == null) "Simpan ke lemari" else "Simpan perubahan", saving, modifier = Modifier.fillMaxWidth(), onClick = {
                val cat = category
                when {
                    name.isBlank() -> error = "Beri nama pakaian ini."
                    cat == null -> error = "Pilih kategori."
                    else -> scope.launch {
                        saving = true
                        error = null
                        try {
                            vm.saveItem(
                                existing,
                                WardrobeItemInput(
                                    name = name.trim(), category = cat, colors = colors, styles = styles, occasions = occasions,
                                    isAvailable = available, isFavorite = favorite, notes = notes.trim().ifBlank { null },
                                    imagePath = existing?.imagePath,
                                ),
                                newPhoto, removePhoto,
                            )
                            clearCameraCache(context)
                            vm.toast(if (existing == null) "Pakaian ditambahkan." else "Perubahan disimpan.")
                            nav.popBackStack()
                        } catch (e: Exception) {
                            error = e.friendlyMessage()
                        }
                        saving = false
                    }
                }
            })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}
