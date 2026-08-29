package com.smarttravel.analyzer.domain.model.search;

import java.math.BigDecimal;

public record PackageFilter(BigDecimal maxPrice, Double minRating, boolean directFlightOnly,
                            boolean breakfastIncluded, boolean freeCancellation, String neighborhood) {
    public static PackageFilter none() { return new PackageFilter(null, null, false, false, false, null); }
}
