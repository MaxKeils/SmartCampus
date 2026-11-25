package ru.smartcampus

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartCampusApplication : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val campus: CampusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkPalette else lightPalette) {
                CampusNavigation(campus)
            }
        }
    }
}

private val lightPalette = lightColorScheme(
    primary = Color(0xFF12593F), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2EFDC), onPrimaryContainer = Color(0xFF164734),
    secondaryContainer = Color(0xFFD2EFDC), onSecondaryContainer = Color(0xFF164734),
    background = Color(0xFFF3F7EF), onBackground = Color(0xFF182C24),
    surface = Color(0xFFF3F7EF), onSurface = Color(0xFF182C24),
    surfaceVariant = Color(0xFFE8EFE5), onSurfaceVariant = Color(0xFF405D50),
    tertiaryContainer = Color(0xFFF5E7A7), onTertiaryContainer = Color(0xFF504215),
    outlineVariant = Color(0xFFCAD8CC),
)

private val darkPalette = darkColorScheme(
    primary = Color(0xFFAFE9D2), onPrimary = Color(0xFF112D25),
    primaryContainer = Color(0xFF214638), onPrimaryContainer = Color(0xFFC2F0DC),
    secondaryContainer = Color(0xFF214638), onSecondaryContainer = Color(0xFFC2F0DC),
    background = Color(0xFF111C18), onBackground = Color(0xFFE3EFE7),
    surface = Color(0xFF111C18), onSurface = Color(0xFFE3EFE7),
    surfaceVariant = Color(0xFF202E28), onSurfaceVariant = Color(0xFFBACFC3),
    tertiaryContainer = Color(0xFF58491B), onTertiaryContainer = Color(0xFFF7E6A8),
    outlineVariant = Color(0xFF3D5449),
)
