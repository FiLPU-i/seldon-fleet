package br.edu.uea.seldonfleet

import br.edu.uea.seldonfleet.data.dto.DeviceDto
import br.edu.uea.seldonfleet.data.mapper.DeviceMapper
import br.edu.uea.seldonfleet.domain.model.DeviceStatus
import br.edu.uea.seldonfleet.domain.usecase.ClassifyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMapperTest {

    private val mapper = DeviceMapper()
    private val classifyDevice = ClassifyDevice()

    @Test
    fun `equivalencia de unidade - mesmo consumo fisico em mWh e mAh produz mesmo dreno e status`() {
        val dtoMah = DeviceDto(
            id = "DEV-MAH",
            model = "Galaxy A54",
            soc = "Exynos 1380",
            tier = "mid",
            ramMb = 8192,
            androidApi = 34,
            batteryCapacityMah = 5000,
            batteryLevelPct = 61,
            screenOnMinutes = 186,
            energyConsumed = 980.0,
            energyUnit = "mAh",
            nominalVoltage = 3.85,
            jankFramesPct = 4.1,
            crashCount7d = 0,
            lastSeen = "2026-10-20T02:50:44Z"
        )

        val dtoMwh = dtoMah.copy(
            id = "DEV-MWH",
            energyConsumed = 3773.0,
            energyUnit = "mWh",
            nominalVoltage = 3.85
        )

        val domainMah = mapper.toDomain(dtoMah)
        val domainMwh = mapper.toDomain(dtoMwh)

        assertNotNull(domainMah.energyConsumedMah)
        assertNotNull(domainMwh.energyConsumedMah)
        assertEquals(domainMah.energyConsumedMah!!, domainMwh.energyConsumedMah!!, 0.001)

        val resultMah = classifyDevice(domainMah)
        val resultMwh = classifyDevice(domainMwh)

        assertEquals(DeviceStatus.OK, resultMah.status)
        assertEquals(resultMah.status, resultMwh.status)
        assertEquals(resultMah.relativeDrainPctPerHour!!, resultMwh.relativeDrainPctPerHour!!, 0.001)
    }

    @Test
    fun `unidade mWh sem nominalVoltage valida retorna energyConsumedMah nulo e status INDETERMINADO`() {
        val dtoInvalido = DeviceDto(
            id = "DEV-INV",
            model = "Teste",
            batteryCapacityMah = 4000,
            screenOnMinutes = 120,
            energyConsumed = 2000.0,
            energyUnit = "mWh",
            nominalVoltage = 0.0
        )

        val domain = mapper.toDomain(dtoInvalido)
        assertNull(domain.energyConsumedMah)

        val result = classifyDevice(domain)
        assertEquals(DeviceStatus.INDETERMINADO, result.status)
    }
}
