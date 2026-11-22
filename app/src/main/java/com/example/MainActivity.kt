package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebase.configureAppCheck
import com.example.ui.DocFolioApp
import com.example.ui.theme.DocFolioTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase ops
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge display and render Compose UI directly
        enableEdgeToEdge()
        setContent {
            DocFolioTheme {
                DocFolioApp(currentUser = Firebase.auth.currentUser)
            }
        }
    }
}
