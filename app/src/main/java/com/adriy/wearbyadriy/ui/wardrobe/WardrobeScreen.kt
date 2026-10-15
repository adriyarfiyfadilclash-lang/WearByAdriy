package com.adriy.wearbyadriy.ui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.adriy.wearbyadriy.data.Catalog
import com.adriy.wearbyadriy.ui.Routes
import com.adriy.wearbyadriy.ui.components.EmptyState
import com.adriy.wearbyadriy.ui.components.ErrorCard
import com.adriy.wearbyadriy.ui.components.ItemTile
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.ui.components.ScreenScaffold

private const val ALL = "all"
private const val FAVORITES = "fav"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(nav: NavHostController) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    val shown = lib.items.filter { item ->
        (filter == ALL || (filter == FAVORITES && item.isFavorite) || item.category == filter) &&
            (query.isBlank() || item.name.contains(query.trim(), ignoreCase = true) ||
                item.colors.any { it.contains(query.trim(), true) } || item.notes?.contains(query.trim(), true) == true)
    }

    ScreenScaffold(
        title = "Wardrobe",
        isTab = true,
        actions = {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, if (searching) "Tutup pencarian" else "Cari pakaian")
            }
            IconButton(onClick = { nav.navigate(Routes.ITEM_NEW) }) { Icon(Icons.Outlined.Add, "Tambah pakaian") }
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = lib.loading && lib.loaded, onRefresh = { vm.refresh() }, modifier = Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (searching) {
                    OutlinedTextField(
                        query, { query = it },
                        placeholder = { Text("Cari nama, warna, atau catatan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                val filters = listOf(ALL to "Semua (${lib.items.size})", FAVORITES to "Favorit") +
                    Catalog.categories.map { c -> c.key to "${c.label} (${lib.items.count { it.category == c.key }})" }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters, key = { it.first }) { (key, label) ->
                        FilterChip(
                            selected = filter == key, onClick = { filter = key }, label = { Text(label) },
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
                    lib.items.isEmpty() -> EmptyState(
                        Icons.Outlined.Checkroom, "Lemarimu masih kosong",
                        "Foto pakaianmu satu per satu. Semua tersimpan aman di akunmu dan hanya bisa dilihat olehmu.",
                        actionLabel = "Tambah pakaian pertama", onAction = { nav.navigate(Routes.ITEM_NEW) },
                    )
                    shown.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "Tidak ada yang cocok", "Coba filter atau kata kunci lain.")
                    else -> LazyVerticalGrid(
                        GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (lib.error != null) item(span = { GridItemSpan(maxLineSpan) }) { ErrorCard(lib.error!!) { vm.refresh() } }
                        items(shown, key = { it.id }) { item -> ItemTile(item, onClick = { nav.navigate(Routes.item(item.id)) }) }
                    }
                }
            }
        }
    }
}
