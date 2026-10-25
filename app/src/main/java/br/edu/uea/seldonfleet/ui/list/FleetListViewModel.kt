package br.edu.uea.seldonfleet.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.edu.uea.seldonfleet.domain.model.DeviceStatus
import br.edu.uea.seldonfleet.domain.model.EvaluatedDevice
import br.edu.uea.seldonfleet.domain.repository.DeviceRepository
import br.edu.uea.seldonfleet.domain.usecase.ClassifyDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FleetUiState(
    val allDevices: List<EvaluatedDevice> = emptyList(),
    val filteredDevices: List<EvaluatedDevice> = emptyList(),
    val selectedFilter: DeviceStatus? = null,
    val onlyFavorites: Boolean = false,
    val favoriteCount: Int = 0,
    val countsByStatus: Map<DeviceStatus, Int> = emptyMap(),
    val selectedDevice: EvaluatedDevice? = null,
    val isLoading: Boolean = true
)

class FleetListViewModel(
    private val repository: DeviceRepository,
    private val classifyDevice: ClassifyDevice
) : ViewModel() {

    private val _rawDevices = MutableStateFlow<List<EvaluatedDevice>>(emptyList())
    private val _selectedFilter = MutableStateFlow<DeviceStatus?>(null)
    private val _onlyFavorites = MutableStateFlow(false)
    private val _selectedDeviceId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(FleetUiState())
    val uiState: StateFlow<FleetUiState> = _uiState.asStateFlow()

    init {
        loadFleet()
    }

    private fun loadFleet() {
        viewModelScope.launch {
            val devices = repository.getDevices()
            val evaluated = devices.map { device ->
                EvaluatedDevice(
                    device = device,
                    classification = classifyDevice(device)
                )
            }
            _rawDevices.value = evaluated

            combine(
                _rawDevices,
                repository.getFavoriteIds(),
                _selectedFilter,
                _onlyFavorites,
                _selectedDeviceId
            ) { list, favIds, filter, favOnly, detailId ->
                // Favoritos sobem automaticamente para o topo da lista mantendo a ordem original de ID
                val withFavs = list.map { item ->
                    item.copy(isFavorite = favIds.contains(item.device.id))
                }.sortedByDescending { it.isFavorite }

                val counts = DeviceStatus.values().associateWith { status ->
                    withFavs.count { it.classification.status == status }
                }
                val filtered = withFavs.filter { item ->
                    val matchesStatus = (filter == null) || (item.classification.status == filter)
                    val matchesFav = !favOnly || item.isFavorite
                    matchesStatus && matchesFav
                }
                val detail = withFavs.find { it.device.id == detailId }

                FleetUiState(
                    allDevices = withFavs,
                    filteredDevices = filtered,
                    selectedFilter = filter,
                    onlyFavorites = favOnly,
                    favoriteCount = favIds.size,
                    countsByStatus = counts,
                    selectedDevice = detail,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onSelectFilter(status: DeviceStatus?) {
        _onlyFavorites.value = false
        _selectedFilter.value = status
    }

    fun onToggleFavoritesFilter() {
        _onlyFavorites.value = !_onlyFavorites.value
        if (_onlyFavorites.value) {
            _selectedFilter.value = null
        }
    }

    fun onToggleFavorite(deviceId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(deviceId)
        }
    }

    fun onOpenDetail(deviceId: String) {
        _selectedDeviceId.value = deviceId
    }

    fun onCloseDetail() {
        _selectedDeviceId.value = null
    }

    class Factory(
        private val repository: DeviceRepository,
        private val classifyDevice: ClassifyDevice
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FleetListViewModel(repository, classifyDevice) as T
        }
    }
}
