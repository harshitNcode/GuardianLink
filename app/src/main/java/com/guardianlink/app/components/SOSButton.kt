package com.guardianlink.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SOSButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassSOSButton(
        modifier = modifier,
        onClick = onClick
    )
}