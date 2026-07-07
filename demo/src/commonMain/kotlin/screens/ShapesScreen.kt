package screens
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

@Composable
internal fun ShapesScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Shapes", "RectangleShape, RoundedCornerShape(Dp / percent), CircleShape")

        Section("RectangleShape") {
            Box(modifier = Modifier.size(120.dp, 40.dp).background(MaterialTheme.colorScheme.primary))
        }

        Section("RoundedCornerShape — radius 4 / 12 / 24 dp") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)))
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)))
            }
        }

        Section("CircleShape") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Box(modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }

        Section("Borders on each shape", "Modifier.border respects the same Shape param as background") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, MaterialTheme.colorScheme.primary))
                Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)))
                Box(modifier = Modifier.size(48.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}
