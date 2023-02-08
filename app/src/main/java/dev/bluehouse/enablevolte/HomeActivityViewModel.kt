package dev.bluehouse.enablevolte

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HomeActivityUIState(
    var shizukuEnabled: Boolean = false,
    var shizukuGranted: Boolean = false,
    var deviceIMSEnabled: Boolean = false,
    var carrierIMSEnabled: Boolean = false,
    var isIMSRunning: Boolean = false,
    var subscriptionId: Int = -1,
    var errorString: String = ""
)
class HomeActivityViewModel: ViewModel() {
    private val _uiState = MutableStateFlow(HomeActivityUIState())
    val uiState: StateFlow<HomeActivityUIState> = _uiState
}