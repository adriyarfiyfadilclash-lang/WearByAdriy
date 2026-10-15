package com.adriy.wearbyadriy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.adriy.wearbyadriy.data.Backend
import com.adriy.wearbyadriy.ui.AppViewModel
import com.adriy.wearbyadriy.ui.ConfigMissingScreen
import com.adriy.wearbyadriy.ui.WearRoot
import com.adriy.wearbyadriy.ui.theme.WearTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!Backend.isConfigured) {
            setContent { WearTheme { ConfigMissingScreen() } }
            return
        }
        val vm: AppViewModel by viewModels()
        handleLink(intent, vm)
        setContent { WearRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Backend.isConfigured) {
            val vm: AppViewModel by viewModels()
            handleLink(intent, vm)
        }
    }

    private fun handleLink(intent: Intent?, vm: AppViewModel) {
        if (intent?.data?.scheme != "wearbyadriy") return
        val recovery = (application as WearApp).auth.handleDeepLink(intent)
        if (recovery) vm.setRecovery(true)
    }
}
