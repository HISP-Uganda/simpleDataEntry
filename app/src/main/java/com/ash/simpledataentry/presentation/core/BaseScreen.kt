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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.NavController
import com.ash.simpledataentry.data.sync.SyncStatusController
import org.hisp.dhis.mobile.ui.designsystem.component.Title
import org.hisp.dhis.mobile.ui.designsystem.component.TopBar
import org.hisp.dhis.mobile.ui.designsystem.component.TopBarType
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    title: String,
    subtitle: String? = null,
    navController: NavController,
    usePrimaryTopBar: Boolean = true,
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
    val titleTextColor = if (usePrimaryTopBar) TextColor.OnPrimary else TextColor.OnSurface
    val subtitleColor = if (usePrimaryTopBar) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    val view = LocalView.current
    val isLightTheme = !isSystemInDarkTheme()
    val statusBarColor = if (isLightTheme) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primary
    }
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val colorInt = statusBarColor.toArgb()
        window.statusBarColor = colorInt
        window.navigationBarColor = colorInt
        val insetsController = WindowInsetsControllerCompat(window, view)
        val useDarkIcons = isLightTheme && statusBarColor.luminance() > 0.5f
        insetsController.isAppearanceLightStatusBars = useDarkIcons
        insetsController.isAppearanceLightNavigationBars = useDarkIcons
    }

    Scaffold(
        topBar = {
            Box {
                TopBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Title(text = title, textColor = titleTextColor)
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
                        containerColor = if (usePrimaryTopBar) SurfaceColor.Primary else SurfaceColor.Surface,
                        titleContentColor = if (usePrimaryTopBar) TextColor.OnSurface else TextColor.OnSurface,
                        navigationIconContentColor = TextColor.OnSurface,
                        actionIconContentColor = TextColor.OnSurface,
                        scrolledContainerColor = if (usePrimaryTopBar) SurfaceColor.Container else SurfaceColor.Surface,
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
