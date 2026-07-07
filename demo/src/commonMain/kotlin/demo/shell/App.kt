package demo.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import demo.registry.DemoCategory
import demo.registry.allCategories
import demo.shim.DemoAnchoredMenu
import demo.shim.DemoIcon
import demo.shim.blend

// ==================
// MARK: App shell — sidebar + content
// ==================

/* Platform-neutral showcase shell. The sidebar dropdown switches between the
   categories returned by allCategories() (common Core + Material 3, plus whatever
   getPlatformCategories() contributes). Nothing here touches project-only APIs —
   the same code runs on native today and on a future jvm target. */
@Composable
fun App() {
    val categories = remember { allCategories() }
    var category by remember { mutableStateOf(categories.first()) }
    var current by remember { mutableStateOf(category.screens.first()) }
    val sidebarBg = MaterialTheme.colorScheme.surface.blend(MaterialTheme.colorScheme.onSurface, 0.02f)

    val sidebarScroll = rememberScrollState()
    val contentScroll = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Sidebar (vertically scrollable — try resizing the window short).
        Column(
            modifier = Modifier
                .width(190.dp)
                .fillMaxHeight()
                .background(sidebarBg)
                .verticalScroll(sidebarScroll)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "ComposeNativeSDL3",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategorySelector(
                categories = categories,
                category = category,
                onSelect = { picked ->
                    if (picked.id != category.id) {
                        category = picked
                        current = picked.screens.first()
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (screen in category.screens) {
                NavItem(
                    label = screen.name,
                    selected = screen.name == current.name,
                    onClick = { current = screen },
                )
            }
        }

        // Content (vertically scrollable — long screens fit a short window).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(contentScroll)
                .padding(24.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            current.content()
        }
    }
}

/* The category dropdown at the top of the sidebar. A styled trigger row (current
   category + chevron) opens an anchored menu listing every category. */
@Composable
private fun CategorySelector(
    categories: List<DemoCategory>,
    category: DemoCategory,
    onSelect: (DemoCategory) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val triggerBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        DemoAnchoredMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = { anchorModifier ->
                Row(
                    modifier = anchorModifier
                        .fillMaxWidth()
                        .background(triggerBg, RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "CATEGORY",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 9.sp,
                        )
                        Text(
                            text = category.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                        )
                    }
                    DemoIcon(
                        if (expanded) DemoIcon.ExpandLess else DemoIcon.ExpandMore,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        size = 20.dp,
                    )
                }
            },
            menu = {
                for (candidate in categories) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable { onSelect(candidate); expanded = false }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = candidate.label,
                            color = if (candidate.id == category.id) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val hoveredSrc = remember { MutableInteractionSource() }
    val hovered by hoveredSrc.collectIsHoveredAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        hovered  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else     -> Color.Transparent
    }
    val foreground = if (selected) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(background, RoundedCornerShape(8.dp))
            .hoverable(hoveredSrc)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = foreground, fontSize = 14.sp)
    }
}
