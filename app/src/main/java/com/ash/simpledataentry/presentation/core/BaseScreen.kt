package com.ash.simpledataentry.presentation.core

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.ash.simpledataentry.data.sync.SyncStatusController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    title: String,
    subtitle: String? = null,
    navController: NavController,
    usePrimaryTopBar: Boolean = false,
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
    syncStatusController: SyncStatusController? = null,
    showProgress: Boolean = false,
    progress: Float? = null,
    content: @Composable () -> Unit
) {
    val syncShowProgress by syncStatusController?.showTopBarProgress?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val syncProgressValue by syncStatusController?.topBarProgressValue?.collectAsState()
        ?: remember { mutableStateOf<Float?>(null) }

    val effectiveShowProgress = showProgress || syncShowProgress
    val effectiveProgress = progress ?: syncProgressValue

    val topBarColor = if (usePrimaryTopBar) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val titleColor = if (usePrimaryTopBar) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (usePrimaryTopBar) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = MaterialTheme.colorScheme.background

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val barColorInt = topBarColor.toArgb()
        window.statusBarColor = barColorInt
        window.navigationBarColor = backgroundColor.toArgb()

        val useDarkIcons = topBarColor.luminance() > 0.5f
        val navUseDarkIcons = backgroundColor.luminance() > 0.5f
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = useDarkIcons
        insetsController.isAppearanceLightNavigationBars = navUseDarkIcons
    }

    Scaffold(
        topBar = {
            Box {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtitleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        navigationIcon?.invoke()
                    },
                    actions = {
                        statusIndicator?.invoke()
                        actions()
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = topBarColor,
                        titleContentColor = titleColor,
                        navigationIconContentColor = titleColor,
                        actionIconContentColor = titleColor
                    )
                )

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
