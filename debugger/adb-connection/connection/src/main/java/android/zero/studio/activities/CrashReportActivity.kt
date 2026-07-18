package android.zero.studio.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import android.zero.studio.core.common.CompositionLocals
import android.zero.studio.core.common.LocalSeedColor
import android.zero.studio.core.domain.provider.SeedColorProvider
import android.zero.studio.core.presentation.theme.AshellYouTheme
import android.zero.studio.crashreporter.presentation.screens.CrashReportScreen

@AndroidEntryPoint
class CrashReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            CompositionLocals {
                SeedColorProvider.setSeedColor(LocalSeedColor.current)

                AshellYouTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) { CrashReportScreen() }
                }
            }
        }
    }
}