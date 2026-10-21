package com.purrmatch.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(68.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        navigationIcon?.invoke()
        Text(title, Modifier.weight(1f), style = Typography.AppBar, color = Colors.Ink)
        actions()
    }
}

enum class Tab(
    @param:StringRes val label: Int,
    val icon: Icon,
    val selectedIcon: Icon,
) {
    Home(R.string.tab_home, Icon.Home, Icon.HomeSelected),
    Favorites(R.string.tab_favorites, Icon.Favorite, Icon.FavoriteSelected),
    Profile(R.string.tab_profile, Icon.Profile, Icon.ProfileSelected),
}

@Composable
fun BottomBar(
    selectedTab: Tab,
    onTabSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(25.dp)
    Row(
        modifier.fillMaxWidth().height(81.dp)
            .dropShadow(shape, Shadow(24.dp, Colors.NavigationShadow, offset = DpOffset(0.dp, 8.dp)))
            .background(Colors.Navigation, shape).border(1.dp, Color.White, shape)
            .padding(start = 9.dp, end = 9.dp, top = 10.dp, bottom = 13.dp).selectableGroup(),
    ) {
        Tab.entries.forEach { tab ->
            val selected = selectedTab == tab
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .selectable(selected, role = Role.Tab) { onTabSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val selectionShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                Box(
                    Modifier.size(58.dp, 34.dp).graphicsLayer { rotationZ = if (selected) -5f else 0f }
                        .then(if (selected) Modifier
                            .dropShadow(selectionShape, Shadow(9.dp, Colors.NavigationShadow, offset = DpOffset(0.dp, 4.dp)))
                            .background(Colors.NavigationSelection, selectionShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (selected) tab.selectedIcon else tab.icon, null, Modifier.size(25.dp)) }
                Text(
                    stringResource(tab.label),
                    style = Typography.Navigation.copy(fontWeight = if (selected) FontWeight.Black else FontWeight.ExtraBold),
                    color = if (selected) Colors.NavigationActive else Colors.NavigationInactive,
                )
            }
        }
    }
}
