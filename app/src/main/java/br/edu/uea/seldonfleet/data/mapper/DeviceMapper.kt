package br.edu.uea.seldonfleet.data.mapper

import br.edu.uea.seldonfleet.data.dto.DeviceDto
import br.edu.uea.seldonfleet.domain.model.Device

class DeviceMapper {

    fun toDomain(dto: DeviceDto): Device {
        val rawEnergy = dto.energyConsumed ?: -1.0
        val unit = dto.energyUnit?.trim() ?: ""
        val voltage = dto.nominalVoltage

        val normalizedEnergyMah: Double? = when {
            rawEnergy < 0.0 -> null
            unit.equals("mAh", ignoreCase = true) -> rawEnergy
            unit.equals("mWh", ignoreCase = true) -> {
                if (voltage != null && voltage > 0.0 && !voltage.isNaN() && !voltage.isInfinite()) {
                    rawEnergy / voltage
                } else {
                    null
                }
            }
            else -> null
        }

        return Device(
            id = dto.id?.trim().takeUnless { it.isNullOrEmpty() } ?: "UNKNOWN-ID",
            model = dto.model?.trim().takeUnless { it.isNullOrEmpty() } ?: "Modelo Desconhecido",
            soc = dto.soc?.trim().takeUnless { it.isNullOrEmpty() } ?: "N/A",
            tier = dto.tier?.trim()?.lowercase() ?: "unknown",
            ramMb = dto.ramMb ?: 0,
            androidApi = dto.androidApi ?: 0,
            batteryCapacityMah = dto.batteryCapacityMah ?: 0,
            batteryLevelPct = (dto.batteryLevelPct ?: 0).coerceIn(0, 100),
            screenOnMinutes = dto.screenOnMinutes ?: 0,
            energyConsumedMah = normalizedEnergyMah,
            originalEnergyConsumed = dto.energyConsumed ?: 0.0,
            originalEnergyUnit = unit.ifEmpty { "N/A" },
            nominalVoltage = voltage,
            jankFramesPct = dto.jankFramesPct ?: 0.0,
            crashCount7d = dto.crashCount7d ?: 0,
            lastSeen = dto.lastSeen ?: "N/A"
        )
    }
}
