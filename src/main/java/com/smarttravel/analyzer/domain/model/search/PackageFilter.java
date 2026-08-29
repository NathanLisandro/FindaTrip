package com.smarttravel.analyzer.domain.model.search;

import java.math.BigDecimal;

public record PackageFilter(BigDecimal maxPrice, Double minRating, boolean directFlightOnly,
                            boolean breakfastIncluded, boolean freeCancellation, String neighborhood,
                            java.util.Set<com.smarttravel.analyzer.domain.model.lodging.StayType> stayTypes) {
    public PackageFilter {
        stayTypes = stayTypes == null ? java.util.Set.of() : java.util.Set.copyOf(stayTypes);
    }

    public static PackageFilter none() {
        return new PackageFilter(null, null, false, false, false, null, java.util.Set.of());
    }
}
