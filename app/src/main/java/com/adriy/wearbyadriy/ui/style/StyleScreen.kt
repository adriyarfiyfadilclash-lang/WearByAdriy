package com.adriy.wearbyadriy.ui.style

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.adriy.wearbyadriy.ai.AiConfig
import com.adriy.wearbyadriy.ai.StyleRequest
import com.adriy.wearbyadriy.ai.Stylist
import com.adriy.wearbyadriy.data.Catalog
import com.adriy.wearbyadriy.data.GenerationLog
import com.adriy.wearbyadriy.data.OutfitSuggestion
import com.adriy.wearbyadriy.data.WardrobeItem
import com.adriy.wearbyadriy.ui.AppViewModel
import com.adriy.wearbyadriy.ui.Routes
import com.adriy.wearbyadriy.ui.WeatherState
import com.adriy.wearbyadriy.ui.components.ChoiceChips
import com.adriy.wearbyadriy.ui.components.ColorChips
import com.adriy.wearbyadriy.ui.components.EmptyState
import com.adriy.wearbyadriy.ui.components.ErrorCard
import com.adriy.wearbyadriy.ui.components.ItemImage
import com.adriy.wearbyadriy.ui.components.LoadingButton
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.ui.components.OutfitCard
import com.adriy.wearbyadriy.ui.components.ScreenScaffold
import com.adriy.wearbyadriy.ui.components.SecondaryButton
import com.adriy.wearbyadriy.util.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class StyleMode { AI, RULES }

/** Form and results for the Style tab; survives switching tabs. */
class StyleViewModel(app: Application) : AndroidViewModel(app) {
    var occasion by mutableStateOf("Santai")
    val styles = mutableStateListOf<String>()
    val preferredColors = mutableStateListOf<String>()
    val avoidedColors = mutableStateListOf<String>()
    var weather by mutableStateOf<String?>(null)
    var mood by mutableStateOf<String?>(null)
    val pinned = mutableStateListOf<String>()
    var mode by mutableStateOf(StyleMode.AI)
    var initializedFromProfile = false

    var generating by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var aiFailed by mutableStateOf(false)
    var results by mutableStateOf<List<OutfitSuggestion>>(emptyList())
    var resultLabel by mutableStateOf<String?>(null)
    val saved = mutableStateListOf<Int>()
    private var job: Job? = null

    fun request() = StyleRequest(
        occasion = occasion, styles = styles.toList(), preferredColors = preferredColors.toList(),
        avoidedColors = avoidedColors.toList(), weather = weather, mood = mood, pinnedItemIds = pinned.toList(), count = 3,
    )

    fun generate(app: AppViewModel, items: List<WardrobeItem>, useMode: StyleMode, config: AiConfig?) {
        job?.cancel()
        job = viewModelScope.launch {
            generating = true
            error = null
            aiFailed = false
            val req = request()
            try {
                if (useMode == StyleMode.AI) {
                    val cfg = config ?: throw IllegalStateException("AI belum diatur.")
                    val text = app.aiClient.complete(cfg, Stylist.prompt(items, req))
                    val looks = Stylist.parse(text, items, req, cfg.provider.label)
                    results = looks
                    resultLabel = "Saran AI · ${cfg.provider.label} (${cfg.model})"
                    app.data.logGeneration(GenerationLog(cfg.provider.key, cfg.model, "success", occasion = req.occasion, lookCount = looks.size))
                } else {
                    val looks = Stylist.ruleBased(items, req)
                    if (looks.isEmpty()) throw IllegalStateException("Tidak ada kombinasi yang cocok. Kurangi warna yang dihindari atau tandai lebih banyak item tersedia.")
                    results = looks
                    resultLabel = "Saran otomatis · berdasarkan aturan warna, gaya, dan cuaca"
                }
                saved.clear()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.friendlyMessage()
                error = msg
                if (useMode == StyleMode.AI) {
                    aiFailed = true
                    config?.let { app.data.logGeneration(GenerationLog(it.provider.key, it.model, "failed", msg.take(300), req.occasion)) }
                }
            } finally {
                generating = false
            }
        }
    }
}

private val weatherOptions = listOf("Panas", "Hangat", "Sejuk", "Dingin", "Hujan")

@Composable
fun StyleScreen(nav: NavHostController) {
    val app = LocalAppViewModel.current
    val vm: StyleViewModel = viewModel()
    val lib by app.library.collectAsStateWithLifecycle()
    val weatherState by app.weather.collectAsStateWithLifecycle()
    val pendingPin by app.pendingPin.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val config = app.aiConfig()
    val aiReady = config?.ready == true

    LaunchedEffect(lib.profile) {
        val p = lib.profile
        if (p != null && !vm.initializedFromProfile) {
            vm.initializedFromProfile = true
            vm.styles.addAll(p.preferredStyles.filter { it !in vm.styles })
            vm.preferredColors.addAll(p.preferredColors.filter { it !in vm.preferredColors })
            vm.avoidedColors.addAll(p.avoidedColors.filter { it !in vm.avoidedColors })
        }
    }
    LaunchedEffect(weatherState) {
        val w = weatherState
        if (vm.weather == null && w is WeatherState.Ready) vm.weather = w.weather.condition
    }
    LaunchedEffect(pendingPin) {
        pendingPin?.let { id -> if (id !in vm.pinned) vm.pinned.add(id); app.pendingPin.value = null }
    }
    LaunchedEffect(aiReady) { if (!aiReady && vm.mode == StyleMode.AI) vm.mode = StyleMode.RULES }

    val pool = Stylist.pool(lib.items, vm.request())
    val canStyle = Stylist.canStyle(pool)

    ScreenScaffold(title = "Style", isTab = true) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (lib.loaded && lib.items.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.Checkroom, "Belum ada pakaian",
                        "Stylist hanya memakai pakaian yang benar-benar ada di lemarimu. Tambahkan dulu minimal satu atasan dan satu bawahan.",
                        actionLabel = "Tambah pakaian", onAction = { nav.navigate(Routes.ITEM_NEW) },
                    )
                }
                return@LazyColumn
            }
            item {
                Text("Mau tampil seperti apa?", style = MaterialTheme.typography.headlineSmall)
                Text("Outfit disusun dari ${pool.size} pakaian tersedia di lemarimu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Field("Acara") { ChoiceChips(Catalog.occasions, setOf(vm.occasion), { vm.occasion = it }) } }
            item { Field("Gaya") { ChoiceChips(Catalog.styles, vm.styles.toSet(), { if (it in vm.styles) vm.styles.remove(it) else vm.styles.add(it) }) } }
            item {
                val hint = (weatherState as? WeatherState.Ready)?.let { "Dari cuaca ${it.city}: ${it.weather.condition}" }
                Field("Cuaca", hint) { ChoiceChips(weatherOptions, setOfNotNull(vm.weather), { vm.weather = if (vm.weather == it) null else it }) }
            }
            item { Field("Mood") { ChoiceChips(Catalog.moods, setOfNotNull(vm.mood), { vm.mood = if (vm.mood == it) null else it }) } }
            item {
                Field("Warna favorit") {
                    ColorChips(vm.preferredColors.toSet(), { c ->
                        if (c in vm.preferredColors) vm.preferredColors.remove(c) else { vm.preferredColors.add(c); vm.avoidedColors.remove(c) }
                    })
                }
            }
            item {
                Field("Warna yang dihindari") {
                    ColorChips(vm.avoidedColors.toSet(), { c ->
                        if (c in vm.avoidedColors) vm.avoidedColors.remove(c) else { vm.avoidedColors.add(c); vm.preferredColors.remove(c) }
                    })
                }
            }
            if (vm.pinned.isNotEmpty()) item {
                Field("Wajib dipakai") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.pinned.toList(), key = { it }) { id ->
                            val item = lib.item(id)
                            InputChip(
                                selected = true,
                                onClick = { vm.pinned.remove(id) },
                                label = { Text(item?.name ?: "Item dihapus") },
                                avatar = { ItemImage(item, Modifier.size(24.dp), 6.dp) },
                                trailingIcon = { Icon(Icons.Outlined.Close, "Lepas", Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
            item {
                Field("Cara membuat") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = vm.mode == StyleMode.AI, onClick = { vm.mode = StyleMode.AI }, enabled = aiReady,
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("AI") }
                        SegmentedButton(
                            selected = vm.mode == StyleMode.RULES, onClick = { vm.mode = StyleMode.RULES },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Otomatis (tanpa AI)") }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (aiReady) {
                        Text("AI memakai ${config!!.provider.label} dengan API key milikmu. Daftar pakaian (nama, kategori, warna, tag — tanpa foto) dikirim ke ${config.provider.label}.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AI belum diatur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { nav.navigate(Routes.SETTINGS_AI) }) { Text("Atur API AI") }
                        }
                    }
                }
            }
            item {
                if (!canStyle) {
                    ErrorCard("Butuh minimal satu atasan dan satu bawahan yang tersedia dan tidak termasuk warna yang dihindari.")
                } else {
                    LoadingButton(
                        if (vm.mode == StyleMode.AI) "Buat 3 outfit dengan AI" else "Buat 3 outfit",
                        vm.generating, modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.generate(app, lib.items, vm.mode, config) },
                    )
                }
                if (vm.generating) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (vm.mode == StyleMode.AI) "Menunggu jawaban dari ${config?.provider?.label}…" else "Menyusun…",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            vm.error?.let { msg ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ErrorCard(if (vm.aiFailed) "AI gagal: $msg" else msg)
                        if (vm.aiFailed) {
                            Text("Tidak ada outfit yang ditampilkan dari AI. Kamu bisa mencoba lagi, atau membuat saran otomatis tanpa AI.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton("Coba lagi", { vm.generate(app, lib.items, StyleMode.AI, config) }, Modifier.weight(1f))
                                SecondaryButton("Pakai otomatis", { vm.mode = StyleMode.RULES; vm.generate(app, lib.items, StyleMode.RULES, config) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (vm.results.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(6.dp))
                        Text(vm.resultLabel.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(vm.results) { index, s ->
                    var saving by androidx.compose.runtime.remember(s) { mutableStateOf(false) }
                    OutfitCard(
                        name = s.name, description = s.description,
                        items = s.itemIds.mapNotNull { lib.item(it) },
                        reasons = s.reasons, tags = s.tags,
                        onItemClick = { nav.navigate(Routes.item(it.id)) },
                        actions = {
                            val done = index in vm.saved
                            LoadingButton(if (done) "Tersimpan di Looks" else "Simpan ke Looks", saving, enabled = !done, modifier = Modifier.fillMaxWidth(), onClick = {
                                scope.launch {
                                    saving = true
                                    try { app.saveLook(s, vm.occasion); vm.saved.add(index); app.toast("Look disimpan.") }
                                    catch (e: Exception) { app.toast(e.friendlyMessage()) }
                                    saving = false
                                }
                            })
                        },
                    )
                }
            }
            if (!lib.loaded && lib.loading) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
            }
        }
    }
}

@Composable
private fun Field(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}
