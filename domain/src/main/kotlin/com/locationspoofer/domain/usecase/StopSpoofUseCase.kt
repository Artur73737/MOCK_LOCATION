package com.locationspoofer.domain.usecase

import com.locationspoofer.domain.repository.LocationRepository

class StopSpoofUseCase(private val repository: LocationRepository) {
    operator fun invoke(): Result<Unit> = repository.stop()
}
