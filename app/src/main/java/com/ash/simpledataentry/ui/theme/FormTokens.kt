package com.ash.simpledataentry.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FormColors(
    val gridHeaderBackground: Color,
    val gridHeaderText: Color,
    val gridRowHeaderBackground: Color,
    val gridRowHeaderText: Color,
    val gridCellBackground: Color,
    val gridCellAltBackground: Color,
    val gridCellText: Color,
    val gridCellPlaceholder: Color,
    val gridBorder: Color,
    val gridBorderFocused: Color,
    val gridBorderError: Color
)

data class FormDimensions(
    val rowHorizontalPadding: Dp,
    val rowVerticalPadding: Dp,
    val sectionCornerRadius: Dp,
    val fieldCornerRadius: Dp,
    val gridCellHeight: Dp
)

data class FormTypography(
    val gridHeader: TextStyle,
    val gridRowHeader: TextStyle,
    val gridCell: TextStyle,
    val sectionTitle: TextStyle
)

val LocalFormColors = staticCompositionLocalOf<FormColors> {
    error("FormColors not provided")
}

val LocalFormDimensions = staticCompositionLocalOf<FormDimensions> {
    FormDimensions(
        rowHorizontalPadding = 16.dp,
        rowVerticalPadding = 6.dp,
        sectionCornerRadius = 14.dp,
        fieldCornerRadius = 10.dp,
        gridCellHeight = 52.dp
    )
}

val LocalFormTypography = staticCompositionLocalOf<FormTypography> {
    FormTypography(
        gridHeader = TextStyle(fontSize = 12.sp),
        gridRowHeader = TextStyle(fontSize = 13.sp),
        gridCell = TextStyle(fontSize = 16.sp),
        sectionTitle = TextStyle(fontSize = 16.sp)
    )
}
