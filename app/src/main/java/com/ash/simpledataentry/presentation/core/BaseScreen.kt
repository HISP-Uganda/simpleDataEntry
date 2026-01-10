package com.ash.simpledataentry.presentation.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.ash.simpledataentry.data.sync.SyncStatusController
import org.hisp.dhis.mobile.ui.designsystem.component.Title
import org.hisp.dhis.mobile.ui.designsystem.component.TopBar
import org.hisp.dhis.mobile.ui.designsystem.component.TopBarType
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    title: String,
    subtitle: String? = null,
    navController: NavController,
    navigationIcon: @Composable (() -> Unit)? = {
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    },
    statusIndicator: (@Composable () -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    floatingActionButton: @Composable (() -> Unit)? = null,
    // PHASE 4: Top bar progress indicator
    syncStatusController: SyncStatusController? = null,
    showProgress: Boolean = false,
    progress: Float? = null, // null = indeterminate, 0.0-1.0 = determinate
    content: @Composable () -> Unit
) {
    val syncShowProgress by syncStatusController?.showTopBarProgress?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val syncProgressValue by syncStatusController?.topBarProgressValue?.collectAsState()
        ?: remember { mutableStateOf<Float?>(null) }

    val effectiveShowProgress = showProgress || syncShowProgress
    val effectiveProgress = progress ?: syncProgressValue
    val titleContentColor = MaterialTheme.colorScheme.onPrimary
    val subtitleColor = titleContentColor.copy(alpha = 0.75f)

    Scaffold(
        topBar = {
            Box {
                TopBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Title(text = title, textColor = TextColor.OnPrimary)
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtitleColor
                                )
                            }
                        }
                    },
                    type = TopBarType.CENTERED,
                    navigationIcon = navigationIcon!!,
                    actions = {
                        statusIndicator?.invoke()
                        actions()
                    },
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
                    isVisible = effectiveShowProgress,
                    progress = effectiveProgress
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
