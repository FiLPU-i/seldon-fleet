package br.edu.uea.seldonfleet.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import br.edu.uea.seldonfleet.domain.model.EvaluatedDevice
import br.edu.uea.seldonfleet.ui.StatusFormatter
import br.edu.uea.seldonfleet.ui.list.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    item: EvaluatedDevice,
    onBack: () -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val device = item.device
    val result = item.classification

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${device.id} - ${device.model}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleFavorite(device.id) }) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "Favorito",
                            tint = if (item.isFavorite) Color(0xFFFFB300) else Color.White
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnóstico de Saúde",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        StatusBadge(status = result.status)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = StatusFormatter.formatJustification(result),
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Atributos de Hardware e Telemetria",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Divider()

                    AttributeRow("Identificador", device.id)
                    AttributeRow("Modelo", device.model)
                    AttributeRow("SoC (Processador)", "${device.soc} (Tier: ${device.tier.uppercase()})")
                    AttributeRow("Memória RAM", "${device.ramMb} MB")
                    AttributeRow("Nível de API Android", "API ${device.androidApi}")
                    AttributeRow(
                        "Capacidade da Bateria",
                        if (device.batteryCapacityMah > 0) "${device.batteryCapacityMah} mAh" else "0 mAh (Ausente/Inválida)"
                    )
                    AttributeRow("Nível Atual de Bateria", "${device.batteryLevelPct}%")
                    AttributeRow(
                        "Consumo Normalizado",
                        if (device.energyConsumedMah != null && device.originalEnergyConsumed != null) {
                            "${StatusFormatter.fmt(device.energyConsumedMah)} mAh (Original: ${StatusFormatter.fmt(device.originalEnergyConsumed)} ${device.originalEnergyUnit})"
                        } else {
                            "Inválido/Ausente (${device.originalEnergyConsumed ?: "N/A"} ${device.originalEnergyUnit})"
                        }
                    )
                    AttributeRow(
                        "Dreno Absoluto / Relativo",
                        if (result.absoluteDrainMahPerHour != null && result.relativeDrainPctPerHour != null) {
                            "${StatusFormatter.fmt(result.absoluteDrainMahPerHour)} mAh/h (${StatusFormatter.fmt(result.relativeDrainPctPerHour)}%/h)"
                        } else {
                            "Indeterminado (Divisor nulo ou métrica inválida)"
                        }
                    )
                    AttributeRow("Tempo de Tela Ativa", "${device.screenOnMinutes} min")
                    AttributeRow(
                        "Quadros Perdidos (Jank)",
                        device.jankFramesPct?.let { "${StatusFormatter.fmt(it)}%" } ?: "Ausente"
                    )
                    AttributeRow(
                        "Falhas Recentes (7 dias)",
                        device.crashCount7d?.toString() ?: "Ausente"
                    )
                    AttributeRow("Último Contato (UTC)", device.lastSeen)
                }
            }
        }
    }
}

@Composable
private fun AttributeRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.DarkGray, fontSize = 14.sp, modifier = Modifier.weight(0.45f))
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(0.55f))
    }
}
