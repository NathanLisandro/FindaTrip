package com.smarttravel.analyzer.domain.model.shared;

public record Location(String code, String city, String country) {
    public Location { if (code == null || code.isBlank()) throw new IllegalArgumentException("Location code is required"); }
}
