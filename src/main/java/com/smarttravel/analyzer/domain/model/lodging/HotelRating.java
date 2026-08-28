package com.smarttravel.analyzer.domain.model.lodging;
public record HotelRating(double average, int reviewCount) { public double totalRatingPoints() { return average * reviewCount; } }
