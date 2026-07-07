package screens
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

// ==================
// MARK: LazyColumn screen
// ==================

@Composable
internal fun LazyColumnScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("LazyColumn", "DSL: items / item / itemsIndexed inside a verticalScroll viewport")

        Section(
            "items(count) — 100 rows",
            "Mouse-wheel over the list area to scroll. Header item pinned at the top of the list (it scrolls with content — sticky headers TBD).",
        ) {
            val state = rememberLazyListState()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(420.dp).height(260.dp),
            ) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Text(
                            "LazyColumn — first item (a header)",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    items(100) { i ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$i",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                "Item $i — generated via LazyColumn.items(100) { i -> … }",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        Section(
            "itemsIndexed(list)",
            "Use itemsIndexed when you need (index, element) — same API as upstream",
        ) {
            val names = remember { listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta") }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(320.dp).height(160.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(names) { idx, name ->
                        Text(
                            "$idx. $name",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
