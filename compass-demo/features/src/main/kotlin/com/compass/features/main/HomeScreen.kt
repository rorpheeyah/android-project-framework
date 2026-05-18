package com.compass.features.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compass.design.components.CompassPrimaryButton
import com.compass.design.components.CompassSecondaryButton

/**
 * Tiny landing screen so navigation has a destination after login.
 * Not part of the user's three required features; included only to make the
 * Boot → Login → Switch-Institution flow runnable end-to-end.
 */
@Composable
fun HomeScreen(
    onOpenSwitcher: () -> Unit,
    onLogout: () -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome, ${state.userDisplayName}", style = MaterialTheme.typography.titleLarge)
        Text(
            "Variant · ${state.variantDisplayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Active institution · ${state.activeAccountDisplayName}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Sample formatted amount · ${state.sampleAmount}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CompassPrimaryButton(text = "Switch institution", onClick = onOpenSwitcher)
        CompassSecondaryButton(text = "Log out", onClick = onLogout)
    }
}
