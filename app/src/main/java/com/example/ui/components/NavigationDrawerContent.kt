package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.NavDestination

@Composable
fun NavigationDrawerContent(
    viewModel: MainViewModel,
    onDestinationSelected: (NavDestination) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val currentDestination by viewModel.currentDestination.collectAsState()
    val storageSpace by viewModel.storageSpaceInfo.collectAsState()

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Branding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedPulseIcon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            isPulsing = true
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "LS Files",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enterprise Storage Manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Overview Card in Drawer
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Internal Storage",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(storageSpace.usedRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { storageSpace.usedRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${formatFileSize(storageSpace.usedBytes)} / ${formatFileSize(storageSpace.totalBytes)} used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Primary Navigation Items
            val isHome = currentDestination == NavDestination.HOME
            NavigationDrawerItem(
                label = { Text("Home & Clean") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Home,
                        unselectedIcon = Icons.Outlined.Home,
                        isSelected = isHome,
                        contentDescription = null
                    )
                },
                selected = isHome,
                onClick = {
                    onDestinationSelected(NavDestination.HOME)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_home")
            )

            val isBrowse = currentDestination == NavDestination.BROWSE
            NavigationDrawerItem(
                label = { Text("Browse Storage") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Folder,
                        unselectedIcon = Icons.Outlined.FolderOpen,
                        isSelected = isBrowse,
                        contentDescription = null
                    )
                },
                selected = isBrowse,
                onClick = {
                    onDestinationSelected(NavDestination.BROWSE)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_browse")
            )

            val isSearch = currentDestination == NavDestination.SEARCH
            NavigationDrawerItem(
                label = { Text("Search Files") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Search,
                        unselectedIcon = Icons.Outlined.Search,
                        isSelected = isSearch,
                        contentDescription = null
                    )
                },
                selected = isSearch,
                onClick = {
                    onDestinationSelected(NavDestination.SEARCH)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_search")
            )

            val isRecent = currentDestination == NavDestination.RECENT
            NavigationDrawerItem(
                label = { Text("Recent Files") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Schedule,
                        unselectedIcon = Icons.Outlined.Schedule,
                        isSelected = isRecent,
                        contentDescription = null
                    )
                },
                selected = isRecent,
                onClick = {
                    onDestinationSelected(NavDestination.RECENT)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_recent")
            )

            val isTags = currentDestination == NavDestination.TAGS
            NavigationDrawerItem(
                label = { Text("Starred & Tags") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Label,
                        unselectedIcon = Icons.Outlined.Label,
                        isSelected = isTags,
                        contentDescription = null
                    )
                },
                selected = isTags,
                onClick = {
                    onDestinationSelected(NavDestination.TAGS)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_tags")
            )

            val isSafe = currentDestination == NavDestination.SAFE_FOLDER
            NavigationDrawerItem(
                label = { Text("Safe Folder") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Lock,
                        unselectedIcon = Icons.Outlined.Lock,
                        isSelected = isSafe,
                        contentDescription = null
                    )
                },
                selected = isSafe,
                onClick = {
                    onDestinationSelected(NavDestination.SAFE_FOLDER)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_safe_folder")
            )

            val isBin = currentDestination == NavDestination.BIN
            NavigationDrawerItem(
                label = { Text("Recycle Bin") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Delete,
                        unselectedIcon = Icons.Outlined.Delete,
                        isSelected = isBin,
                        contentDescription = null
                    )
                },
                selected = isBin,
                onClick = {
                    onDestinationSelected(NavDestination.BIN)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_bin")
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            val isSettings = currentDestination == NavDestination.SETTINGS
            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = {
                    AnimatedNavIcon(
                        selectedIcon = Icons.Filled.Settings,
                        unselectedIcon = Icons.Outlined.Settings,
                        isSelected = isSettings,
                        contentDescription = null
                    )
                },
                selected = isSettings,
                onClick = {
                    onDestinationSelected(NavDestination.SETTINGS)
                    onCloseDrawer()
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_nav_settings")
            )
        }
    }
}
