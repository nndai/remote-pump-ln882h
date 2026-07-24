package com.nndai.remotepump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nndai.remotepump.data.di.PumpRepositoryProvider
import com.nndai.remotepump.ui.navigation.AppNavigation
import com.nndai.remotepump.ui.theme.RemotePumpTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PumpRepositoryProvider.init(this)
        enableEdgeToEdge()
        setContent {
            RemotePumpTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val repo = PumpRepositoryProvider.provide()
        if (repo.isLogEnabled.value) {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch {
                repo.setLogMqtt(false)
            }
        }
    }
}