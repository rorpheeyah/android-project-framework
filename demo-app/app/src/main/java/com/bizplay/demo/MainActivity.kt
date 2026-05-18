package com.bizplay.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bizplay.demo.navigation.AppNavGraph
import com.bizplay.design.theme.BizTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host. The navigation graph in [AppNavGraph] strings together
 * BootScreen → LoginScreen → (SelectInstitutionScreen) → HomeScreen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BizTheme {
                AppNavGraph()
            }
        }
    }
}
