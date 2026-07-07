package screens
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

// ==================
// MARK: Scroll screen
// ==================

@Composable
internal fun ScrollScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Scroll",
            "Modifier.verticalScroll(rememberScrollState()), wheel events, auto-clip",
        )

        Section(
            "Fixed-height scrollable Box",
            "Hover over the box and use mouse wheel / trackpad. Content is 40 rows tall.",
        ) {
            val vScroll = rememberScrollState()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(400.dp).height(200.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 1..40) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$i",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Text(
                                    "Row $i — scroll to see all 40 items",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        Section(
            "App-shell scrolling",
            "Shrink the window vertically — the sidebar AND the main content area both gain scrollbars.",
        ) {
            Text(
                "Both panes were wrapped in verticalScroll(rememberScrollState()) inside App().",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )
        }
    }
}
