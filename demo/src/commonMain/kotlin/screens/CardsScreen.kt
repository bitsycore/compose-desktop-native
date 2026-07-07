package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Material3 — Card / ElevatedCard / OutlinedCard container variants.
@Composable
internal fun CardsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Cards",
			"material3 Card / ElevatedCard / OutlinedCard — surface containers with tonal + shadow elevation.",
		)

		Section("Card", "Filled surface container with a subtle shadow") {
			Card(modifier = Modifier.fillMaxWidth()) {
				CardBody("Filled Card", "The default container — a filled surface at low tonal elevation.")
			}
		}

		Section("ElevatedCard", "Larger drop shadow — reads as lifted") {
			ElevatedCard(modifier = Modifier.fillMaxWidth()) {
				CardBody("Elevated Card", "Higher shadow elevation to separate it from the background.")
			}
		}

		Section("OutlinedCard", "Bordered, minimal fill") {
			OutlinedCard(modifier = Modifier.fillMaxWidth()) {
				CardBody("Outlined Card", "A 1.dp outline instead of a shadow — good on busy backgrounds.")
			}
		}
	}
}

@Composable
private fun CardBody(title: String, body: String) {
	Column(
		modifier = Modifier.fillMaxWidth().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
		Text(body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
	}
}
