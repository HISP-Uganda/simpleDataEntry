package com.ash.simpledataentry.presentation.dataEntry.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SectionNavigator(
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    currentSectionIndex: Int,
    totalSections: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = onPreviousClick,
            enabled = currentSectionIndex > 0,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Previous Section")
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = "${currentSectionIndex + 1} / $totalSections",
            modifier = Modifier.weight(0.5f)
                .wrapContentWidth(align = androidx.compose.ui.Alignment.CenterHorizontally)
                .align(androidx.compose.ui.Alignment.CenterVertically)
        )

        Spacer(Modifier.width(16.dp))

        Button(
            onClick = onNextClick,
            enabled = currentSectionIndex < totalSections - 1,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = "Next Section")
        }
    }
}
