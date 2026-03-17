package io.github.eranl.gotoshelter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eranl.gotoshelter.Alert
import io.github.eranl.gotoshelter.AlertStore
import io.github.eranl.gotoshelter.R
import io.github.eranl.gotoshelter.ui.components.AppTopBar

/**
 * Screen that displays all emergency alerts received
 */
@Composable
fun AlertsScreen(onSettingsClick: () -> Unit = {}) {
  val alerts by AlertStore.alerts.collectAsState()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      AppTopBar(
        title = stringResource(R.string.urgent_alerts),
        actions = {
          if (alerts.isNotEmpty()) {
            IconButton(onClick = { AlertStore.clearAllAlerts() }) {
              Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.clear_all),
                tint = MaterialTheme.colorScheme.onPrimary
              )
            }
          }
          IconButton(onClick = onSettingsClick) {
            Icon(
              Icons.Default.Settings,
              contentDescription = stringResource(R.string.settings_title),
              tint = MaterialTheme.colorScheme.onPrimary
            )
          }
        }
      )
    }
  ) { innerPadding ->
    if (alerts.isEmpty()) {
      EmptyAlertsView(modifier = Modifier.padding(innerPadding))
    } else {
      AlertsList(
        alerts = alerts,
        onDismiss = { AlertStore.removeAlert(it) },
        modifier = Modifier.padding(innerPadding)
      )
    }
  }
}

@Composable
private fun EmptyAlertsView(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = stringResource(R.string.no_alerts),
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 8.dp),
      textAlign = TextAlign.Center
    )
    Text(
      text = stringResource(R.string.no_alerts_desc),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun AlertsList(
  alerts: List<Alert>,
  onDismiss: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(vertical = 8.dp)
  ) {
    items(alerts, key = { it.id }) { alert ->
      AlertCard(alert = alert, onDismiss = onDismiss)
    }
  }
}

@Composable
private fun AlertCard(
  alert: Alert,
  onDismiss: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .background(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp)
      ),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.alert_prefix, alert.type),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Start
          )
          Text(
            text = formatTime(alert.timestamp.toString()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Start
          )
        }
        IconButton(
          onClick = { onDismiss(alert.id) },
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.dismiss),
            tint = MaterialTheme.colorScheme.onErrorContainer
          )
        }
      }

      if (alert.title.isNotEmpty()) {
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 8.dp),
          color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f)
        )

        Text(
          text = stringResource(R.string.title),
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(bottom = 4.dp),
          textAlign = TextAlign.Start
        )

        Text(
          text = alert.title,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
          modifier = Modifier
            .padding(start = 8.dp)
            .background(
              color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.1f),
              shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
          textAlign = TextAlign.Start
        )
      }
    }
  }
}

private fun formatTime(timestamp: String): String {
  return try {
    val parts = timestamp.split("T")
    if (parts.size > 1) {
      val time = parts[1].split(".")[0]
      time
    } else {
      timestamp
    }
  } catch (e: Exception) {
    timestamp
  }
}
