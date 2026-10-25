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
        energyConsumedMah: Double? = 250.0, // 250 mAh em 1h num tanque de 5000 mAh = 5.0%/h (OK)
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
        originalEnergyConsumed = energyConsumedMah ?: 0.0,
        originalEnergyUnit = "mAh",
        nominalVoltage = 3.85,
        jankFramesPct = jankFramesPct,
        crashCount7d = crashCount7d,
        lastSeen = "2026-10-20T00:00:00Z"
    )

    @Test
    fun `capacidade nula ou zero retorna INDETERMINADO sem NaN ou Infinity`() {
        val device = baseDevice(batteryCapacityMah = 0, crashCount7d = 5)
        val result = classify(device)

        assertEquals(DeviceStatus.INDETERMINADO, result.status)
        assertNull(result.relativeDrainPctPerHour)
        assertNull(result.absoluteDrainMahPerHour)
    }

    @Test
    fun `tempo de tela igual a zero retorna INDETERMINADO sem divisao por zero`() {
        val device = baseDevice(screenOnMinutes = 0, energyConsumedMah = 0.0)
        val result = classify(device)

        assertEquals(DeviceStatus.INDETERMINADO, result.status)
        assertNull(result.relativeDrainPctPerHour)
    }

    @Test
    fun `fronteira de ATENCAO para dreno relativo - imediatamente abaixo (6,99) eh OK e no limiar (7,00) eh ATENCAO`() {
        // Capacidade 5000 mAh, 60 min (1h): 349.5 mAh = 6.99%/h | 350.0 mAh = 7.00%/h
        val belowWarning = baseDevice(energyConsumedMah = 349.5)
        val atWarning = baseDevice(energyConsumedMah = 350.0)

        val resBelow = classify(belowWarning)
        val resAt = classify(atWarning)

        assertEquals(DeviceStatus.OK, resBelow.status)
        assertEquals(DeviceStatus.ATENCAO, resAt.status)
        assertFalse(resAt.relativeDrainPctPerHour!!.isNaN())
    }

    @Test
    fun `fronteira de CRITICO para dreno relativo - imediatamente abaixo (11,99) eh ATENCAO e no limiar (12,00) eh CRITICO`() {
        // Capacidade 5000 mAh, 60 min (1h): 599.5 mAh = 11.99%/h | 600.0 mAh = 12.00%/h
        val belowCritical = baseDevice(energyConsumedMah = 599.5)
        val atCritical = baseDevice(energyConsumedMah = 600.0)

        assertEquals(DeviceStatus.ATENCAO, classify(belowCritical).status)
        assertEquals(DeviceStatus.CRITICO, classify(atCritical).status)
    }

    @Test
    fun `fronteiras de crashCount7d e jankFramesPct respeitam limiares exatos`() {
        assertEquals(DeviceStatus.OK, classify(baseDevice(crashCount7d = 0, jankFramesPct = 7.99)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(crashCount7d = 1)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(jankFramesPct = 8.0)).status)
        assertEquals(DeviceStatus.ATENCAO, classify(baseDevice(crashCount7d = 2, jankFramesPct = 14.99)).status)
        assertEquals(DeviceStatus.CRITICO, classify(baseDevice(crashCount7d = 3)).status)
        assertEquals(DeviceStatus.CRITICO, classify(baseDevice(jankFramesPct = 15.0)).status)
    }

    @Test
    fun `precedencia entre sinais - sinal CRITICO vence sinal de ATENCAO simultaneo`() {
        // Dreno em faixa de ATENCAO (8%/h = 400 mAh/h), mas crashes em nivel CRITICO (3)
        val device = baseDevice(energyConsumedMah = 400.0, crashCount7d = 3, jankFramesPct = 9.0)
        val result = classify(device)

        assertEquals(DeviceStatus.CRITICO, result.status)
    }
}
