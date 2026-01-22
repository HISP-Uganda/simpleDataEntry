package com.ash.simpledataentry.presentation.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.BaseScreen

@Composable
fun AboutScreen(
    navController: NavController
) {
    BaseScreen(
        title = "About",
        subtitle = "App info",
        navController = navController
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Icon/Logo placeholder
            Card(
                modifier = Modifier.size(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DHIS2",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // App Name and Version
            Text(
                text = "DHIS2 Data Entry",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Build: ${System.currentTimeMillis() / 1000}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Description
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About This App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "A simplified Android application for DHIS2 data entry. This app provides an intuitive interface for entering and managing health data in DHIS2 systems.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                }
            }

            // Features
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Key Features",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val features = listOf(
                        "• Simplified data entry forms with accordion layout",
                        "• Offline data storage and caching",
                        "• Real-time data synchronization with DHIS2",
                        "• Multiple account support with secure storage",
                        "• Intuitive user interface with Material Design",
                        "• Advanced filtering and search capabilities",
                        "• Data validation and error handling",
                        "• Period-based data organization",
                        "• Organization unit management",
                        "• Draft saving and auto-recovery"
                    )
                    features.forEach { feature ->
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Technical Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Technical Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Built with Android Jetpack Compose",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• DHIS2 Android SDK integration",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Room database for offline storage",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Hilt dependency injection",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Material Design 3 components",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // System Requirements
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "System Requirements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Android 7.0 (API level 24) or higher",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• 2GB RAM minimum, 4GB recommended",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• 100MB free storage space",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Internet connection for synchronization",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• DHIS2 server version 2.35 or higher",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Credits
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Credits & Acknowledgments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Developed for the DHIS2 ecosystem",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Developed by HISP Uganda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "DHIS2 is a product of HISP (Health Information Systems Programme)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Special thanks to the DHIS2 community and contributors",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Copyright
            Text(
                text = "© 2025 DHIS2 Data Entry App",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
