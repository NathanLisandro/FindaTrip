package com.smarttravel.analyzer.domain.model.lodging;

import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.Set;

public record LodgingOffer(String id, String name, Money nightlyRate, int nights, Money serviceFees, Money cityTaxes, Money resortFees, HotelRating rating, Set<Amenity> amenities, double distanceToAttractionsKm) {
    public LodgingOffer { amenities = Set.copyOf(amenities); }
    public NormalizedPrice normalize() { return new NormalizedPrice(nightlyRate.multiply(nights), nightlyRate.multiply(nights).add(serviceFees).add(cityTaxes).add(resortFees), java.util.List.of("service_fees","city_taxes","resort_fees")); }
    public boolean has(Amenity amenity) { return amenities.contains(amenity); }
}
