package br.edu.uea.seldonfleet.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.edu.uea.seldonfleet.data.mapper.DeviceMapper
import br.edu.uea.seldonfleet.data.source.AssetFleetSource
import br.edu.uea.seldonfleet.domain.model.Device
import br.edu.uea.seldonfleet.domain.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "seldon_favorites")

class FleetRepositoryImpl(
    private val context: Context,
    private val assetSource: AssetFleetSource,
    private val mapper: DeviceMapper
) : DeviceRepository {

    companion object {
        private val FAVORITES_KEY = stringSetPreferencesKey("favorite_device_ids")
    }

    override suspend fun getDevices(): List<Device> = withContext(Dispatchers.IO) {
        assetSource.loadDevices().map { dto -> mapper.toDomain(dto) }
    }

    override fun getFavoriteIds(): Flow<Set<String>> {
        return context.dataStore.data.map { preferences ->
            preferences[FAVORITES_KEY] ?: emptySet()
        }
    }

    override suspend fun toggleFavorite(deviceId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[FAVORITES_KEY] ?: emptySet()
            preferences[FAVORITES_KEY] = if (current.contains(deviceId)) {
                current - deviceId
            } else {
                current + deviceId
            }
        }
    }

    override suspend fun clearFavorites() {
        context.dataStore.edit { preferences ->
            preferences[FAVORITES_KEY] = emptySet()
        }
    }
}
