package com.example.mediqr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mediqr.navigation.AppNavHost
import com.example.mediqr.ui.theme.MediQRTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediQRTheme {
                AppNavHost()
            }
        }
    }
}
