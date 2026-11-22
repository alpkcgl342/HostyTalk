package com.hostytalk.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hostytalk.app.ui.AppViewModel
import com.hostytalk.app.ui.ConversationScreen
import com.hostytalk.app.ui.HistoryDetailScreen
import com.hostytalk.app.ui.HistoryScreen
import com.hostytalk.app.ui.HomeScreen
import com.hostytalk.app.ui.LoginScreen
import com.hostytalk.app.ui.Screen
import com.hostytalk.app.ui.theme.HostyTalkTheme

// AppCompatActivity: uygulama içi dil değişimi (AppCompatDelegate.setApplicationLocales) için gerekli.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HostyTalkTheme { Root() } }
    }
}

@Composable
private fun Root(vm: AppViewModel = viewModel()) {
    val token by vm.token.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val active = session
    when {
        token == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        token!!.isEmpty() -> LoginScreen()
        active != null -> ConversationScreen(active, onClose = vm::closeSession)
        else -> when (val s = screen) {
            Screen.Home -> HomeScreen(
                onOpen = vm::open,
                onLogout = { vm.logout() },
                onHistory = { vm.go(Screen.History) },
            )
            Screen.History -> HistoryScreen(onBack = vm::back, onOpen = { vm.go(Screen.HistoryDetail(it)) })
            is Screen.HistoryDetail -> HistoryDetailScreen(s.id, onBack = vm::back)
        }
    }
}
