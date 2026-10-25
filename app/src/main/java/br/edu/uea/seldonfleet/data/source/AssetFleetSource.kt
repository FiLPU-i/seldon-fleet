package br.edu.uea.seldonfleet.data.source

import android.content.Context
import br.edu.uea.seldonfleet.data.dto.DeviceDto
import br.edu.uea.seldonfleet.data.dto.FleetResponseDto
import com.google.gson.Gson

class AssetFleetSource(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    fun loadDevices(fileName: String = "frota.json"): List<DeviceDto> {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val response = gson.fromJson(jsonString, FleetResponseDto::class.java)
        return response?.devices ?: emptyList()
    }
}
