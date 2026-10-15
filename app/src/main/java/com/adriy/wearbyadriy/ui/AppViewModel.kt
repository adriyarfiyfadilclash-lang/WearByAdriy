package com.adriy.wearbyadriy.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.adriy.wearbyadriy.WearApp
import com.adriy.wearbyadriy.ai.AiConfig
import com.adriy.wearbyadriy.ai.AiProvider
import com.adriy.wearbyadriy.data.Look
import com.adriy.wearbyadriy.data.OutfitSuggestion
import com.adriy.wearbyadriy.data.Profile
import com.adriy.wearbyadriy.data.ProfileUpdate
import com.adriy.wearbyadriy.data.ThemeMode
import com.adriy.wearbyadriy.data.WardrobeItem
import com.adriy.wearbyadriy.data.WardrobeItemInput
import com.adriy.wearbyadriy.data.Weather
import com.adriy.wearbyadriy.util.friendlyMessage
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LibraryState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val profile: Profile? = null,
    val items: List<WardrobeItem> = emptyList(),
    val looks: List<Look> = emptyList(),
) {
    fun item(id: String) = items.firstOrNull { it.id == id }
    fun look(id: String) = looks.firstOrNull { it.id == id }
}

sealed interface WeatherState {
    data object NoCity : WeatherState
    data object Loading : WeatherState
    data class Ready(val city: String, val weather: Weather) : WeatherState
    data class Failed(val city: String, val message: String) : WeatherState
}

/**
 * Holds the signed-in user's data for the whole app. Everything is loaded from Supabase and
 * written back to Supabase first; local state only changes after the server accepted the change.
 * Signing out wipes this state and the image cache.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val wear = app as WearApp
    val auth = wear.auth
    val data = wear.data
    val prefs = wear.prefs
    val online: StateFlow<Boolean> = wear.network.online

    val session: StateFlow<SessionStatus> = auth.sessionStatus

    private val _library = MutableStateFlow(LibraryState())
    val library = _library.asStateFlow()

    private val _weather = MutableStateFlow<WeatherState>(WeatherState.NoCity)
    val weather = _weather.asStateFlow()

    /** True after the user opened a password-reset link; the app then asks for a new password. */
    private val _recovery = MutableStateFlow(false)
    val recovery = _recovery.asStateFlow()

    /** An item the user asked to style from the item page; consumed by the Style tab. */
    val pendingPin = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadedFor: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            session.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val uid = status.session.user?.id ?: auth.userId()
                        if (uid != null && uid != loadedFor) {
                            wipeLocal()
                            loadedFor = uid
                            refresh()
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        loadedFor = null
                        wipeLocal()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun userId(): String? = auth.userId()
    fun email(): String? = auth.email()

    fun toast(message: String) { _messages.trySend(message) }

    fun setRecovery(value: Boolean) { _recovery.value = value }

    private fun wipeLocal() {
        loadJob?.cancel()
        _library.value = LibraryState()
        pendingPin.value = null
        _weather.value = WeatherState.NoCity
        data.clearCaches()
        runCatching { getApplication<Application>().imageLoader.memoryCache?.clear() }
    }

    fun refresh() {
        val uid = auth.userId() ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _library.update { it.copy(loading = true, error = null) }
            try {
                val profile = async { data.profile(uid) }
                val items = async { data.items() }
                val looks = async { data.looks() }
                val p = profile.await()
                _library.update {
                    it.copy(loading = false, loaded = true, profile = p, items = items.await(), looks = looks.await())
                }
                loadWeather(p)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _library.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun loadWeather(profile: Profile? = _library.value.profile) {
        val lat = profile?.latitude
        val lon = profile?.longitude
        val city = profile?.city
        if (lat == null || lon == null || city.isNullOrBlank()) {
            _weather.value = WeatherState.NoCity
            return
        }
        viewModelScope.launch {
            _weather.value = WeatherState.Loading
            _weather.value = try {
                WeatherState.Ready(city, wear.weather.forecast(lat, lon))
            } catch (e: Exception) {
                WeatherState.Failed(city, e.friendlyMessage())
            }
        }
    }

    suspend fun searchCity(query: String) = wear.weather.searchCity(query)

    // ---------------- auth ----------------

    suspend fun signOut() {
        _recovery.value = false
        auth.signOut()
        wipeLocal()
    }

    suspend fun deleteAccount() {
        val uid = auth.userId()
        auth.deleteAccount()
        uid?.let { wear.aiStore.deleteAllFor(it) }
        wipeLocal()
    }

    // ---------------- profile ----------------

    suspend fun saveProfile(update: ProfileUpdate) {
        val uid = auth.userId() ?: return
        data.updateProfile(uid, update)
        val p = data.profile(uid)
        _library.update { it.copy(profile = p) }
        loadWeather(p)
    }

    // ---------------- wardrobe ----------------

    suspend fun saveItem(existing: WardrobeItem?, input: WardrobeItemInput, newPhoto: Uri?, removePhoto: Boolean): WardrobeItem {
        val uid = auth.userId() ?: error("Sesi berakhir")
        var uploaded: String? = null
        val path = when {
            newPhoto != null -> data.uploadImage(uid, newPhoto).also { uploaded = it }
            removePhoto -> null
            else -> existing?.imagePath
        }
        val saved = try {
            val body = input.copy(imagePath = path)
            if (existing == null) data.addItem(body) else data.updateItem(existing.id, body)
        } catch (e: Exception) {
            uploaded?.let { runCatching { data.deleteImage(it) } }   // don't leave an orphaned photo
            throw e
        }
        // The old photo is removed only after the row points at the new one.
        val old = existing?.imagePath
        if (old != null && old != saved.imagePath) runCatching { data.deleteImage(old) }
        _library.update { s ->
            val list = if (existing == null) listOf(saved) + s.items else s.items.map { if (it.id == saved.id) saved else it }
            s.copy(items = list)
        }
        return saved
    }

    suspend fun toggleItemFavorite(item: WardrobeItem) {
        data.setItemFavorite(item.id, !item.isFavorite)
        _library.update { s -> s.copy(items = s.items.map { if (it.id == item.id) it.copy(isFavorite = !item.isFavorite) else it }) }
    }

    suspend fun toggleItemAvailable(item: WardrobeItem) {
        data.setItemAvailable(item.id, !item.isAvailable)
        _library.update { s -> s.copy(items = s.items.map { if (it.id == item.id) it.copy(isAvailable = !item.isAvailable) else it }) }
    }

    suspend fun deleteItem(item: WardrobeItem) {
        data.deleteItem(item)
        _library.update { s ->
            s.copy(
                items = s.items.filterNot { it.id == item.id },
                looks = s.looks.map { l -> l.copy(lookItems = l.lookItems.filterNot { it.itemId == item.id }) },
            )
        }
    }

    // ---------------- looks ----------------

    suspend fun saveLook(s: OutfitSuggestion, occasion: String?): Look {
        val look = data.saveLook(s, occasion)
        _library.update { it.copy(looks = listOf(look) + it.looks) }
        return look
    }

    suspend fun toggleLookFavorite(look: Look) {
        data.setLookFavorite(look.id, !look.isFavorite)
        _library.update { s -> s.copy(looks = s.looks.map { if (it.id == look.id) it.copy(isFavorite = !look.isFavorite) else it }) }
    }

    suspend fun renameLook(look: Look, name: String, description: String?) {
        data.renameLook(look.id, name, description)
        _library.update { s -> s.copy(looks = s.looks.map { if (it.id == look.id) it.copy(name = name, description = description) else it }) }
    }

    suspend fun updateLookItems(look: Look, itemIds: List<String>) {
        data.setLookItems(look.id, itemIds)
        val refs = itemIds.mapIndexed { i, id -> com.adriy.wearbyadriy.data.LookItemRef(id, i) }
        _library.update { s -> s.copy(looks = s.looks.map { if (it.id == look.id) it.copy(lookItems = refs) else it }) }
    }

    suspend fun toggleWornToday(look: Look) {
        val today = LocalDate.now().toString()
        val dates = if (today in look.wornDates) look.wornDates - today else (look.wornDates + today).sortedDescending()
        data.setWornDates(look.id, dates)
        _library.update { s -> s.copy(looks = s.looks.map { if (it.id == look.id) it.copy(wornDates = dates) else it }) }
    }

    suspend fun deleteLook(look: Look) {
        data.deleteLook(look.id)
        _library.update { s -> s.copy(looks = s.looks.filterNot { it.id == look.id }) }
    }

    // ---------------- AI settings (device only, encrypted) ----------------

    fun aiConfig(): AiConfig? = auth.userId()?.let { wear.aiStore.load(it) }
    fun aiConfigFor(provider: AiProvider): AiConfig? = auth.userId()?.let { wear.aiStore.loadFor(it, provider) }
    fun saveAiConfig(config: AiConfig) { auth.userId()?.let { wear.aiStore.save(it, config) } }
    fun deleteAiKey(provider: AiProvider) { auth.userId()?.let { wear.aiStore.deleteKey(it, provider) } }
    suspend fun testAi(config: AiConfig) = wear.aiClient.test(config)
    val aiClient get() = wear.aiClient

    fun setTheme(mode: ThemeMode) = prefs.setTheme(mode)
}
