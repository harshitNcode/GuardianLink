package com.guardianlink.app.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guardianlink.app.ui.theme.AlertRed
import com.guardianlink.app.ui.theme.PrimaryBlue
import com.guardianlink.app.ui.theme.SecondaryBlue

@Composable
fun CustomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    containerColor: Color = PrimaryBlue,
    contentColor: Color = Color.White
) {
    val variant = when {
        outlined -> LiquidButtonVariant.FROSTED
        containerColor == AlertRed || containerColor == Color(0xFFEF4444) -> LiquidButtonVariant.EMERGENCY
        containerColor == SecondaryBlue || containerColor == Color(0xFF06B6D4) -> LiquidButtonVariant.SECONDARY
        else -> LiquidButtonVariant.PRIMARY
    }

    LiquidPillButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = variant,
        height = 52.dp
    )
}