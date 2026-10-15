package com.adriy.wearbyadriy.util

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** An error whose message is already written for the person using the app. */
class UserError(message: String) : Exception(message)

fun Throwable.friendlyMessage(): String {
    if (this is UserError) return message ?: "Terjadi kesalahan."
    val raw = (message ?: "").lowercase()
    return when {
        this is UnknownHostException || this is ConnectException ->
            "Tidak ada koneksi internet. Periksa koneksi lalu coba lagi."
        this is SocketTimeoutException || raw.contains("timeout") || raw.contains("timed out") ->
            "Waktu tunggu habis. Coba lagi beberapa saat lagi."
        this is HttpRequestException ->
            "Tidak bisa terhubung ke server. Periksa koneksi lalu coba lagi."
        this is RestException -> restMessage(raw)
        this is IOException -> "Gangguan jaringan. Periksa koneksi lalu coba lagi."
        else -> message?.takeIf { it.isNotBlank() } ?: "Terjadi kesalahan."
    }
}

private fun restMessage(raw: String): String = when {
    raw.contains("invalid login credentials") -> "Email atau password salah."
    raw.contains("email not confirmed") -> "Email belum dikonfirmasi. Buka tautan konfirmasi di email kamu."
    raw.contains("already registered") || raw.contains("already been registered") -> "Email ini sudah terdaftar. Silakan masuk."
    raw.contains("password should be") || raw.contains("weak") -> "Password terlalu lemah. Gunakan minimal 8 karakter."
    raw.contains("rate limit") || raw.contains("too many") -> "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
    raw.contains("jwt expired") || raw.contains("refresh token") -> "Sesi kamu sudah berakhir. Silakan masuk lagi."
    raw.contains("row-level security") || raw.contains("permission denied") -> "Akses ditolak untuk data ini."
    raw.contains("payload too large") || raw.contains("exceeded the maximum") -> "Ukuran foto terlalu besar."
    raw.contains("mime type") -> "Format foto tidak didukung. Gunakan JPG, PNG, atau WebP."
    else -> "Server menolak permintaan. Coba lagi."
}
