package com.example.mapicomandas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mapicomandas.ui.navigation.MapiNavGraph
import com.example.mapicomandas.ui.theme.MapiComandasTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MapiComandasTheme {
                MapiNavGraph()
            }
        }
    }
}
