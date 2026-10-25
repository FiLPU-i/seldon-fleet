package br.edu.uea.seldonfleet.domain.model

data class Device(
    val id: String,
    val model: String,
    val soc: String,
    val tier: String,
    val ramMb: Int,
    val androidApi: Int,
    val batteryCapacityMah: Int,
    val batteryLevelPct: Int,
    val screenOnMinutes: Int,
    val energyConsumedMah: Double?,
    val originalEnergyConsumed: Double,
    val originalEnergyUnit: String,
    val nominalVoltage: Double?,
    val jankFramesPct: Double,
    val crashCount7d: Int,
    val lastSeen: String
)

enum class ReasonCode {
    MISSING_BATTERY_CAPACITY,
    ZERO_SCREEN_TIME,
    INVALID_ENERGY_CONVERSION,
    NEGATIVE_ENERGY,
    HIGH_RELATIVE_DRAIN,
    HIGH_CRASH_COUNT,
    HIGH_JANK_FRAMES,
    HEALTHY
}

data class ReasonDetail(
    val code: ReasonCode,
    val observedValue: Double,
    val thresholdValue: Double
)

data class ClassificationResult(
    val status: DeviceStatus,
    val absoluteDrainMahPerHour: Double?,
    val relativeDrainPctPerHour: Double?,
    val reasons: List<ReasonDetail>
)

data class EvaluatedDevice(
    val device: Device,
    val classification: ClassificationResult,
    val isFavorite: Boolean = false
)
