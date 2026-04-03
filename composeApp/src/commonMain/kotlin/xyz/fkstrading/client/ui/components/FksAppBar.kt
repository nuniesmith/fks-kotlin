package xyz.fkstrading.client.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import xyz.fkstrading.client.features.settings.SettingsScreenVoyager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FksAppBar(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text("FKS Trading") },
        modifier = modifier,
        actions = {
            IconButton(onClick = { navigator.push(SettingsScreenVoyager()) }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                )
            }
        },
    )
}
