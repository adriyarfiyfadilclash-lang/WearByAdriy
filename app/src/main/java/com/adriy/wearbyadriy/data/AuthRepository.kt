package com.adriy.wearbyadriy.data

import android.content.Intent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.StateFlow
import com.adriy.wearbyadriy.util.UserError

class AuthRepository(private val client: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    fun userId(): String? = client.auth.currentUserOrNull()?.id
    fun email(): String? = client.auth.currentUserOrNull()?.email

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** Returns true when the project requires email confirmation before the first login. */
    suspend fun signUp(email: String, password: String, name: String): Boolean {
        client.auth.signUpWith(Email, redirectUrl = Backend.REDIRECT_URL) {
            this.email = email
            this.password = password
            data = kotlinx.serialization.json.buildJsonObject {
                put("display_name", kotlinx.serialization.json.JsonPrimitive(name))
            }
        }
        return client.auth.currentSessionOrNull() == null
    }

    suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email, redirectUrl = Backend.REDIRECT_URL + "?type=recovery")
    }

    suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    /** Handles email-confirmation and password-recovery links. Returns true for a recovery link. */
    fun handleDeepLink(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (data.scheme != "wearbyadriy") return false
        val recovery = data.toString().contains("type=recovery")
        client.handleDeeplinks(intent)
        return recovery
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
            .onFailure { runCatching { client.auth.signOut(SignOutScope.LOCAL) } }
        runCatching { client.auth.clearSession() }
    }

    /** Calls the delete-account Edge Function, which removes the user, their rows and their photos. */
    suspend fun deleteAccount() {
        val response = client.functions.invoke("delete-account")
        if (!response.status.isSuccess()) throw UserError("Akun belum bisa dihapus (kode ${response.status.value}). Coba lagi.")
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
        runCatching { client.auth.clearSession() }
    }
}
