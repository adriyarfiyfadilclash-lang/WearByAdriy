package com.adriy.wearbyadriy.data

import com.adriy.wearbyadriy.util.UserError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Weather(val temperature: Double, val min: Double, val max: Double, val code: Int) {
    val label: String get() = when (code) {
        0 -> "Cerah"
        1, 2 -> "Cerah berawan"
        3 -> "Berawan"
        45, 48 -> "Berkabut"
        in 51..57 -> "Gerimis"
        in 61..67, in 80..82 -> "Hujan"
        in 71..77, 85, 86 -> "Salju"
        in 95..99 -> "Badai petir"
        else -> "Berawan"
    }
    val rainy: Boolean get() = code in 51..67 || code in 80..82 || code in 95..99
    /** The coarse condition the stylist understands. */
    val condition: String get() = when {
        rainy -> "Hujan"
        temperature >= 30 -> "Panas"
        temperature >= 26 -> "Hangat"
        temperature >= 20 -> "Sejuk"
        else -> "Dingin"
    }
}

data class Place(val name: String, val region: String?, val country: String?, val latitude: Double, val longitude: Double) {
    val display: String get() = listOfNotNull(name, region, country).distinct().joinToString(", ")
}

/** Open-Meteo: free, no API key, nothing personal sent beyond coordinates or a city name. */
class WeatherRepository {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forecast(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("current", "temperature_2m,weather_code")
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min")
            .addQueryParameter("forecast_days", "1")
            .addQueryParameter("timezone", "auto")
            .build()
        val body = fetch(url.toString())
        runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            val cur = o.getValue("current").jsonObject
            val daily = o.getValue("daily").jsonObject
            Weather(
                temperature = cur.getValue("temperature_2m").jsonPrimitive.double,
                min = daily.getValue("temperature_2m_min").jsonArray[0].jsonPrimitive.double,
                max = daily.getValue("temperature_2m_max").jsonArray[0].jsonPrimitive.double,
                code = cur.getValue("weather_code").jsonPrimitive.int,
            )
        }.getOrElse { throw UserError("Data cuaca tidak bisa dibaca.") }
    }

    suspend fun searchCity(query: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "6")
            .addQueryParameter("language", "id")
            .build()
        val body = fetch(url.toString())
        runCatching {
            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return@runCatching emptyList()
            results.map { r ->
                val o = r.jsonObject
                Place(
                    name = o.getValue("name").jsonPrimitive.content,
                    region = o["admin1"]?.jsonPrimitive?.contentOrNull,
                    country = o["country"]?.jsonPrimitive?.contentOrNull,
                    latitude = o.getValue("latitude").jsonPrimitive.double,
                    longitude = o.getValue("longitude").jsonPrimitive.double,
                )
            }
        }.getOrElse { throw UserError("Hasil pencarian kota tidak bisa dibaca.") }
    }

    private fun fetch(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw UserError("Layanan cuaca sedang bermasalah (kode ${resp.code}).")
            resp.body?.string().orEmpty()
        }
}
