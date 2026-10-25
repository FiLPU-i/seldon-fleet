package br.edu.uea.seldonfleet

import br.edu.uea.seldonfleet.domain.model.Device
import br.edu.uea.seldonfleet.domain.model.DeviceStatus
import br.edu.uea.seldonfleet.domain.usecase.ClassifyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ClassifyDeviceTest {

    private val classify = ClassifyDevice()

    private fun baseDevice(
        batteryCapacityMah: Int = 5000,
        screenOnMinutes: Int = 60,
        energyConsumedMah: Double? = 250.0,
        originalEnergyConsumed: Double = 250.0,
        jankFramesPct: Double = 2.0,
        crashCount7d: Int = 0
    ) = Device(
        id = "DEV-TEST",
        model = "Moto Test",
        soc = "Snapdragon Test",
        tier = "mid",
        ramMb = 8192,
        androidApi = 34,
        batteryCapacityMah = batteryCapacityMah,
        batteryLevelPct = 50,
        screenOnMinutes = screenOnMinutes,
        energyConsumedMah = energyConsumedMah,
        originalEnergyConsumed = originalEnergyConsumed,
        originalEnergyUnit = "mAh",
        nominalVoltage = 3.85,
        jankFramesPct = jankFramesPct,
        crashCount7d = crashCount7d,
        lastSeen = "2026-10-20T00:00:00Z"
    )

    @Test
    fun `capacidade nula, zero ou negativa retorna INDETERMINADO sem NaN ou Infinity`() {
        assertEquals(DeviceStatus.INDETERMINADO, classify(baseDevice(batteryCapacityMah = 0, crashCount7d = 5)).status)
        assertEquals(DeviceStatus.INDETERMINADO, classify(baseDevice(batteryCapacityMah = -500)).status)
    }

    @Test
    fun `tempo de tela igual a zero ou negativo retorna INDETERMINADO sem divisao por zero`() {
        val zeroScreen = classify(baseDevice(screenOnMinutes = 0, energyConsumedMah = 0.0, originalEnergyConsumed = 0.0))
        assertEquals(DeviceStatus.INDETERMINADO, zeroScreen.status)
        assertNull(zeroScreen.relativeDrainPctPerHour)

        val negativeScreen = classify(baseDevice(screenOnMinutes = -15))
        assertEquals(DeviceStatus.INDETERMINADO, negativeScreen.status)
    }

    @Test
    fun `fronteira completa de ATENCAO para dreno relativo - abaixo (6,99), no limiar (7,00) e acima (7,01)`() {
        // Capacidade 5000 mAh, 60 min (1h): 349.5 mAh = 6.99%/h | 350.0 mAh = 7.00%/h | 350.5 mAh = 7.01%/h
        val belowWarning = baseDevice(energyConsumedMah = 349.5, originalEnergyConsumed = 349.5)
        val atWarning = baseDevice(energyConsumedMah = 350.0, originalEnergyConsumed = 350.0)
        val aboveWarning = baseDevice(energyConsumedMah = 350.5, originalEnergyConsumed = 350.5)

        val resBelow = classify(belowWarning)
        val resAt = classify(atWarning)
        val resAbove = classify(aboveWarning)

        assertEquals(DeviceStatus.OK, resBelow.status)
        assertEquals(DeviceStatus.ATENCAO, resAt.status)
        assertEquals(DeviceStatus.ATENCAO, resAbove.status)
        assertFalse(resAt.relativeDrainPctPerHour!!.isNaN())
    }

    @Test
    fun `fronteira completa de CRITICO para dreno relativo - abaixo (11,99), no limiar (12,00) e acima (12,01)`() {
        // Capacidade 5000 mAh, 60 min (1h): 599.5 mAh = 11.99%/h | 600.0 mAh = 12.00%/h | 600.5 mAh = 12.01%/h
        val belowCritical = baseDevice(energyConsumedMah = 599.5, originalEnergyConsumed = 599.5)
        val atCritical = baseDevice(energyConsumedMah = 600.0, originalEnergyConsumed = 600.0)
        val aboveCritical = baseDevice(energyConsumedMah = 600.5, originalEnergyConsumed = 600.5)

        assertEquals(DeviceStatus.ATENCAO, classify(belowCritical).status)
        assertEquals(DeviceStatus.CRITICO, classify(atCritical).status)
        assertEquals(DeviceStatus.CRITICO, classify(aboveCritical).status)
    }

    @Test
    fun `fronteiras de crashCount7d e jankFramesPct abaixo, no limiar e acima`() {
        // Jank: 7.99 (OK), 8.00 (ATENCAO), 8.01 (ATENCAO), 14.99 (ATENCAO), 15.00 (CRITICO), 15.01 (CRITICO)
        assertEquals(DeviceStatus.OK, classify(baseDevice(jankFramesPct = 7.99)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(jankFramesPct = 8.00)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(jankFramesPct = 8.01)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(jankFramesPct = 14.99)).status)
        assertEquals(DeviceStatus.CRITICO, classify(baseDevice(jankFramesPct = 15.00)).status)
        assertEquals(DeviceStatus.CRITICO, classify(baseDevice(jankFramesPct = 15.01)).status)

        // Crashes: 0 (OK), 1 (ATENCAO), 2 (ATENCAO), 3 (CRITICO), 4 (CRITICO)
        assertEquals(DeviceStatus.OK, classify(baseDevice(crashCount7d = 0)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(crashCount7d = 1)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(crashCount7d = 2)).status)
        assertEquals(DeviceStatus.CRITICO, classify(baseDevice(crashCount7d = 3)).status)
        assertEquals(DeviceStatus.CRITICO, classify(baseDevice(crashCount7d = 4)).status)
    }

    @Test
    fun `precedencia entre sinais - sinal CRITICO vence sinal de ATENCAO simultaneo`() {
        val device = baseDevice(energyConsumedMah = 400.0, originalEnergyConsumed = 400.0, crashCount7d = 3, jankFramesPct = 9.0)
        val result = classify(device)

        assertEquals(DeviceStatus.CRITICO, result.status)
    }

    @Test
    fun `dados externos invalidos - jank negativo, acima de 100 ou crashes negativos retornam INDETERMINADO`() {
        assertEquals(DeviceStatus.INDETERMINADO, classify(baseDevice(jankFramesPct = -1.0)).status)
        assertEquals(DeviceStatus.INDETERMINADO, classify(baseDevice(jankFramesPct = 105.0)).status)
        assertEquals(DeviceStatus.INDETERMINADO, classify(baseDevice(jankFramesPct = Double.NaN)).status)
        assertEquals(DeviceStatus.INDETERMINADO, classify(baseDevice(crashCount7d = -1)).status)
        assertEquals(DeviceStatus.INDETERMINADO, classify(baseDevice(energyConsumedMah = -50.0, originalEnergyConsumed = -50.0)).status)
    }
}
