package br.edu.uea.seldonfleet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import br.edu.uea.seldonfleet.data.mapper.DeviceMapper
import br.edu.uea.seldonfleet.data.repository.FleetRepositoryImpl
import br.edu.uea.seldonfleet.data.source.AssetFleetSource
import br.edu.uea.seldonfleet.domain.usecase.ClassifyDevice
import br.edu.uea.seldonfleet.ui.detail.DeviceDetailScreen
import br.edu.uea.seldonfleet.ui.list.FleetListScreen
import br.edu.uea.seldonfleet.ui.list.FleetListViewModel
import br.edu.uea.seldonfleet.ui.theme.SeldonFleetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Injeção de dependência manual (Seção 6.3)
        val assetSource = AssetFleetSource(applicationContext)
        val mapper = DeviceMapper()
        val repository = FleetRepositoryImpl(applicationContext, assetSource, mapper)
        val classifyDevice = ClassifyDevice()
        val viewModelFactory = FleetListViewModel.Factory(repository, classifyDevice)

        setContent {
            SeldonFleetTheme {
                val viewModel: FleetListViewModel = viewModel(factory = viewModelFactory)
                val uiState by viewModel.uiState.collectAsState()

                val selected = uiState.selectedDevice
                if (selected != null) {
                    DeviceDetailScreen(
                        item = selected,
                        onBack = { viewModel.onCloseDetail() },
                        onToggleFavorite = { viewModel.onToggleFavorite(it) }
                    )
                } else {
                    FleetListScreen(
                        uiState = uiState,
                        onSelectFilter = { viewModel.onSelectFilter(it) },
                        onToggleFavorite = { viewModel.onToggleFavorite(it) },
                        onDeviceClick = { viewModel.onOpenDetail(it) }
                    )
                }
            }
        }
    }
}
