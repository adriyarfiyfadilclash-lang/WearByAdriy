package com.adriy.wearbyadriy

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.adriy.wearbyadriy.ai.AiClient
import com.adriy.wearbyadriy.ai.AiSettingsStore
import com.adriy.wearbyadriy.data.AppPrefs
import com.adriy.wearbyadriy.data.AuthRepository
import com.adriy.wearbyadriy.data.Backend
import com.adriy.wearbyadriy.data.DataRepository
import com.adriy.wearbyadriy.data.WeatherRepository
import com.adriy.wearbyadriy.util.NetworkMonitor

class WearApp : Application(), ImageLoaderFactory {

    val auth: AuthRepository by lazy { AuthRepository(Backend.client) }
    val data: DataRepository by lazy { DataRepository(Backend.client, this) }
    val weather by lazy { WeatherRepository() }
    val aiStore by lazy { AiSettingsStore(this) }
    val aiClient by lazy { AiClient() }
    val prefs by lazy { AppPrefs(this) }
    val network by lazy { NetworkMonitor(this) }

    /**
     * Private wardrobe photos are cached in memory only, never on disk, so nothing remains
     * readable after logout. The memory cache is cleared on logout as well.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCachePolicy(CachePolicy.DISABLED)
        .crossfade(true)
        .build()
}
