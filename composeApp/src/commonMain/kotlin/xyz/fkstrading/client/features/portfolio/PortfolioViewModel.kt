package xyz.fkstrading.client.features.portfolio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import xyz.fkstrading.shared.data.repository.PositionRepository
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.PositionStatus
import xyz.fkstrading.shared.domain.models.currentValue
import xyz.fkstrading.shared.domain.models.entryValue
import xyz.fkstrading.shared.domain.models.isClosed
import xyz.fkstrading.shared.domain.models.isOpen
import xyz.fkstrading.shared.domain.models.totalPnL

/**
 * ViewModel for Portfolio/Positions Screen
 *
 * Manages portfolio state including:
 * - Open and closed positions
 * - Position filtering and search
 * - Position operations (close, update)
 * - Real-time P&L calculations
 * - Portfolio metrics
 */
class PortfolioViewModel(
    private val positionRepository: PositionRepository,
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // UI State
    private val _uiState = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    // Filter state
    private val _filterState = MutableStateFlow(PositionFilter())
    val filterState: StateFlow<PositionFilter> = _filterState.asStateFlow()

    // Portfolio metrics
    private val _portfolioMetrics = MutableStateFlow(PortfolioMetrics())
    val portfolioMetrics: StateFlow<PortfolioMetrics> = _portfolioMetrics.asStateFlow()

    init {
        observePositions()
    }

    /**
     * Observes positions from repository and updates UI state
     */
    private fun observePositions() {
        viewModelScope.launch {
            combine(
                positionRepository.observeAllPositions(),
                _filterState,
            ) { positions, filter ->
                applyFilter(positions, filter)
            }.catch { error ->
                _uiState.value =
                    PortfolioUiState.Error(
                        error.message ?: "Failed to load positions",
                    )
            }.collect { filteredPositions ->
                updateMetrics(filteredPositions)
                _uiState.value =
                    if (filteredPositions.isEmpty()) {
                        PortfolioUiState.Empty
                    } else {
                        PortfolioUiState.Success(filteredPositions)
                    }
            }
        }
    }

    /**
     * Applies filter to positions list
     */
    private fun applyFilter(
        positions: List<Position>,
        filter: PositionFilter,
    ): List<Position> {
        var filtered = positions

        // Filter by status
        if (filter.showOpenOnly) {
            filtered = filtered.filter { it.status == PositionStatus.OPEN }
        }
        if (filter.showClosedOnly) {
            filtered = filtered.filter { it.status == PositionStatus.CLOSED }
        }

        // Filter by symbol
        if (filter.symbol != null) {
            filtered =
                filtered.filter {
                    it.symbol.contains(filter.symbol, ignoreCase = true)
                }
        }

        // Filter by search query
        if (filter.searchQuery.isNotBlank()) {
            filtered =
                filtered.filter { position ->
                    position.symbol.contains(filter.searchQuery, ignoreCase = true) ||
                        position.positionId.contains(filter.searchQuery, ignoreCase = true)
                }
        }

        // Sort
        filtered =
            when (filter.sortBy) {
                SortOption.DATE_DESC -> filtered.sortedByDescending { it.openedAt }
                SortOption.DATE_ASC -> filtered.sortedBy { it.openedAt }
                SortOption.PNL_DESC -> filtered.sortedByDescending { it.totalPnL() }
                SortOption.PNL_ASC -> filtered.sortedBy { it.totalPnL() }
                SortOption.SYMBOL_ASC -> filtered.sortedBy { it.symbol }
                SortOption.SYMBOL_DESC -> filtered.sortedByDescending { it.symbol }
            }

        return filtered
    }

    /**
     * Updates portfolio metrics based on current positions
     */
    private fun updateMetrics(positions: List<Position>) {
        val openPositions = positions.filter { it.isOpen() }
        val closedPositions = positions.filter { it.isClosed() }

        val totalUnrealizedPnL = openPositions.sumOf { it.unrealizedPnL }
        val totalRealizedPnL = closedPositions.sumOf { it.realizedPnL }
        val totalPnL = totalUnrealizedPnL + totalRealizedPnL

        val totalValue = openPositions.sumOf { it.currentValue() }
        val totalInvested = openPositions.sumOf { it.entryValue() }

        val winningPositions = closedPositions.count { it.realizedPnL > 0 }
        val losingPositions = closedPositions.count { it.realizedPnL < 0 }
        val winRate =
            if (closedPositions.isNotEmpty()) {
                (winningPositions.toDouble() / closedPositions.size) * 100
            } else {
                0.0
            }

        _portfolioMetrics.value =
            PortfolioMetrics(
                totalPositions = positions.size,
                openPositions = openPositions.size,
                closedPositions = closedPositions.size,
                totalUnrealizedPnL = totalUnrealizedPnL,
                totalRealizedPnL = totalRealizedPnL,
                totalPnL = totalPnL,
                totalValue = totalValue,
                totalInvested = totalInvested,
                winRate = winRate,
                winningTrades = winningPositions,
                losingTrades = losingPositions,
            )
    }

    /**
     * Refreshes positions from remote server
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState.Loading
            try {
                positionRepository.refresh()
            } catch (e: Exception) {
                _uiState.value =
                    PortfolioUiState.Error(
                        e.message ?: "Failed to refresh positions",
                    )
            }
        }
    }

    /**
     * Closes a position
     */
    fun closePosition(positionId: String) {
        viewModelScope.launch {
            try {
                val position = positionRepository.getPositionById(positionId)
                if (position != null) {
                    val realizedPnL = position.unrealizedPnL
                    positionRepository.closePosition(positionId, realizedPnL)
                }
            } catch (e: Exception) {
                // Handle error - could emit error state or show snackbar
                println("Error closing position: ${e.message}")
            }
        }
    }

    /**
     * Deletes a position
     */
    fun deletePosition(positionId: String) {
        viewModelScope.launch {
            try {
                positionRepository.deletePosition(positionId)
            } catch (e: Exception) {
                println("Error deleting position: ${e.message}")
            }
        }
    }

    /**
     * Updates the filter state
     */
    fun updateFilter(filter: PositionFilter) {
        _filterState.value = filter
    }

    /**
     * Sets search query
     */
    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    /**
     * Toggles open positions filter
     */
    fun toggleShowOpenOnly() {
        _filterState.value =
            _filterState.value.copy(
                showOpenOnly = !_filterState.value.showOpenOnly,
                showClosedOnly = false,
            )
    }

    /**
     * Toggles closed positions filter
     */
    fun toggleShowClosedOnly() {
        _filterState.value =
            _filterState.value.copy(
                showClosedOnly = !_filterState.value.showClosedOnly,
                showOpenOnly = false,
            )
    }

    /**
     * Shows all positions (clears status filters)
     */
    fun showAllPositions() {
        _filterState.value =
            _filterState.value.copy(
                showOpenOnly = false,
                showClosedOnly = false,
            )
    }

    /**
     * Updates sort option
     */
    fun updateSortOption(sortOption: SortOption) {
        _filterState.value = _filterState.value.copy(sortBy = sortOption)
    }

    /**
     * Clears all filters
     */
    fun clearFilters() {
        _filterState.value = PositionFilter()
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        // Cancel all coroutines when ViewModel is cleared
        viewModelScope.launch {
            // Any cleanup needed
        }
    }
}

/**
 * UI state for portfolio screen
 */
sealed class PortfolioUiState {
    object Loading : PortfolioUiState()

    object Empty : PortfolioUiState()

    data class Success(val positions: List<Position>) : PortfolioUiState()

    data class Error(val message: String) : PortfolioUiState()
}

/**
 * Filter configuration for positions
 */
data class PositionFilter(
    val showOpenOnly: Boolean = false,
    val showClosedOnly: Boolean = false,
    val symbol: String? = null,
    val searchQuery: String = "",
    val sortBy: SortOption = SortOption.DATE_DESC,
)

/**
 * Sort options for positions
 */
enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    PNL_DESC,
    PNL_ASC,
    SYMBOL_ASC,
    SYMBOL_DESC,
}

/**
 * Portfolio metrics
 */
data class PortfolioMetrics(
    val totalPositions: Int = 0,
    val openPositions: Int = 0,
    val closedPositions: Int = 0,
    val totalUnrealizedPnL: Double = 0.0,
    val totalRealizedPnL: Double = 0.0,
    val totalPnL: Double = 0.0,
    val totalValue: Double = 0.0,
    val totalInvested: Double = 0.0,
    val winRate: Double = 0.0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
) {
    /**
     * Returns P&L percentage
     */
    fun getPnLPercentage(): Double {
        return if (totalInvested > 0) {
            (totalPnL / totalInvested) * 100
        } else {
            0.0
        }
    }

    /**
     * Checks if portfolio is profitable
     */
    fun isProfitable(): Boolean = totalPnL > 0
}
