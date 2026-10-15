package com.adriy.wearbyadriy.ui.auth

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adriy.wearbyadriy.ui.components.ErrorCard
import com.adriy.wearbyadriy.ui.components.LoadingButton
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.util.friendlyMessage
import kotlinx.coroutines.launch

private enum class AuthPage { LOGIN, REGISTER, FORGOT }

fun validEmail(e: String) = Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()

/** Shown whenever there is no session. Nothing private is composed while this is on screen. */
@Composable
fun AuthFlow() {
    var page by rememberSaveable { mutableStateOf(AuthPage.LOGIN) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    AuthFrame {
        when (page) {
            AuthPage.LOGIN -> LoginForm(
                notice = notice,
                onRegister = { notice = null; page = AuthPage.REGISTER },
                onForgot = { notice = null; page = AuthPage.FORGOT },
            )
            AuthPage.REGISTER -> RegisterForm(
                onBack = { page = AuthPage.LOGIN },
                onNeedsConfirmation = { email ->
                    notice = "Kami mengirim tautan konfirmasi ke $email. Buka tautan itu di ponsel ini, lalu masuk."
                    page = AuthPage.LOGIN
                },
            )
            AuthPage.FORGOT -> ForgotForm(
                onBack = { page = AuthPage.LOGIN },
                onSent = { email ->
                    notice = "Jika $email terdaftar, tautan untuk membuat password baru sudah dikirim. Buka tautannya di ponsel ini."
                    page = AuthPage.LOGIN
                },
            )
        }
    }
}

@Composable
fun AuthFrame(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            Text("WearByAdriy", style = MaterialTheme.typography.headlineLarge)
            Text("Lemari digital & stylist pribadimu", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(40.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, ime: ImeAction = ImeAction.Done, supporting: String? = null) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Sembunyikan password" else "Tampilkan password")
            }
        },
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LoginForm(notice: String?, onRegister: () -> Unit, onForgot: () -> Unit) {
    val vm = LocalAppViewModel.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Masuk", style = MaterialTheme.typography.headlineSmall)
        if (notice != null) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(notice, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        EmailField(email) { email = it; error = null }
        PasswordField(password, { password = it; error = null }, "Password")
        error?.let { ErrorCard(it) }
        LoadingButton("Masuk", loading, modifier = Modifier.fillMaxWidth(), onClick = {
            when {
                !validEmail(email) -> error = "Masukkan alamat email yang valid."
                password.isEmpty() -> error = "Masukkan password."
                else -> scope.launch {
                    loading = true
                    error = null
                    // The session flow switches the app to the main screens only when Supabase returns a session.
                    try { vm.setRecovery(false); vm.auth.signIn(email.trim(), password) } catch (e: Exception) { error = e.friendlyMessage() }
                    loading = false
                }
            }
        })
        TextButton(onClick = onForgot, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Lupa password?") }
        Spacer(Modifier.height(8.dp))
        Text("Belum punya akun?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        com.adriy.wearbyadriy.ui.components.SecondaryButton("Buat akun", onRegister, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RegisterForm(onBack: () -> Unit, onNeedsConfirmation: (String) -> Unit) {
    val vm = LocalAppViewModel.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Buat akun", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it; error = null }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        EmailField(email) { email = it; error = null }
        PasswordField(password, { password = it; error = null }, "Password", ImeAction.Next, "Minimal 8 karakter, berisi huruf dan angka")
        PasswordField(confirm, { confirm = it; error = null }, "Ulangi password")
        error?.let { ErrorCard(it) }
        LoadingButton("Daftar", loading, modifier = Modifier.fillMaxWidth(), onClick = {
            val problem = passwordProblem(password, confirm)
            when {
                name.isBlank() -> error = "Masukkan nama kamu."
                !validEmail(email) -> error = "Masukkan alamat email yang valid."
                problem != null -> error = problem
                else -> scope.launch {
                    loading = true
                    error = null
                    try {
                        val needsConfirm = vm.auth.signUp(email.trim(), password, name.trim())
                        if (needsConfirm) onNeedsConfirmation(email.trim())
                    } catch (e: Exception) {
                        error = e.friendlyMessage()
                    }
                    loading = false
                }
            }
        })
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Sudah punya akun? Masuk") }
    }
}

fun passwordProblem(password: String, confirm: String): String? = when {
    password.length < 8 -> "Password minimal 8 karakter."
    !password.any { it.isLetter() } || !password.any { it.isDigit() } -> "Password harus berisi huruf dan angka."
    password != confirm -> "Kedua password tidak sama."
    else -> null
}

@Composable
private fun ForgotForm(onBack: () -> Unit, onSent: (String) -> Unit) {
    val vm = LocalAppViewModel.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lupa password", style = MaterialTheme.typography.headlineSmall)
        Text("Masukkan email akunmu. Kami akan mengirim tautan untuk membuat password baru.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EmailField(email) { email = it; error = null }
        error?.let { ErrorCard(it) }
        LoadingButton("Kirim tautan", loading, modifier = Modifier.fillMaxWidth(), onClick = {
            if (!validEmail(email)) error = "Masukkan alamat email yang valid."
            else scope.launch {
                loading = true
                try { vm.auth.sendPasswordReset(email.trim()); onSent(email.trim()) } catch (e: Exception) { error = e.friendlyMessage() }
                loading = false
            }
        })
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Kembali ke Masuk") }
    }
}

/** Shown after a password-recovery link signed the user in. */
@Composable
fun SetNewPasswordScreen(onDone: () -> Unit) {
    val vm = LocalAppViewModel.current
    val scope = rememberCoroutineScope()
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AuthFrame {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Buat password baru", style = MaterialTheme.typography.headlineSmall)
            Text("Akun: ${vm.email().orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PasswordField(password, { password = it; error = null }, "Password baru", ImeAction.Next, "Minimal 8 karakter, berisi huruf dan angka")
            PasswordField(confirm, { confirm = it; error = null }, "Ulangi password baru")
            error?.let { ErrorCard(it) }
            LoadingButton("Simpan password", loading, modifier = Modifier.fillMaxWidth(), onClick = {
                val problem = passwordProblem(password, confirm)
                if (problem != null) error = problem
                else scope.launch {
                    loading = true
                    try {
                        vm.auth.updatePassword(password)
                        vm.toast("Password berhasil diganti.")
                        onDone()
                    } catch (e: Exception) { error = e.friendlyMessage() }
                    loading = false
                }
            })
            TextButton(onClick = { scope.launch { vm.signOut() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Batal dan keluar") }
        }
    }
}
