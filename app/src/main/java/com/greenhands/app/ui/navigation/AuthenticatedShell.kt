package com.greenhands.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.greenhands.app.R

data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

val globalDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings, "nav_settings"),
    TopLevelDestination(Routes.ACCOUNT, R.string.nav_account, Icons.Outlined.AccountCircle, "nav_account")
)

val heatDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    TopLevelDestination(Routes.CROPS, R.string.nav_crops, Icons.Outlined.Spa, "nav_crops"),
    TopLevelDestination(Routes.SIMULATION, R.string.nav_simulation, Icons.Outlined.Science, "nav_simulation"),
    TopLevelDestination(Routes.SOURCES, R.string.nav_sources, Icons.AutoMirrored.Outlined.MenuBook, "nav_sources"),
    TopLevelDestination(Routes.ACCOUNT, R.string.nav_account, Icons.Outlined.AccountCircle, "nav_account")
)

val topLevelDestinations = heatDestinations

@Composable
fun GreenHandsBottomBar(
    currentRoute: String?,
    heatNavigation: Boolean,
    onNavigate: (String) -> Unit
) {
    val destinations = if (heatNavigation) heatDestinations else globalDestinations
    val selected = Routes.topLevelFor(currentRoute, heatNavigation)
    NavigationBar(
        modifier = Modifier.testTag("authenticated_bottom_nav"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        destinations.forEach { dest ->
            val isSelected = dest.route == selected
            val label = stringResource(dest.labelRes)
            val iconColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "navIcon"
            )
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(dest.route) },
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .padding(bottom = 4.dp)
                                .fillMaxWidth(0.38f)
                                .height(2.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                        Icon(
                            dest.icon,
                            contentDescription = label,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(dest.testTag)
            )
        }
    }
}

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
