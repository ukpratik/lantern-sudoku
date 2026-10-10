// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PlaceholderScreen() }
    }
}

// M0 placeholder. The real theme (four palettes, fonts) arrives with the Play tab in M2 and M5.
private val PaperColors = lightColorScheme(background = Color(0xFFFBF6EC), onBackground = Color(0xFF1E2A47))
private val NightColors = darkColorScheme(background = Color(0xFF14161D), onBackground = Color(0xFFECE6DA))

@Composable
private fun PlaceholderScreen() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) NightColors else PaperColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    PlaceholderScreen()
}
