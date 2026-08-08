package com.royermethod.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.royermethod.app.ui.logo.RoyerLogo
import com.royermethod.app.ui.theme.Ink

/**
 * Cabecera de marca. Es la misma en inicio y en las pantallas de dentro, para
 * que el logo no salte de sitio al navegar.
 */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoyerLogo(size = 34.dp, glow = 0.8f)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "ROYER METHOD",
            style = MaterialTheme.typography.titleMedium,
            color = Ink.White90
        )
        Spacer(Modifier.weight(1f))
        GlowDot()
    }
}
