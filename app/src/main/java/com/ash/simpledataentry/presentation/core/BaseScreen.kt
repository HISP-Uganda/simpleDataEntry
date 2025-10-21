package com.ash.simpledataentry.presentation.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import org.hisp.dhis.mobile.ui.designsystem.component.Title
import org.hisp.dhis.mobile.ui.designsystem.component.TopBar
import org.hisp.dhis.mobile.ui.designsystem.component.TopBarType
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    title: String,
    navController: NavController,
    navigationIcon: @Composable (() -> Unit)? = {
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back"
            )
        }
    },
    actions: @Composable (RowScope.() -> Unit) = {},
    floatingActionButton: @Composable (() -> Unit)? = null,
    // PHASE 4: Top bar progress indicator
    showProgress: Boolean = false,
    progress: Float? = null, // null = indeterminate, 0.0-1.0 = determinate
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            Box {
                TopBar(
                    title = { Title(text = title, textColor = TextColor.OnPrimary) },
                    type = TopBarType.CENTERED,
                    navigationIcon = navigationIcon!!,
                    actions = actions,
                    colors = TopAppBarColors(
                        containerColor = SurfaceColor.Primary,
                        titleContentColor = TextColor.OnSurface,
                        navigationIconContentColor = TextColor.OnSurface,
                        actionIconContentColor = TextColor.OnSurface,
                        scrolledContainerColor = SurfaceColor.Container,
                    ),
                )
                // PHASE 4: Progress indicator beneath top bar
                TopBarProgress(
                    isVisible = showProgress,
                    progress = progress
                )
            }
        },
        floatingActionButton = floatingActionButton ?: {},
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            content()
        }
    }
}