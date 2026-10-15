package com.adriy.wearbyadriy.ui.looks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.adriy.wearbyadriy.data.Catalog
import com.adriy.wearbyadriy.data.Look
import com.adriy.wearbyadriy.data.OutfitSuggestion
import com.adriy.wearbyadriy.ui.Routes
import com.adriy.wearbyadriy.ui.components.ChoiceChips
import com.adriy.wearbyadriy.ui.components.ConfirmDialog
import com.adriy.wearbyadriy.ui.components.EmptyState
import com.adriy.wearbyadriy.ui.components.ErrorCard
import com.adriy.wearbyadriy.ui.components.Eyebrow
import com.adriy.wearbyadriy.ui.components.ItemTile
import com.adriy.wearbyadriy.ui.components.LoadingButton
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.ui.components.LookCollage
import com.adriy.wearbyadriy.ui.components.OutfitCard
import com.adriy.wearbyadriy.ui.components.ScreenScaffold
import com.adriy.wearbyadriy.util.friendlyMessage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ALL = "Semua"
private const val FAV = "Favorit"
private const val WORN = "Pernah dipakai"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LooksScreen(nav: NavHostController) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(ALL) }
    val occasionsInUse = lib.looks.mapNotNull { it.occasion }.distinct()
    val filters = listOf(ALL, FAV, WORN) + occasionsInUse
    val shown = lib.looks.filter {
        when (filter) {
            ALL -> true
            FAV -> it.isFavorite
            WORN -> it.wornDates.isNotEmpty()
            else -> it.occasion == filter
        }
    }

    ScreenScaffold(
        title = "Other Looks",
        isTab = true,
        actions = { IconButton(onClick = { nav.navigate(Routes.LOOK_NEW) }) { Icon(Icons.Outlined.Add, "Buat look manual") } },
    ) { pad ->
        PullToRefreshBox(isRefreshing = lib.loading && lib.loaded, onRefresh = { vm.refresh() }, modifier = Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters, key = { it }) { f ->
                        FilterChip(
                            selected = filter == f, onClick = { filter = f }, label = { Text(f) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                when {
                    !lib.loaded && lib.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
                    !lib.loaded && lib.error != null -> ErrorCard(lib.error!!, Modifier.padding(16.dp)) { vm.refresh() }
                    lib.looks.isEmpty() -> EmptyState(
                        Icons.Outlined.Collections, "Belum ada look tersimpan",
                        "Buat outfit di tab Style atau susun sendiri dari isi lemarimu.",
                        actionLabel = "Susun look", onAction = { nav.navigate(Routes.LOOK_NEW) },
                    )
                    shown.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "Tidak ada look di filter ini", "Pilih filter lain.")
                    else -> LazyVerticalGrid(
                        GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (lib.error != null) item(span = { GridItemSpan(maxLineSpan) }) { ErrorCard(lib.error!!) { vm.refresh() } }
                        items(shown, key = { it.id }) { look -> LookTile(look, lib.items.associateBy { it.id }) { nav.navigate(Routes.look(look.id)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookTile(look: Look, byId: Map<String, com.adriy.wearbyadriy.data.WardrobeItem>, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(8.dp)) {
            Box {
                LookCollage(look.itemIds.mapNotNull { byId[it] }, Modifier.fillMaxWidth().aspectRatio(0.88f))
                if (look.isFavorite) Icon(Icons.Filled.Favorite, "Favorit", Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(look.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(look.occasion, if (look.wornDates.isNotEmpty()) "Dipakai ${look.wornDates.size}×" else null, if (look.source == "ai") "AI" else null)
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID"))

@Composable
fun LookDetailScreen(nav: NavHostController, id: String) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    val look = lib.look(id)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    fun act(block: suspend () -> Unit) = scope.launch {
        busy = true
        try { block() } catch (e: Exception) { vm.toast(e.friendlyMessage()) }
        busy = false
    }

    ScreenScaffold(
        title = look?.name ?: "Look",
        onBack = { nav.popBackStack() },
        actions = {
            if (look != null) {
                IconButton(enabled = !busy, onClick = { act { vm.toggleLookFavorite(look) } }) {
                    Icon(if (look.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (look.isFavorite) "Hapus dari favorit" else "Jadikan favorit")
                }
                IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Ubah nama") }
            }
        },
    ) { pad ->
        if (look == null) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (!lib.loaded) CircularProgressIndicator(Modifier.align(Alignment.Center), strokeWidth = 2.dp)
                else EmptyState(Icons.Outlined.SearchOff, "Look tidak ditemukan", "Mungkin sudah dihapus.", actionLabel = "Kembali", onAction = { nav.popBackStack() })
            }
            return@ScreenScaffold
        }
        val items = look.itemIds.mapNotNull { lib.item(it) }
        val today = LocalDate.now().toString()
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutfitCard(
                name = look.name,
                description = look.description,
                items = items,
                reasons = look.reasons,
                tags = look.tags,
                badge = listOfNotNull(look.occasion, when (look.source) { "ai" -> "Saran AI${look.aiProvider?.let { " ($it)" } ?: ""}"; "rules" -> "Saran otomatis"; else -> "Disusun manual" }).joinToString(" · "),
                onItemClick = { nav.navigate(Routes.item(it.id)) },
            )
            if (items.any { !it.isAvailable }) {
                Text("Sebagian item sedang ditandai tidak tersedia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            LoadingButton(
                if (today in look.wornDates) "Batalkan tanda dipakai hari ini" else "Tandai dipakai hari ini",
                busy, modifier = Modifier.fillMaxWidth(),
                onClick = { act { vm.toggleWornToday(look) } },
            )
            OutlinedButton(onClick = { nav.navigate(Routes.lookItems(look.id)) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Ganti item") }
            if (look.wornDates.isNotEmpty()) {
                Column {
                    Eyebrow("Riwayat dipakai")
                    Spacer(Modifier.height(6.dp))
                    look.wornDates.take(10).forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Outlined.EventAvailable, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(runCatching { LocalDate.parse(d).format(dateFmt) }.getOrDefault(d), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("Hapus look", color = MaterialTheme.colorScheme.error)
            }
        }
        if (confirmDelete) ConfirmDialog(
            "Hapus look ini?", "Look \"${look.name}\" akan dihapus. Pakaian di lemarimu tidak ikut terhapus.", "Hapus",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    busy = true
                    try { vm.deleteLook(look); vm.toast("Look dihapus."); nav.popBackStack() } catch (e: Exception) { vm.toast(e.friendlyMessage()) }
                    busy = false
                }
            },
            onDismiss = { confirmDelete = false },
        )
        if (renaming) RenameDialog(look, onDismiss = { renaming = false }) { name, desc ->
            renaming = false
            act { vm.renameLook(look, name, desc); vm.toast("Look diperbarui.") }
        }
    }
}

@Composable
private fun RenameDialog(look: Look, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(look.name) }
    var desc by rememberSaveable { mutableStateOf(look.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah look") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nama") }, singleLine = true)
                OutlinedTextField(desc, { desc = it.take(300) }, label = { Text("Deskripsi") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), desc.trim().ifBlank { null }) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Build a look by hand (lookId == null) or change the items of an existing look. */
@Composable
fun LookBuilderScreen(nav: NavHostController, lookId: String?) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    val existing = lookId?.let { lib.look(it) }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(existing?.itemIds ?: emptyList()) }
    var name by rememberSaveable { mutableStateOf("") }
    var occasion by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = if (existing == null) "Susun look" else "Ganti item",
        onBack = { nav.popBackStack() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    error?.let { ErrorCard(it); Spacer(Modifier.height(8.dp)) }
                    LoadingButton("Simpan (${selected.size} item)", saving, modifier = Modifier.fillMaxWidth(), onClick = {
                        when {
                            selected.isEmpty() -> error = "Pilih minimal satu item."
                            existing == null && name.isBlank() -> error = "Beri nama look ini."
                            else -> scope.launch {
                                saving = true
                                error = null
                                val ordered = selected.sortedBy { id -> Catalog.slotOrder.indexOf(lib.item(id)?.category) }
                                try {
                                    if (existing == null) {
                                        vm.saveLook(OutfitSuggestion(name.trim(), "", emptyList(), listOfNotNull(occasion), ordered, "manual"), occasion)
                                        vm.toast("Look disimpan.")
                                    } else {
                                        vm.updateLookItems(existing, ordered)
                                        vm.toast("Item look diperbarui.")
                                    }
                                    nav.popBackStack()
                                } catch (e: Exception) { error = e.friendlyMessage() }
                                saving = false
                            }
                        }
                    })
                }
            }
        },
    ) { pad ->
        if (lookId != null && existing == null) {
            EmptyState(Icons.Outlined.SearchOff, "Look tidak ditemukan", "Mungkin sudah dihapus.", Modifier.padding(pad))
            return@ScreenScaffold
        }
        if (lib.items.isEmpty()) {
            EmptyState(
                Icons.Outlined.Collections, "Lemarimu masih kosong", "Tambahkan pakaian dulu untuk menyusun look.", Modifier.padding(pad),
                actionLabel = "Tambah pakaian", onAction = { nav.navigate(Routes.ITEM_NEW) },
            )
            return@ScreenScaffold
        }
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).imePadding().fillMaxSize(),
        ) {
            if (existing == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(name, { name = it.take(60); error = null }, label = { Text("Nama look") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Acara (opsional)", style = MaterialTheme.typography.titleMedium)
                        ChoiceChips(Catalog.occasions, setOfNotNull(occasion), { occasion = if (occasion == it) null else it })
                    }
                }
            }
            Catalog.categories.forEach { cat ->
                val inCat = lib.items.filter { it.category == cat.key }
                if (inCat.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-${cat.key}") {
                        Text(cat.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(inCat, key = { it.id }) { item ->
                        ItemTile(item, selected = item.id in selected, onClick = {
                            selected = if (item.id in selected) selected - item.id else selected + item.id
                            error = null
                        })
                    }
                }
            }
        }
    }
}
