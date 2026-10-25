package br.edu.uea.seldonfleet.domain.repository

import br.edu.uea.seldonfleet.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    suspend fun getDevices(): List<Device>
    fun getFavoriteIds(): Flow<Set<String>>
    suspend fun toggleFavorite(deviceId: String)
}
