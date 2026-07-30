package com.maquis.caisse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Typographie volontairement grande et grasse (prix, quantités) pour
// rester lisible en usage rapide, debout, au comptoir.
val MaquisTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
)
