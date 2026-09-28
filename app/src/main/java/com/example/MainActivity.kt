package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CourierNavScreen
import com.example.ui.CourierViewModel
import com.example.ui.theme.CourierNavTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val courierViewModel: CourierViewModel = viewModel()
            val isNightMode by courierViewModel.isNightMode.collectAsState()

            CourierNavTheme(darkTheme = isNightMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CourierNavScreen(viewModel = courierViewModel)
                }
            }
        }
    }
}
