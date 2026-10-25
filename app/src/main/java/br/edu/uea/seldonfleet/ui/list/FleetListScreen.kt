package br.edu.uea.seldonfleet.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.edu.uea.seldonfleet.domain.model.DeviceStatus
import br.edu.uea.seldonfleet.domain.model.EvaluatedDevice
import br.edu.uea.seldonfleet.ui.StatusFormatter
import br.edu.uea.seldonfleet.ui.theme.StatusCriticalBg
import br.edu.uea.seldonfleet.ui.theme.StatusCriticalColor
import br.edu.uea.seldonfleet.ui.theme.StatusOkBg
import br.edu.uea.seldonfleet.ui.theme.StatusOkColor
import br.edu.uea.seldonfleet.ui.theme.StatusUnknownBg
import br.edu.uea.seldonfleet.ui.theme.StatusUnknownColor
import br.edu.uea.seldonfleet.ui.theme.StatusWarningBg
import br.edu.uea.seldonfleet.ui.theme.StatusWarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetListScreen(
    uiState: FleetUiState,
    onSelectFilter: (DeviceStatus?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeviceClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Seldon Fleet", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Monitoramento de Telemetria (${uiState.allDevices.size} aparelhos)",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            FilterBar(
                totalCount = uiState.allDevices.size,
                countsByStatus = uiState.countsByStatus,
                selectedFilter = uiState.selectedFilter,
                onSelectFilter = onSelectFilter
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredDevices, key = { it.device.id }) { item ->
                    DeviceRowCard(
                        item = item,
                        onToggleFavorite = { onToggleFavorite(item.device.id) },
                        onClick = { onDeviceClick(item.device.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    totalCount: Int,
    countsByStatus: Map<DeviceStatus, Int>,
    selectedFilter: DeviceStatus?,
    onSelectFilter: (DeviceStatus?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onSelectFilter(null) },
            label = { Text("Todos ($totalCount)") }
        )
        DeviceStatus.values().forEach { status ->
            val count = countsByStatus[status] ?: 0
            val label = StatusFormatter.labelFor(status)
            FilterChip(
                selected = selectedFilter == status,
                onClick = { onSelectFilter(status) },
                label = { Text("$label ($count)") }
            )
        }
    }
}

@Composable
private fun DeviceRowCard(
    item: EvaluatedDevice,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Favorito",
                    tint = if (item.isFavorite) Color(0xFFFFB300) else Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.device.id} • ${item.device.model}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bateria: ${item.device.batteryLevelPct}% | SoC: ${item.device.soc}",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }

            StatusBadge(status = item.classification.status)
        }
    }
}

@Composable
fun StatusBadge(status: DeviceStatus) {
    val (textColor, bgColor) = when (status) {
        DeviceStatus.OK -> StatusOkColor to StatusOkBg
        DeviceStatus.ATENCAO -> StatusWarningColor to StatusWarningBg
        DeviceStatus.CRITICO -> StatusCriticalColor to StatusCriticalBg
        DeviceStatus.INDETERMINADO -> StatusUnknownColor to StatusUnknownBg
    }

    Box(
        modifier = Modifier
            .background(color = bgColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = StatusFormatter.labelFor(status),
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
