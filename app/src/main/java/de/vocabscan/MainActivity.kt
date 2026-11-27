package de.vocabscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.vocabscan.scan.OcrImporter
import de.vocabscan.ui.VocabApp
import de.vocabscan.ui.VocabTheme
import de.vocabscan.ui.VocabViewModel
import de.vocabscan.ui.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VocabScanApplication
        setContent {
            var themeMode by remember { mutableStateOf(ThemePreference.read(app)) }
            VocabTheme(themeMode) {
                val model: VocabViewModel = viewModel(factory = viewModelFactory {
                    initializer { VocabViewModel(app.repository, OcrImporter(app), createSavedStateHandle(), app) }
                })
                VocabApp(model, themeMode, onThemeChange = {
                    ThemePreference.write(app, it)
                    themeMode = it
                })
            }
        }
    }
}
