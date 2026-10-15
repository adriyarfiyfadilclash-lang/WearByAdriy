package com.adriy.wearbyadriy.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.adriy.wearbyadriy.ai.StyleRequest
import com.adriy.wearbyadriy.ai.Stylist
import com.adriy.wearbyadriy.ui.Routes
import com.adriy.wearbyadriy.ui.WeatherState
import com.adriy.wearbyadriy.ui.components.EmptyState
import com.adriy.wearbyadriy.ui.components.ErrorCard
import com.adriy.wearbyadriy.ui.components.Eyebrow
import com.adriy.wearbyadriy.ui.components.LoadingButton
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.ui.components.LookCollage
import com.adriy.wearbyadriy.ui.components.OutfitCard
import com.adriy.wearbyadriy.ui.components.ScreenScaffold
import com.adriy.wearbyadriy.ui.components.SecondaryButton
import com.adriy.wearbyadriy.ui.components.SectionTitle
import com.adriy.wearbyadriy.ui.goTab
import com.adriy.wearbyadriy.util.friendlyMessage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = LocalAppViewModel.current
    val lib by vm.library.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var seed by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var saving by remember { mutableStateOf(false) }
    var savedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val profile = lib.profile
    val condition = (weather as? WeatherState.Ready)?.weather?.condition
    val suggestion = remember(lib.items, seed, condition, profile) {
        Stylist.ruleBased(
            lib.items,
            StyleRequest(
                occasion = "Santai",
                styles = profile?.preferredStyles.orEmpty(),
                preferredColors = profile?.preferredColors.orEmpty(),
                avoidedColors = profile?.avoidedColors.orEmpty(),
                weather = condition,
                count = 1,
            ),
            seed,
        ).firstOrNull()
    }
    val wornToday = lib.looks.firstOrNull { today.toString() in it.wornDates }

    ScreenScaffold(
        title = "WearByAdriy",
        isTab = true,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.AccountCircle, "Profil & pengaturan") }
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = lib.loading && lib.loaded, onRefresh = { vm.refresh() }, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    val name = profile?.displayName?.takeIf { it.isNotBlank() }
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("id-ID"))),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(if (name != null) "Halo, $name" else "Halo", style = MaterialTheme.typography.headlineMedium)
                }
                item { WeatherCard(weather, onSetCity = { nav.navigate(Routes.SETTINGS_PROFILE) }, onRetry = { vm.loadWeather() }) }

                when {
                    !lib.loaded && lib.loading -> item {
                        Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
                    }
                    !lib.loaded && lib.error != null -> item { ErrorCard(lib.error!!, onRetry = { vm.refresh() }) }
                    else -> {
                        if (lib.error != null) item { ErrorCard(lib.error!!, onRetry = { vm.refresh() }) }
                        if (wornToday != null) item {
                            OutfitCard(
                                name = wornToday.name,
                                description = wornToday.description,
                                items = wornToday.itemIds.mapNotNull { lib.item(it) },
                                reasons = emptyList(),
                                tags = wornToday.tags,
                                badge = "Dipakai hari ini",
                                actions = { TextButton(onClick = { nav.navigate(Routes.look(wornToday.id)) }) { Text("Buka look") } },
                            )
                        }
                        item {
                            if (suggestion == null) {
                                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                                    EmptyState(
                                        Icons.Outlined.Checkroom,
                                        "Belum bisa membuat outfit",
                                        if (lib.items.isEmpty()) "Lemarimu masih kosong. Tambahkan pakaian dulu, minimal satu atasan dan satu bawahan."
                                        else "Tambahkan minimal satu atasan dan satu bawahan yang tersedia.",
                                        actionLabel = "Tambah pakaian",
                                        onAction = { nav.navigate(Routes.ITEM_NEW) },
                                    )
                                }
                            } else {
                                val key = suggestion.itemIds.sorted().joinToString()
                                OutfitCard(
                                    name = suggestion.name,
                                    description = suggestion.description,
                                    items = suggestion.itemIds.mapNotNull { lib.item(it) },
                                    reasons = suggestion.reasons,
                                    tags = suggestion.tags,
                                    badge = "Outfit hari ini · dari lemarimu",
                                    onItemClick = { nav.navigate(Routes.item(it.id)) },
                                    actions = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            LoadingButton(
                                                if (savedKey == key) "Tersimpan" else "Simpan ke Looks",
                                                saving,
                                                enabled = savedKey != key,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    scope.launch {
                                                        saving = true
                                                        try {
                                                            vm.saveLook(suggestion, "Santai")
                                                            savedKey = key
                                                            vm.toast("Look disimpan.")
                                                        } catch (e: Exception) { vm.toast(e.friendlyMessage()) }
                                                        saving = false
                                                    }
                                                },
                                            )
                                            SecondaryButton("Acak lagi", { seed = System.nanoTime() }, Modifier.weight(1f))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Dipilih otomatis dengan aturan warna, gaya, dan cuaca. Untuk saran AI, buka tab Style.",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                            }
                        }
                        item { Stats(lib.items.size, lib.looks.size, lib.looks.count { it.isFavorite }) }
                        if (lib.looks.isNotEmpty()) {
                            item {
                                SectionTitle("Look terbaru", action = { TextButton(onClick = { nav.goTab(Routes.LOOKS) }) { Text("Lihat semua") } })
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(lib.looks.take(8), key = { it.id }) { look ->
                                        Surface(onClick = { nav.navigate(Routes.look(look.id)) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                                            Column(Modifier.width(150.dp).padding(8.dp)) {
                                                LookCollage(look.itemIds.mapNotNull { lib.item(it) }, Modifier.fillMaxWidth().aspectRatio(0.9f))
                                                Spacer(Modifier.height(6.dp))
                                                Text(look.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(state: WeatherState, onSetCity: () -> Unit, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state is WeatherState.NoCity) Icons.Outlined.LocationOn else Icons.Outlined.WbSunny,
                null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                when (state) {
                    WeatherState.NoCity -> {
                        Text("Cuaca belum aktif", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Atur kotamu agar outfit menyesuaikan cuaca.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    WeatherState.Loading -> Text("Memuat cuaca…", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    is WeatherState.Ready -> {
                        Eyebrow(state.city)
                        Text("${state.weather.temperature.roundToInt()}° · ${state.weather.label}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Min ${state.weather.min.roundToInt()}° · Maks ${state.weather.max.roundToInt()}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    is WeatherState.Failed -> {
                        Eyebrow(state.city)
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            when (state) {
                WeatherState.NoCity -> TextButton(onClick = onSetCity) { Text("Atur kota") }
                is WeatherState.Failed -> IconButton(onClick = onRetry) { Icon(Icons.Outlined.Refresh, "Muat ulang cuaca") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun Stats(items: Int, looks: Int, favorites: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Pakaian" to items, "Looks" to looks, "Favorit" to favorites).forEach { (label, n) ->
            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(14.dp)) {
                    Text("$n", style = MaterialTheme.typography.headlineSmall)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
