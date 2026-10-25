package br.edu.uea.seldonfleet.domain.usecase

import br.edu.uea.seldonfleet.domain.model.ClassificationResult
import br.edu.uea.seldonfleet.domain.model.Device
import br.edu.uea.seldonfleet.domain.model.DeviceStatus
import br.edu.uea.seldonfleet.domain.model.ReasonCode
import br.edu.uea.seldonfleet.domain.model.ReasonDetail

class ClassifyDevice {

    companion object {
        const val CRITICAL_DRAIN_PCT_PER_HOUR = 12.0
        const val CRITICAL_CRASH_COUNT = 3
        const val CRITICAL_JANK_PCT = 15.0

        const val WARNING_DRAIN_PCT_PER_HOUR = 7.0
        const val WARNING_CRASH_COUNT = 1
        const val WARNING_JANK_PCT = 8.0
    }

    operator fun invoke(device: Device): ClassificationResult {
        val indeterminadoReasons = mutableListOf<ReasonDetail>()

        if (device.batteryCapacityMah <= 0) {
            indeterminadoReasons.add(
                ReasonDetail(ReasonCode.MISSING_BATTERY_CAPACITY, device.batteryCapacityMah.toDouble(), 0.0)
            )
        }
        if (device.screenOnMinutes <= 0) {
            indeterminadoReasons.add(
                ReasonDetail(ReasonCode.ZERO_SCREEN_TIME, device.screenOnMinutes.toDouble(), 0.0)
            )
        }
        if (device.originalEnergyConsumed < 0.0) {
            indeterminadoReasons.add(
                ReasonDetail(ReasonCode.NEGATIVE_ENERGY, device.originalEnergyConsumed, 0.0)
            )
        } else if (device.energyConsumedMah == null || device.energyConsumedMah.isNaN() || device.energyConsumedMah.isInfinite()) {
            indeterminadoReasons.add(
                ReasonDetail(ReasonCode.INVALID_ENERGY_CONVERSION, device.originalEnergyConsumed, 0.0)
            )
        }
        if (device.jankFramesPct.isNaN() || device.jankFramesPct.isInfinite() || device.jankFramesPct < 0.0 || device.jankFramesPct > 100.0) {
            indeterminadoReasons.add(
                ReasonDetail(ReasonCode.INVALID_JANK_METRIC, device.jankFramesPct, 100.0)
            )
        }
        if (device.crashCount7d < 0) {
            indeterminadoReasons.add(
                ReasonDetail(ReasonCode.INVALID_CRASH_METRIC, device.crashCount7d.toDouble(), 0.0)
            )
        }

        if (indeterminadoReasons.isNotEmpty()) {
            return ClassificationResult(
                status = DeviceStatus.INDETERMINADO,
                absoluteDrainMahPerHour = null,
                relativeDrainPctPerHour = null,
                reasons = indeterminadoReasons
            )
        }

        val energyMah = device.energyConsumedMah!!
        val screenHours = device.screenOnMinutes / 60.0
        val absoluteDrain = energyMah / screenHours
        val relativeDrain = (absoluteDrain / device.batteryCapacityMah) * 100.0

        if (relativeDrain.isNaN() || relativeDrain.isInfinite()) {
            return ClassificationResult(
                status = DeviceStatus.INDETERMINADO,
                absoluteDrainMahPerHour = null,
                relativeDrainPctPerHour = null,
                reasons = listOf(ReasonDetail(ReasonCode.INVALID_ENERGY_CONVERSION, 0.0, 0.0))
            )
        }

        val criticalReasons = mutableListOf<ReasonDetail>()
        if (relativeDrain >= CRITICAL_DRAIN_PCT_PER_HOUR) {
            criticalReasons.add(
                ReasonDetail(ReasonCode.HIGH_RELATIVE_DRAIN, relativeDrain, CRITICAL_DRAIN_PCT_PER_HOUR)
            )
        }
        if (device.crashCount7d >= CRITICAL_CRASH_COUNT) {
            criticalReasons.add(
                ReasonDetail(ReasonCode.HIGH_CRASH_COUNT, device.crashCount7d.toDouble(), CRITICAL_CRASH_COUNT.toDouble())
            )
        }
        if (device.jankFramesPct >= CRITICAL_JANK_PCT) {
            criticalReasons.add(
                ReasonDetail(ReasonCode.HIGH_JANK_FRAMES, device.jankFramesPct, CRITICAL_JANK_PCT)
            )
        }

        if (criticalReasons.isNotEmpty()) {
            return ClassificationResult(
                status = DeviceStatus.CRITICO,
                absoluteDrainMahPerHour = absoluteDrain,
                relativeDrainPctPerHour = relativeDrain,
                reasons = criticalReasons
            )
        }

        val warningReasons = mutableListOf<ReasonDetail>()
        if (relativeDrain >= WARNING_DRAIN_PCT_PER_HOUR) {
            warningReasons.add(
                ReasonDetail(ReasonCode.HIGH_RELATIVE_DRAIN, relativeDrain, WARNING_DRAIN_PCT_PER_HOUR)
            )
        }
        if (device.crashCount7d >= WARNING_CRASH_COUNT) {
            warningReasons.add(
                ReasonDetail(ReasonCode.HIGH_CRASH_COUNT, device.crashCount7d.toDouble(), WARNING_CRASH_COUNT.toDouble())
            )
        }
        if (device.jankFramesPct >= WARNING_JANK_PCT) {
            warningReasons.add(
                ReasonDetail(ReasonCode.HIGH_JANK_FRAMES, device.jankFramesPct, WARNING_JANK_PCT)
            )
        }

        if (warningReasons.isNotEmpty()) {
            return ClassificationResult(
                status = DeviceStatus.ATENCAO,
                absoluteDrainMahPerHour = absoluteDrain,
                relativeDrainPctPerHour = relativeDrain,
                reasons = warningReasons
            )
        }

        return ClassificationResult(
            status = DeviceStatus.OK,
            absoluteDrainMahPerHour = absoluteDrain,
            relativeDrainPctPerHour = relativeDrain,
            reasons = listOf(ReasonDetail(ReasonCode.HEALTHY, relativeDrain, WARNING_DRAIN_PCT_PER_HOUR))
        )
    }
}
