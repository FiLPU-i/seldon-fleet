package br.edu.uea.seldonfleet.data.dto

data class FleetResponseDto(
    val schemaVersion: String? = null,
    val exportedAt: String? = null,
    val fleetId: String? = null,
    val deviceCount: Int? = null,
    val devices: List<DeviceDto>? = emptyList()
)

data class DeviceDto(
    val id: String? = null,
    val model: String? = null,
    val soc: String? = null,
    val tier: String? = null,
    val ramMb: Int? = null,
    val androidApi: Int? = null,
    val batteryCapacityMah: Int? = null,
    val batteryLevelPct: Int? = null,
    val screenOnMinutes: Int? = null,
    val energyConsumed: Double? = null,
    val energyUnit: String? = null,
    val nominalVoltage: Double? = null,
    val jankFramesPct: Double? = null,
    val crashCount7d: Int? = null,
    val lastSeen: String? = null
)
