package com.locationspoofer.domain.usecase

import com.locationspoofer.domain.repository.LocationRepository

class StartSpoofUseCase(private val repository: LocationRepository) {
    operator fun invoke(): Result<Unit> = repository.start()
}
