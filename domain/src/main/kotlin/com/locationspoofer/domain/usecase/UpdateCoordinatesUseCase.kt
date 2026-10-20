package com.locationspoofer.domain.usecase

import com.locationspoofer.domain.model.Coordinates
import com.locationspoofer.domain.repository.LocationRepository

class UpdateCoordinatesUseCase(private val repository: LocationRepository) {
    operator fun invoke(coordinates: Coordinates): Result<Unit> {
        if (!coordinates.isValid) return Result.failure(IllegalArgumentException("Coordinates are outside the valid range"))
        return repository.updateCoordinates(coordinates)
    }
}
