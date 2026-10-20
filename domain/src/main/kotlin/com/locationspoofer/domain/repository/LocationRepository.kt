package com.locationspoofer.domain.repository

import com.locationspoofer.domain.model.Coordinates
import com.locationspoofer.domain.model.SpoofState
import com.locationspoofer.domain.model.RandomizerRegion
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    val state: StateFlow<SpoofState>
    fun start(): Result<Unit>
    fun stop(): Result<Unit>
    fun updateCoordinates(coordinates: Coordinates): Result<Unit>
    fun configureRandomizer(enabled: Boolean, intervalMillis: Long, region: RandomizerRegion): Result<Unit>
    fun reportError(message: String)
    fun clearError()
}
